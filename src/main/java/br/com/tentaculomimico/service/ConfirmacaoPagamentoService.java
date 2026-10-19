package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.ConsultaCheckoutPagBank;
import br.com.tentaculomimico.client.PagBankMatriculaClient;
import br.com.tentaculomimico.config.MatriculaPagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutMatricula.*;
import br.com.tentaculomimico.model.*;
import br.com.tentaculomimico.model.PagamentoMatricula.*;
import br.com.tentaculomimico.repository.WebhookProcessadoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class ConfirmacaoPagamentoService {
    private final MatriculaPagBankProperties config;
    private final ConsultaCheckoutPagBank gateway;
    private final RegistroPagamentoService registros;
    private final WebhookProcessadoRepository eventos;
    private final MongoTemplate mongo;
    public ConfirmacaoPagamentoService(MatriculaPagBankProperties config, ConsultaCheckoutPagBank gateway,
            RegistroPagamentoService registros, WebhookProcessadoRepository eventos, MongoTemplate mongo) {
        this.config = config; this.gateway = gateway; this.registros = registros; this.eventos = eventos; this.mongo = mongo;
    }
    public void processarNotificacao(byte[] corpo, String assinatura) {
        validarAssinatura(corpo, assinatura);
        JsonNode evento;
        try { evento = new JsonMapper().readTree(corpo); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Notificação inválida."); }
        String referencia = evento.path("reference_id").asText();
        PagamentoMatricula p = registros.buscar(referencia);
        Tentativa tentativa = registros.tentativa(p, referencia);
        String id = evento.path("id").asText();
        String checkout = id.startsWith("CHEC_") ? id : tentativa.getCheckoutId();
        // O PagBank reentrega: não dar ACK nem marcar processado enquanto a criação não estiver vinculada.
        if (checkout == null) throw new IllegalStateException("Aguardando vínculo do Checkout.");
        PagBankMatriculaClient.validarId(checkout);
        if (tentativa.getCheckoutId() != null && !checkout.equals(tentativa.getCheckoutId()))
            throw new IllegalArgumentException("Checkout divergente.");
        Consulta consulta = gateway.consultar(checkout);
        validarConsulta(p, referencia, checkout, consulta);
        List<Pagamento> notificadas = new ArrayList<>();
        PagBankMatriculaClient.lerCobrancas(evento.path("charges"), notificadas);
        for (Pagamento pagamento : notificadas) {
            validarPagamento(p, pagamento);
            // Um evento antigo pode divergir no status, mas nunca no ID/valor/moeda.
            if (consulta.pagamentos().stream().noneMatch(c -> c.id().equals(pagamento.id())
                    && c.centavos() == pagamento.centavos() && c.moeda().equals(pagamento.moeda())))
                throw new IllegalStateException("Pagamento ainda não disponível na consulta do Checkout.");
        }
        if (notificadas.isEmpty() && !Set.of("EXPIRED", "ACTIVE", "INACTIVE").contains(evento.path("status").asText()))
            throw new IllegalArgumentException("Estado de notificação desconhecido.");
        // Chave sem dados pessoais, independente de espaços/ordem das propriedades no JSON.
        String estados = consulta.pagamentos().stream().map(c -> c.id() + ":" + c.status() + ":" + c.pagoEm())
                .sorted().reduce("", (a,b) -> a + "|" + b);
        String chave = hash((evento.path("event_id").asText() + "|" + referencia + "|" + checkout
                + "|" + consulta.status() + estados).getBytes(StandardCharsets.UTF_8));
        if (eventos.existsById(chave)) return;
        conciliar(referencia, consulta);
        // Só após todas as escritas. Reentrega recupera aprovação persistida antes da ativação.
        try { eventos.insert(new WebhookProcessado(chave, chave, Instant.now())); }
        catch (DuplicateKeyException e) { /* outra entrega terminou o mesmo trabalho */ }
    }
    public void validarAssinatura(byte[] corpo, String assinatura) {
        if (corpo == null || corpo.length > 262144 || assinatura == null || !assinatura.matches("[a-fA-F0-9]{64}"))
            throw new AssinaturaInvalida();
        byte[] prefixo = (config.token() + "-").getBytes(StandardCharsets.UTF_8);
        byte[] mensagem = new byte[prefixo.length + corpo.length];
        System.arraycopy(prefixo, 0, mensagem, 0, prefixo.length);
        System.arraycopy(corpo, 0, mensagem, prefixo.length, corpo.length);
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(hash(mensagem)), HexFormat.of().parseHex(assinatura)))
            throw new AssinaturaInvalida();
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(); }
    }
    private void validarConsulta(PagamentoMatricula p, String referencia, String checkout, Consulta c) {
        if (!checkout.equals(c.checkoutId()) || !referencia.equals(c.referencia())
                || !Set.of("ACTIVE", "INACTIVE", "EXPIRED").contains(c.status()))
            throw new IllegalArgumentException("Checkout divergente ou estado desconhecido.");
        for (Pagamento pagamento : c.pagamentos()) validarPagamento(p, pagamento);
    }
    private void validarPagamento(PagamentoMatricula p, Pagamento c) {
        if (c.id() == null || !c.id().matches("CHAR_[A-Za-z0-9-]{4,100}") || !"BRL".equals(c.moeda())
                || c.centavos() != p.getValor().movePointRight(2).longValueExact()
                || !Set.of("PAID", "WAITING", "IN_ANALYSIS", "DECLINED", "CANCELED").contains(c.status())
                || !Set.of("PIX", "CREDIT_CARD").contains(c.metodo())
                || ("PAID".equals(c.status()) && c.pagoEm() == null))
            throw new IllegalArgumentException("Pagamento divergente ou estado desconhecido.");
    }
    /** Consulta autenticada ao Sandbox também permite recuperar webhook perdido, apenas em POST. */
    public void reconciliarCheckout(String referencia) {
        var p = registros.buscar(referencia);
        String id = registros.tentativa(p, referencia).getCheckoutId();
        if (id == null) throw new IllegalStateException("Aguardando conciliação da tentativa anterior.");
        Consulta c = gateway.consultar(id);
        validarConsulta(p, referencia, id, c);
        conciliar(referencia, c);
    }
    private void conciliar(String referencia, Consulta consulta) {
        PagamentoMatricula atualizado = registros.alterar(referencia, p -> {
            Tentativa t = registros.tentativa(p, referencia);
            RegistroPagamentoService.vincular(t, consulta.checkoutId());
            t.setExpiracao(consulta.expiracao());
            Map<String, Transacao> historico = new LinkedHashMap<>();
            t.getTransacoes().forEach(c -> historico.put(c.id(), c));
            for (Pagamento c : consulta.pagamentos()) {
                Transacao anterior = historico.get(c.id());
                if (anterior == null || !"PAID".equals(anterior.status()))
                    historico.put(c.id(), new Transacao(c.id(), c.status(), c.centavos(), c.moeda(), c.metodo(), c.pagoEm()));
            }
            t.setTransacoes(new ArrayList<>(historico.values()));
            var pagos = consulta.pagamentos().stream().filter(c -> "PAID".equals(c.status())).toList();
            if (!pagos.isEmpty()) {
                Pagamento pago = pagos.getFirst();
                if (t.getPagamentoId() != null && t.getStatus() == EstadoTentativa.PAID && !t.getPagamentoId().equals(pago.id()))
                    p.setConciliacaoNecessaria(true);
                t.setStatus(EstadoTentativa.PAID);
                t.setPagamentoId(pago.id()); t.setPagoEm(pago.pagoEm()); t.setMetodo(pago.metodo());
                p.setStatus(Estado.APROVADO);
                if (pagos.size() > 1) p.setConciliacaoNecessaria(true);
            } else if (t.getStatus() != EstadoTentativa.PAID) {
                boolean pendente = consulta.pagamentos().stream().anyMatch(c -> Set.of("WAITING", "IN_ANALYSIS").contains(c.status()));
                if ("EXPIRED".equals(consulta.status()) && !pendente && consulta.expiracao() != null
                        && !consulta.expiracao().isAfter(Instant.now())) t.setStatus(EstadoTentativa.EXPIRED);
                else if (t.getStatus() != EstadoTentativa.EXPIRED) {
                    String estado = consulta.pagamentos().isEmpty() ? "WAITING" : consulta.pagamentos().getLast().status();
                    t.setStatus(EstadoTentativa.valueOf(estado));
                }
            }
        });
        if (atualizado.getStatus() == Estado.APROVADO) {
            mongo.updateFirst(Query.query(Criteria.where("id").is(atualizado.getMatriculaId())
                            .and("status").is("aceita_aguardando_pagamento")),
                    new Update().set("status", "ativa").set("acesso_liberado", true), Matricula.class);
            Matricula m = mongo.findById(atualizado.getMatriculaId(), Matricula.class);
            if (m == null || m.getStatusMatricula() != StatusMatricula.ATIVA) {
                registros.alterar(referencia, p -> p.setConciliacaoNecessaria(true));
            }
        }
    }
    public static class AssinaturaInvalida extends RuntimeException {}
}
