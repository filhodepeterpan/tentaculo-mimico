function fecharModal(modal) {
  modal.hidden = true;
}

function abrirModal(modal) {
  modal.hidden = false;
}

function inicializarModais() {
  document.addEventListener('click', (evento) => {
    // Um botão pode ter os dois atributos (fecha um modal e abre outro no
    // mesmo clique) — por isso não damos "return" cedo, os dois são checados.
    const gatilhoAbrir = evento.target.closest('[data-modal-abrir]');
    if (gatilhoAbrir) {
      const modal = document.getElementById(gatilhoAbrir.getAttribute('data-modal-abrir'));
      if (modal) abrirModal(modal);
    }

    const gatilhoFechar = evento.target.closest('[data-modal-fechar]');
    if (gatilhoFechar) {
      const modal = document.getElementById(gatilhoFechar.getAttribute('data-modal-fechar'));
      if (modal) fecharModal(modal);
    }
  });

  document.addEventListener('keydown', (evento) => {
    if (evento.key !== 'Escape') return;
    document.querySelectorAll('.modal:not([hidden]):not(.modal--bloqueante)').forEach(fecharModal);
  });
}

document.addEventListener('DOMContentLoaded', inicializarModais);

export { abrirModal, fecharModal, inicializarModais };
