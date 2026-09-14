function fecharModal(modal) {
  modal.hidden = true;
}

function abrirModal(modal) {
  modal.hidden = false;
}

function inicializarModais() {
  document.addEventListener('click', (evento) => {
    const gatilhoAbrir = evento.target.closest('[data-modal-abrir]');
    if (gatilhoAbrir) {
      const modal = document.getElementById(gatilhoAbrir.getAttribute('data-modal-abrir'));
      if (modal) abrirModal(modal);
      return;
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
