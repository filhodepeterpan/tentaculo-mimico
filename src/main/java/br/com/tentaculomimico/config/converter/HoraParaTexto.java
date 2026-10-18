package br.com.tentaculomimico.config.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

// Grava a hora como texto "HH:mm" (mesmo formato dos inputs do formulário).
@WritingConverter
public class HoraParaTexto implements Converter<LocalTime, String> {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convert(LocalTime hora) {
        return hora.format(HHMM);
    }
}
