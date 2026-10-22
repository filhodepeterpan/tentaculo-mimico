package br.com.tentaculomimico.model;

import br.com.tentaculomimico.config.MongoConfig;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Trava o mapeamento da coleção "cursos" no formato que o banco usa (snake_case),
 * montando um MappingMongoConverter igual ao do Spring Boot — sem precisar de um
 * MongoDB rodando. Regressão direta do bug em que o curso aparecia sempre lotado.
 */
class CursoMapeamentoTest {

    private static final String DOCUMENTO_BANCO = """
            {
              "_id": {"$oid": "6ac2899967b0301aa66633b7"},
              "professor_id": {"$oid": "6ac2899967b0301aa66633b3"},
              "professor_nome": "Professor Teste 02",
              "nome": "Banco de Dados Relacional",
              "descricao": "Fundamentos de modelagem, SQL e bancos de dados relacionais.",
              "imagem_capa": null,
              "carga_horaria": 40,
              "horarios": [
                {"dia_semana": 2, "hora_inicio": "18:30", "hora_fim": "20:30"},
                {"dia_semana": 4, "hora_inicio": "18:30", "hora_fim": "20:30"}
              ],
              "preco": {"$numberDecimal": "129.90"},
              "vagas_totais": 35,
              "vagas_disponiveis": 35,
              "status": "DISPONIVEL",
              "data_inicio": {"$date": "2026-11-09T00:00:00.000Z"},
              "data_fim": {"$date": "2026-12-09T00:00:00.000Z"},
              "data_cadastro": {"$date": "2026-10-04T17:15:05.592Z"},
              "ativo": true
            }
            """;

    private final MappingMongoConverter converter = criarConverter();

    private static MappingMongoConverter criarConverter() {
        MongoCustomConversions conversions = new MongoConfig().mongoCustomConversions();

        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());

        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return converter;
    }

    @Test
    void leCursoDoBancoComTodosOsCampos() {
        Curso curso = converter.read(Curso.class, Document.parse(DOCUMENTO_BANCO));

        assertEquals("6ac2899967b0301aa66633b7", curso.getId());
        assertEquals("6ac2899967b0301aa66633b3", curso.getProfessorId());
        assertEquals("Professor Teste 02", curso.getProfessorNome());
        assertEquals("Banco de Dados Relacional", curso.getNome());
        assertEquals(40, curso.getCargaHoraria());

        // O bug relatado: vinham 0, então o curso aparecia lotado/indisponível.
        assertEquals(35, curso.getVagasTotais());
        assertEquals(35, curso.getVagasDisponiveis());

        assertEquals(new BigDecimal("129.90"), curso.getPreco());
        assertEquals(StatusCurso.DISPONIVEL, curso.getStatus());
        assertEquals(LocalDate.of(2026, 11, 9), curso.getDataInicio());
        assertEquals(LocalDate.of(2026, 12, 9), curso.getDataFim());
        assertEquals(LocalDate.of(2026, 10, 4), curso.getDataCadastro());
        assertEquals(Boolean.TRUE, curso.getAtivo());

        List<Horario> horarios = curso.getHorarios();
        assertEquals(2, horarios.size());
        assertEquals(DiaSemana.SEGUNDA, horarios.get(0).getDiaSemana());
        assertEquals(LocalTime.of(18, 30), horarios.get(0).getInicio());
        assertEquals(LocalTime.of(20, 30), horarios.get(0).getFim());
        assertEquals(DiaSemana.QUARTA, horarios.get(1).getDiaSemana());
    }

    @Test
    void gravaCursoNoFormatoDoBanco() {
        Curso curso = converter.read(Curso.class, Document.parse(DOCUMENTO_BANCO));

        Document doc = new Document();
        converter.write(curso, doc);

        assertEquals(35, doc.get("vagas_disponiveis"));
        assertEquals(35, doc.get("vagas_totais"));
        assertEquals(40, doc.get("carga_horaria"));
        assertEquals("Professor Teste 02", doc.get("professor_nome"));
        assertInstanceOf(ObjectId.class, doc.get("professor_id"));
        // Não deve sobrar chave em camelCase.
        assertFalse(doc.containsKey("vagasDisponiveis"));
        assertFalse(doc.containsKey("vagasTotais"));

        assertInstanceOf(Date.class, doc.get("data_inicio"));
        assertEquals(LocalDate.of(2026, 11, 9),
                ((Date) doc.get("data_inicio")).toInstant().atZone(ZoneId.of("UTC")).toLocalDate());

        List<Document> horarios = doc.getList("horarios", Document.class);
        assertEquals(2, horarios.get(0).get("dia_semana"));
        assertEquals("18:30", horarios.get(0).get("hora_inicio"));
        assertEquals("20:30", horarios.get(0).get("hora_fim"));
        assertEquals(4, horarios.get(1).get("dia_semana"));
    }

    // A leitura de data passou a ser em UTC; garante que isso não desloca a data
    // de nascimento do usuário (que o app grava como LocalDate na meia-noite local).
    @Test
    void dataNascimentoDoUsuarioNaoMuda() {
        LocalDate nascimento = LocalDate.of(2000, 5, 10);
        Document doc = new Document("data_nascimento",
                Date.from(nascimento.atStartOfDay(ZoneId.systemDefault()).toInstant()));

        Usuario usuario = converter.read(Usuario.class, doc);

        assertEquals(nascimento, usuario.getDataNascimento());
    }
}
