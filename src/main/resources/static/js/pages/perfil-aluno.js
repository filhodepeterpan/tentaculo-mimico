import '../componentes/modal.js';

// Excluir conta: confirma no modal e envia o form oculto (POST /perfil-excluir).
// Se a conta não puder ser excluída, o servidor volta pra cá com um modal explicando o motivo.
document.getElementById('modal-excluir-conta-confirmar')?.addEventListener('click', () => {
  const form = document.getElementById('form-acao-perfil');
  if (!form) return;
  form.action = '/perfil-excluir';
  form.submit();
});

// Filtro por status dos cursos (só existe no perfil próprio do aluno).
// As opções do select casam com o data-status de cada card (fragments/card-matricula).
const STATUS_POR_OPCAO = {
  'Pendente': 'pendente',
  'Aguardando pagamento': 'aceita_aguardando_pagamento',
  'Matriculado': 'ativa',
};

const filtro = document.getElementById('filtro-status');
if (filtro) {
  const mensagemVazio = document.getElementById('filtro-vazio');

  filtro.addEventListener('change', () => {
    const desejado = STATUS_POR_OPCAO[filtro.value]; // undefined = "Todos"
    let algumVisivel = false;

    document.querySelectorAll('.cursos__grid [data-status]').forEach((card) => {
      const mostrar = !desejado || card.dataset.status === desejado;
      card.style.display = mostrar ? '' : 'none';
      if (mostrar) algumVisivel = true;
    });

    if (mensagemVazio) mensagemVazio.hidden = algumVisivel;
  });
}
