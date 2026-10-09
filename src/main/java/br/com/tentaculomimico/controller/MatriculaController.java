package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.service.MatriculaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Ações do professor sobre a solicitação (aceitar/recusar) e do aluno
// desistindo de uma matrícula já ativa (RN039/RN044: desistência vira
// status "cancelada", preservando o histórico, não apaga o registro).
@Controller
public class MatriculaController {

    private final MatriculaService matriculaService;

    public MatriculaController(MatriculaService matriculaService) {
        this.matriculaService = matriculaService;
    }

    // "voltar=curso" vem da página de detalhes do curso: o professor volta para ela
    // em vez de ir para o perfil. Só a palavra "curso" é aceita (nunca uma URL), para
    // não virar redirecionamento aberto.
    @PostMapping("/matriculas/{id}/aceitar")
    public String aceitarMatricula(@PathVariable String id,
                                   @RequestParam(required = false) String voltar) {
        String cursoId = matriculaService.aceitar(id);
        return redirecionarDepoisDeResponder(cursoId, voltar);
    }

    @PostMapping("/matriculas/{id}/recusar")
    public String recusarMatricula(@PathVariable String id,
                                   @RequestParam(required = false) String voltar) {
        String cursoId = matriculaService.recusar(id);
        return redirecionarDepoisDeResponder(cursoId, voltar);
    }

    @PostMapping("/matriculas/{id}/cancelar")
    public String cancelarMatricula(@PathVariable String id) {
        matriculaService.cancelar(id);
        return "redirect:/perfil/aluno/" + matriculaService.obterAlunoDaMatricula(id);
    }

    private String redirecionarDepoisDeResponder(String cursoId, String voltar) {
        if ("curso".equals(voltar)) {
            return "redirect:/cursos/" + cursoId;
        }
        // Auxiliar só pra montar o redirect de volta pro perfil do professor
        // logado depois de aceitar/recusar — o back pode simplificar isso se
        // já tiver o id do professor logado disponível em outro lugar (sessão).
        return "redirect:/perfil/professor/" + matriculaService.obterProfessorDoCurso(cursoId);
    }
}
