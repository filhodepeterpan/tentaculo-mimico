package br.com.tentaculomimico.config.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.time.LocalTime;

// O banco grava hora_inicio/hora_fim como texto ("18:30"), não como Date.
@ReadingConverter
public class TextoParaHora implements Converter<String, LocalTime> {

    @Override
    public LocalTime convert(String texto) {
        return LocalTime.parse(texto);
    }
}
