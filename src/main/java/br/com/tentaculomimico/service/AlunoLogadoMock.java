
package br.com.tentaculomimico.service;

import org.springframework.stereotype.Component;

//USUÁRIO DE TESTE FAKE
//ESTE AQRUIVO É TEMPORÁRIO, E SERÁ DELETADO

@Component
public class AlunoLogadoMock implements AlunoLogadoProvider {

    @Override
    public String obterAlunoIdAtual() {
        return "6aac99c8712c34d2ebd831e2";
    }

    @Override
    public String obterAlunoNomeAtual() {
        return "Aluno de Teste";
    }
}