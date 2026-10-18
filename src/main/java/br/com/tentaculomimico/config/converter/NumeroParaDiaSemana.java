package br.com.tentaculomimico.config.converter;

import br.com.tentaculomimico.model.DiaSemana;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.convert.ReadingConverter;

import java.util.Set;

// O banco grava "dia_semana" como número (1=Domingo ... 7=Sábado), o mesmo valor
// de DiaSemana.getNumero().
//
// Precisa registrar o par EXATO (Integer, DiaSemana) — e não (Number, DiaSemana):
// o conversor global ObjectToEnumConverterFactory casa com qualquer enum e, pela
// ordem de resolução do Spring, seria consultado antes e devolveria o enum pelo
// ORDINAL (dia_semana 2 viraria TERCA em vez de SEGUNDA).
@ReadingConverter
public class NumeroParaDiaSemana implements GenericConverter {

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Set.of(
                new ConvertiblePair(Integer.class, DiaSemana.class),
                new ConvertiblePair(Long.class, DiaSemana.class),
                new ConvertiblePair(Double.class, DiaSemana.class)
        );
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        int valor = ((Number) source).intValue();
        for (DiaSemana dia : DiaSemana.values()) {
            if (dia.getNumero() == valor) {
                return dia;
            }
        }
        throw new IllegalArgumentException("Dia da semana inválido: " + source);
    }
}
