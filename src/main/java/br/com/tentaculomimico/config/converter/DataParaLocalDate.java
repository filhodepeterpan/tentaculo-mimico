package br.com.tentaculomimico.config.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

// As datas (data_inicio, data_fim, data_cadastro) são BSON Date gravadas em
// meia-noite UTC. O converter padrão do Spring Data usa o fuso do servidor; num
// servidor em America/Sao_Paulo (UTC-3) isso devolveria o dia anterior. Lendo em
// UTC, o dia bate com o que está no banco.
@ReadingConverter
public class DataParaLocalDate implements Converter<Date, LocalDate> {

    @Override
    public LocalDate convert(Date data) {
        return data.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
