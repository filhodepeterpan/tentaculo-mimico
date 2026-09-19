package br.com.tentaculomimico.service;

import org.springframework.stereotype.Component;


//USUÁRIO DE TESTE FAKE
//ESTE AQRUIVO É TEMPORÁRIO, E SERÁ DELETADO



@Component
public class ProfessorLogadoMock implements ProfessorLogadoProvider {

    @Override
    public String obterProfessorIdAtual() {
        return "6aac99c8712c34d2ebd831e2";
    }

    @Override
    public String obterProfessorNomeAtual() {
        return "Professor de Teste";
    }

}