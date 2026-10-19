package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.PagamentoMatricula;
import br.com.tentaculomimico.model.PagamentoMatricula.*;
import br.com.tentaculomimico.repository.PagamentoMatriculaRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class RegistroPagamentoService {
    private final PagamentoMatriculaRepository repository;
    public RegistroPagamentoService(PagamentoMatriculaRepository repository) { this.repository = repository; }
    public record Reserva(PagamentoMatricula pagamento, Tentativa tentativa, boolean criar) {}

    public Reserva reservarInicio(String matriculaId, int valorCentavos) {
        if (valorCentavos <= 0) throw new IllegalArgumentException("Valor inválido.");
        for (int i = 0; i < 8; i++) {
            var existente = repository.findByMatriculaId(matriculaId);
            if (existente.isEmpty()) {
                PagamentoMatricula p = new PagamentoMatricula();
                p.setMatriculaId(matriculaId);
                p.setValor(BigDecimal.valueOf(valorCentavos, 2));
                p.setCompetencia(java.time.LocalDate.now(java.time.ZoneOffset.UTC).withDayOfMonth(1)
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
                p.setVencimento(Instant.now().plusSeconds(7200));
                Tentativa t = novaTentativa();
                p.getTentativas().add(t);
                try { return new Reserva(repository.insert(p), t, true); }
                catch (DuplicateKeyException e) { continue; }
            }
            PagamentoMatricula p = existente.get();
            if (p.getStatus() == Estado.APROVADO || p.getValor().compareTo(BigDecimal.valueOf(valorCentavos, 2)) != 0)
                throw new IllegalStateException("Pagamento já aprovado ou valor alterado. Contate o suporte.");
            Tentativa t = p.getTentativas().getLast();
            // Só EXPIRED conciliado autoriza outra cobrança; recusa isolada não encerra o Checkout.
            if (t.getStatus() != EstadoTentativa.EXPIRED) return new Reserva(p, t, false);
            Tentativa nova = novaTentativa();
            p.getTentativas().add(nova);
            try { return new Reserva(repository.save(p), nova, true); }
            catch (OptimisticLockingFailureException e) { /* reler o vencedor */ }
        }
        throw new IllegalStateException("Pagamento em atualização. Tente novamente em instantes.");
    }
    private Tentativa novaTentativa() {
        Tentativa t = new Tentativa();
        t.setReferencia(UUID.randomUUID().toString());
        return t;
    }
    public PagamentoMatricula buscar(String referencia) {
        return repository.porReferencia(referencia).orElseThrow(() -> new IllegalArgumentException("Referência desconhecida."));
    }
    public Tentativa tentativa(PagamentoMatricula p, String referencia) {
        return p.getTentativas().stream().filter(t -> referencia.equals(t.getReferencia())).findFirst().orElseThrow();
    }
    public PagamentoMatricula alterar(String referencia, Consumer<PagamentoMatricula> alteracao) {
        for (int i = 0; i < 8; i++) {
            PagamentoMatricula p = buscar(referencia);
            alteracao.accept(p);
            try { return repository.save(p); }
            catch (OptimisticLockingFailureException e) { /* reaplica sobre estado atual */ }
        }
        throw new IllegalStateException("Conciliação em atualização.");
    }
    public void registrarCheckout(String referencia, String checkoutId, String payUrl, Instant expiracao) {
        alterar(referencia, p -> {
            Tentativa t = tentativa(p, referencia);
            vincular(t, checkoutId);
            t.setPayUrl(payUrl);
            t.setExpiracao(expiracao);
            if (t.getStatus() == EstadoTentativa.CRIANDO || t.getStatus() == EstadoTentativa.INCERTA)
                t.setStatus(EstadoTentativa.WAITING);
        });
    }
    public static void vincular(Tentativa t, String id) {
        if (t.getCheckoutId() != null && !t.getCheckoutId().equals(id)) throw new IllegalArgumentException("Checkout divergente.");
        t.setCheckoutId(id);
    }
    public void falharInicio(String referencia) {
        alterar(referencia, p -> {
            Tentativa t = tentativa(p, referencia);
            t.setFalhouEm(Instant.now());
            if (t.getStatus() == EstadoTentativa.CRIANDO) t.setStatus(EstadoTentativa.INCERTA);
        });
    }
}
