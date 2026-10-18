package br.com.tentaculomimico.config.converter;

import br.com.tentaculomimico.model.DiaSemana;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

// Grava "dia_semana" como número, e não como o nome do enum.
@WritingConverter
public class DiaSemanaParaNumero implements Converter<DiaSemana, Integer> {

    @Override
    public Integer convert(DiaSemana dia) {
        return dia.getNumero();
    }
}
