package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.CheckoutMatriculaGateway;
import br.com.tentaculomimico.client.PagBankMatriculaClient;
import br.com.tentaculomimico.config.MatriculaPagBankProperties;
import br.com.tentaculomimico.controller.WebhookMatriculaController;
import br.com.tentaculomimico.model.*;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.CursoRepository;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Instant;

@Service
public class PagamentoMatriculaService {
    private final MatriculaService matriculas;
    private final SessaoService sessao;
    private final CursoRepository cursos;
    private final RegistroPagamentoService registros;
    private final ConfirmacaoPagamentoService confirmacao;
    private final CheckoutMatriculaGateway gateway;
    private final MatriculaPagBankProperties config;
    public PagamentoMatriculaService(MatriculaService matriculas, SessaoService sessao, CursoRepository cursos,
            RegistroPagamentoService registros, ConfirmacaoPagamentoService confirmacao,
            CheckoutMatriculaGateway gateway, MatriculaPagBankProperties config) {
        this.matriculas = matriculas; this.sessao = sessao; this.cursos = cursos; this.registros = registros;
        this.confirmacao = confirmacao; this.gateway = gateway; this.config = config;
    }
    public URI iniciar(String id) {
        if (sessao.exigirUsuarioLogado().getTipoUsuario() != TipoUsuario.ALUNO)
            throw new IllegalArgumentException("Somente o aluno pode iniciar o pagamento.");
        Matricula m = matriculas.buscarMatriculaDoAlunoLogado(id);
        if (m.getStatusMatricula() != StatusMatricula.AGUARDANDO_PAGAMENTO)
            throw new IllegalArgumentException("Esta matrícula não está aguardando pagamento.");
        Curso c = cursos.findById(m.getCursoId()).orElseThrow(() -> new IllegalArgumentException("Curso não encontrado."));
        if (c.getStatus() != StatusCurso.DISPONIVEL || Boolean.FALSE.equals(c.getAtivo())
                || c.getPreco() == null || c.getPreco().signum() <= 0 || c.getNome() == null || c.getNome().isBlank()
                || c.getNome().length() > 100)
            throw new IllegalArgumentException("Curso ou preço inválido.");
        int centavos = c.getPreco().movePointRight(2).intValueExact();
        if (centavos > 999999900) throw new IllegalArgumentException("Valor acima do limite do Checkout.");
        config.token();
        String retorno = config.url("/pagamentos/" + id);
        String webhook = config.url(WebhookMatriculaController.ROTA);
        var reserva = registros.reservarInicio(id, centavos);
        var t = reserva.tentativa();
        if (!reserva.criar()) {
            if (t.getCheckoutId() != null) {
                confirmacao.reconciliarCheckout(t.getReferencia());
                var p = registros.buscar(t.getReferencia());
                t = registros.tentativa(p, t.getReferencia());
                if (p.getStatus() == PagamentoMatricula.Estado.APROVADO)
                    throw new IllegalStateException("Pagamento confirmado. Atualize a página.");
                if (t.getStatus() == PagamentoMatricula.EstadoTentativa.EXPIRED)
                    throw new IllegalStateException("Checkout expirado. Inicie uma nova tentativa.");
            }
            if (t.getPayUrl() != null && t.getExpiracao() != null && t.getExpiracao().isAfter(Instant.now()))
                return PagBankMatriculaClient.validarPay(t.getPayUrl());
            throw new IllegalStateException("Aguardando conciliação do pagamento anterior. Não crie outra cobrança.");
        }
        try {
            var criado = gateway.criar(t.getReferencia(), c.getNome(), centavos, retorno, webhook, webhook);
            URI pay = PagBankMatriculaClient.validarPay(criado.payUrl().toString());
            registros.registrarCheckout(t.getReferencia(), criado.checkoutId(), pay.toString(), criado.expiracao());
            return pay;
        } catch (RuntimeException e) {
            registros.falharInicio(t.getReferencia());
            throw new PagBankMatriculaClient.Indisponivel();
        }
    }
}
