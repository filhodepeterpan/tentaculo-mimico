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

    /** Valor que vai pro template (minúsculo, igual ao tipoUsuario). */
    public String chave() {
        return name().toLowerCase();
    }
}
