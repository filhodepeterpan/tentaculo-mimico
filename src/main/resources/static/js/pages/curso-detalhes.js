import '../componentes/modal.js';
import {
  inicializarAbas,
  inicializarBusca,
  inicializarRespostaMatriculas,
} from '../componentes/gestao-matriculas.js';

document.getElementById('modal-matricula-confirmar')?.addEventListener('click', () => {
    document.getElementById('form-matricula')?.submit();
});

// Aluno desiste da matrícula ativa: confirma no modal e envia o form oculto (POST /matriculas/{id}/cancelar).
document.getElementById('modal-desistir-confirmar')?.addEventListener('click', () => {
    document.getElementById('form-desistir')?.submit();
});

// Professor exclui o curso: confirma no modal e envia o form oculto (POST /cursos/{id}/excluir).
document.getElementById('modal-deletar-curso-confirmar')?.addEventListener('click', () => {
    document.getElementById('form-deletar-curso')?.submit();
});

// Seção "Alunos do curso": só existe na página para o professor dono do curso.
const gestao = document.querySelector('.gestao-matriculas');
if (gestao) {
  inicializarAbas(gestao);
  inicializarBusca('busca-alunos-matriculados', 'painel-matriculados', 'matriculados-vazio');
  inicializarBusca('busca-alunos-pendentes', 'painel-pendentes', 'pendentes-vazio');
  inicializarRespostaMatriculas({ voltar: 'curso' });
}