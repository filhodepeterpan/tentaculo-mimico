package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Log;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LogService {

    // Logger para registrar falhas no console (não quebra a ação principal)
    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final LogRepository logRepository;
    private final SessaoService sessaoService;
    private final MongoTemplate mongoTemplate;

    public LogService(LogRepository logRepository, SessaoService sessaoService, MongoTemplate mongoTemplate) {
        this.logRepository = logRepository;
        this.sessaoService = sessaoService;
        this.mongoTemplate = mongoTemplate;
    }

    public void registrar(String tipoAcao, String descricao, String alvo, String alvoId, String alvoNome) {
        Usuario autor = null;
        try {
            autor = sessaoService.usuarioLogado();
        } catch (Exception e) {
            log.error("Falha ao descobrir o autor do log: {}", e.getMessage());
        }
        registrarComo(autor, tipoAcao, descricao, alvo, alvoId, alvoNome);
    }

    public void registrarComo(Usuario usuario, String tipoAcao, String descricao, String alvo, String alvoId, String alvoNome) {
        try {
            String autor = "Sistema";
            String autorId = null;
            String tipoDeUsuario = null;

            if (usuario != null) {
                autor = usuario.getNome();
                autorId = usuario.getId();
                tipoDeUsuario = usuario.getTipoUsuario().name();
            }

            Log novoLog = new Log(tipoAcao, tipoDeUsuario, descricao, autor, autorId, alvo, alvoId, alvoNome, LocalDateTime.now());
            logRepository.save(novoLog);
        } catch (Exception e) {
            log.error("Falha ao gravar log de ação '{}' sobre '{}': {}", tipoAcao, alvo, e.getMessage(), e);
        }
    }

    public Page<Log> listar(int pagina, String tipo, String usuario, LocalDate de, LocalDate ate) {
        Pageable pageable = PageRequest.of(Math.max(pagina, 0), 20, Sort.by(Sort.Direction.DESC, "dataHora"));
        Query query = new Query();

        if (tipo != null && !tipo.isBlank()) {
            query.addCriteria(Criteria.where("tipoAcao").is(tipo));
        }

        if (usuario != null && !usuario.isBlank()) {
            query.addCriteria(Criteria.where("tipoDeUsuario").is(usuario));
        }

        if (de != null || ate != null) {
            Criteria data = Criteria.where("dataHora");
            if (de != null) {
                data = data.gte(de.atStartOfDay());
            }
            if (ate != null) {
                data = data.lt(ate.plusDays(1).atStartOfDay());
            }
            query.addCriteria(data);
        }

        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Log.class);
        List<Log> itens = mongoTemplate.find(query.with(pageable), Log.class);
        return new PageImpl<>(itens, pageable, total);
    }
}