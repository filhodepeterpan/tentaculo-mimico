package br.com.tentaculomimico.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Document("pagamentos")
public class PagamentoMatricula {
    @Id private String id;
    @Version private Long versao;
    @Field(name = "matricula_id", targetType = FieldType.OBJECT_ID) private String matriculaId;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal valor;
    @Field("status_atual") private Estado status = Estado.PENDENTE;
    @Field("mes_referencia") private Instant competencia;
    @Field("data_vencimento") private Instant vencimento;
    @Field("criado_em") private Instant criadoEm = Instant.now();
    @Field("conciliacao_necessaria") private boolean conciliacaoNecessaria;
    private List<Tentativa> tentativas = new ArrayList<>();

    public enum Estado { PENDENTE, APROVADO }
    public enum EstadoTentativa { CRIANDO, INCERTA, WAITING, IN_ANALYSIS, PAID, DECLINED, CANCELED, EXPIRED }
    @Getter @Setter
    public static class Tentativa {
        @Field("reference_id") private String referencia;
        @Field("checkout_id") private String checkoutId;
        @Field("pay_url") private String payUrl;
        @Field("expira_em") private Instant expiracao;
        @Field("criado_em") private Instant criadoEm = Instant.now();
        @Field("falhou_em") private Instant falhouEm;
        private EstadoTentativa status = EstadoTentativa.CRIANDO;
        @Field("transacao_externa_id") private String pagamentoId;
        @Field("data_pagamento") private Instant pagoEm;
        private String metodo;
        private List<Transacao> transacoes = new ArrayList<>();
    }
    public record Transacao(@Field("transacao_externa_id") String id, String status,
                            @Field("valor_centavos") long centavos, String moeda, String metodo,
                            @Field("pago_em") Instant pagoEm) {}
}
