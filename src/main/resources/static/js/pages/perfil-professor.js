import '../componentes/modal.js';

function normalizar(texto) {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

function inicializarAbas() {
  const abas = document.querySelectorAll('[role="tab"]');
  const paineis = document.querySelectorAll('[role="tabpanel"]');

  abas.forEach((aba) => {
    aba.addEventListener('click', () => {
      abas.forEach((a) => a.setAttribute('aria-selected', 'false'));
      paineis.forEach((p) => { p.hidden = true; });

      aba.setAttribute('aria-selected', 'true');
      const painel = document.getElementById(aba.getAttribute('aria-controls'));
      if (painel) painel.hidden = false;
    });
  });
}

function inicializarBusca(idCampo, idLista, idVazio) {
  const campoBusca = document.getElementById(idCampo);
  const lista = document.getElementById(idLista);
  const mensagemVazio = document.getElementById(idVazio);
  if (!campoBusca || !lista) return;

  campoBusca.addEventListener('input', () => {
    const termos = normalizar(campoBusca.value).trim().split(/\s+/).filter(Boolean);
    const itens = lista.querySelectorAll('.aluno-item');
    let algumVisivel = false;

    itens.forEach((item) => {
      const nomeNormalizado = normalizar(item.dataset.nome);
      const corresponde = termos.length === 0 || termos.every((termo) => nomeNormalizado.includes(termo));
      item.hidden = !corresponde;
      if (corresponde) algumVisivel = true;
    });

    if (mensagemVazio) mensagemVazio.hidden = algumVisivel;
  });
}

function inicializarFormularios() {
  document.addEventListener('click', (evento) => {
    const botao = evento.target.closest('[id$="-confirmar"]');
    if (!botao) return;

    const form = document.getElementById('form-acao-perfil');

    if (botao.id === 'modal-excluir-conta-confirmar') {
      form.action = '/perfil/excluir';
      form.submit();
      return;
    }

    if (botao.id.startsWith('modal-aceitar-')) {
      const matriculaId = botao.id.replace('modal-aceitar-', '').replace('-confirmar', '');
      form.action = `/matriculas/${matriculaId}/aceitar`;
      form.submit();
      return;
    }

    if (botao.id.startsWith('modal-recusar-')) {
      const matriculaId = botao.id.replace('modal-recusar-', '').replace('-confirmar', '');
      form.action = `/matriculas/${matriculaId}/recusar`;
      form.submit();
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  inicializarAbas();
  inicializarBusca('busca-alunos-matriculados', 'painel-matriculados', 'matriculados-vazio');
  inicializarBusca('busca-alunos-pendentes', 'painel-pendentes', 'pendentes-vazio');
  inicializarFormularios();
});