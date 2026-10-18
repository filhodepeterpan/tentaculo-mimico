package br.com.tentaculomimico.config;

import br.com.tentaculomimico.config.converter.DataParaLocalDate;
import br.com.tentaculomimico.config.converter.DiaSemanaParaNumero;
import br.com.tentaculomimico.config.converter.HoraParaTexto;
import br.com.tentaculomimico.config.converter.LocalDateParaData;
import br.com.tentaculomimico.config.converter.NumeroParaDiaSemana;
import br.com.tentaculomimico.config.converter.TextoParaHora;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;

// Ensina ao Spring Data os formatos que o banco já usa na coleção "cursos"
// (o dicionário do banco não muda; o código é que passa a falar o formato dele):
//   - dia_semana     -> número 1..7 (DiaSemana.getNumero())
//   - hora_inicio/fim-> texto "HH:mm"
//   - data_*         -> BSON Date em meia-noite UTC
// Os nomes dos campos em si (snake_case) são resolvidos por @Field nos models.
//
// Este bean substitui o MongoCustomConversions padrão do Boot — que é
// @ConditionalOnMissingBean e, por padrão, só configura a representação de
// BigDecimal (que aqui fica no default "UNSPECIFIED", igual ao do Boot).
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(conversions -> conversions.registerConverters(List.of(
                new NumeroParaDiaSemana(),
                new DiaSemanaParaNumero(),
                new TextoParaHora(),
                new HoraParaTexto(),
                new DataParaLocalDate(),
                new LocalDateParaData()
        )));
    }
}
