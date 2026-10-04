package br.com.tentaculomimico.model;

public enum StatusMatricula {
    PENDENTE,               // aluno solicitou, professor ainda não respondeu
    AGUARDANDO_PAGAMENTO,   // professor aceitou (a vaga já está reservada)
    ATIVA,                  // paga / em andamento
    RECUSADA,               // professor recusou
    CANCELADA;              // aluno desistiu (RN039/RN044: mantém o histórico)

    /** Status que ocupam uma vaga do curso. */
    public boolean ocupaVaga() {
        return this == AGUARDANDO_PAGAMENTO || this == ATIVA;
    }

    /** Status ainda "vivos" (nem recusada, nem cancelada). */
    public boolean emAberto() {
        return this == PENDENTE || ocupaVaga();
    }

    /**
     * Valor que vai pro template e pro banco (campo "status" do schema de matriculas):
     * minúsculo, exceto AGUARDANDO_PAGAMENTO, que o schema e os templates chamam
     * de "aceita_aguardando_pagamento".
     */
    public String chave() {
        return this == AGUARDANDO_PAGAMENTO ? "aceita_aguardando_pagamento" : name().toLowerCase();
    }
}
