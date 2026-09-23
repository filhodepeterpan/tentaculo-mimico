package br.com.tentaculomimico.service;

import org.springframework.stereotype.Component;

public interface AlunoLogadoProvider {

    String obterAlunoIdAtual();

    String obterAlunoNomeAtual();
}

