import '../componentes/modal.js';
import {
  inicializarAbas,
  inicializarBusca,
  inicializarRespostaMatriculas,
} from '../componentes/gestao-matriculas.js';

function inicializarExclusaoDeConta() {
  document.addEventListener('click', (evento) => {
    const botao = evento.target.closest('[id$="-confirmar"]');
    if (!botao || botao.id !== 'modal-excluir-conta-confirmar') return;

    const form = document.getElementById('form-acao-perfil');
    form.action = '/perfil/excluir';
    form.submit();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  inicializarAbas();
  inicializarBusca('busca-alunos-matriculados', 'painel-matriculados', 'matriculados-vazio');
  inicializarBusca('busca-alunos-pendentes', 'painel-pendentes', 'pendentes-vazio');
  inicializarRespostaMatriculas();
  inicializarExclusaoDeConta();
});
