package br.com.tentaculomimico.config.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

// Grava a data em meia-noite UTC, casando com o formato que já está no banco.
@WritingConverter
public class LocalDateParaData implements Converter<LocalDate, Date> {

    @Override
    public Date convert(LocalDate data) {
        return Date.from(data.atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
