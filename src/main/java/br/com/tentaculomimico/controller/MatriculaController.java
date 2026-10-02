package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.service.MatriculaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

// Ações do professor sobre a solicitação (aceitar/recusar) e do aluno
// desistindo de uma matrícula já ativa (RN039/RN044: desistência vira
// status "cancelada", preservando o histórico, não apaga o registro).
@Controller
public class MatriculaController {

    private final MatriculaService matriculaService;

    public MatriculaController(MatriculaService matriculaService) {
        this.matriculaService = matriculaService;
    }

    @PostMapping("/matriculas/{id}/aceitar")
    public String aceitarMatricula(@PathVariable String id, Model model) {
        String cursoId = matriculaService.aceitar(id);
        return "redirect:/perfil/professor/" + obterProfessorDoCurso(cursoId);
    }

    @PostMapping("/matriculas/{id}/recusar")
    public String recusarMatricula(@PathVariable String id, Model model) {
        String cursoId = matriculaService.recusar(id);
        return "redirect:/perfil/professor/" + obterProfessorDoCurso(cursoId);
    }

    @PostMapping("/matriculas/{id}/cancelar")
    public String cancelarMatricula(@PathVariable String id) {
        matriculaService.cancelar(id);
        return "redirect:/perfil/aluno/" + matriculaService.obterAlunoDaMatricula(id);
    }

    // Auxiliar só pra montar o redirect de volta pro perfil do professor
    // logado depois de aceitar/recusar — o back pode simplificar isso se
    // já tiver o id do professor logado disponível em outro lugar (sessão).
    private String obterProfessorDoCurso(String cursoId) {
        return matriculaService.obterProfessorDoCurso(cursoId);
    }
}