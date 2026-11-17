import { abrirModal, fecharModal } from './modal.js';

// Formulários com data-modal-confirmar="<id do modal>" só são enviados depois
// que o usuário clica no botão de confirmar do modal correspondente.
// O botão de confirmar tem id "<id do modal>-confirmar" (ver fragments/modal).

let formularioPendente = null;

function inicializarConfirmacaoDeFormularios() {
  document.addEventListener('submit', (evento) => {
    const formulario = evento.target.closest('form[data-modal-confirmar]');
    if (!formulario) return;

    const modal = document.getElementById(formulario.dataset.modalConfirmar);
    // Sem o modal na página, deixa o envio seguir em vez de travar o botão.
    if (!modal) return;

    evento.preventDefault();
    formularioPendente = formulario;
    abrirModal(modal);
    document.getElementById(`${modal.id}-confirmar`)?.focus();
  });

  document.addEventListener('click', (evento) => {
    const botao = evento.target.closest('.modal button[id$="-confirmar"]');
    if (!botao || !formularioPendente) return;

    const idModal = formularioPendente.dataset.modalConfirmar;
    if (botao.id !== `${idModal}-confirmar`) return;

    const formulario = formularioPendente;
    formularioPendente = null;
    botao.disabled = true;
    fecharModal(document.getElementById(idModal));
    // submit() não dispara o evento "submit" de novo, então não cai em loop.
    formulario.submit();
  });

  // Cancelar, X, overlay ou Esc: descarta o formulário pendente.
  const descartar = () => { formularioPendente = null; };
  document.addEventListener('click', (evento) => {
    if (evento.target.closest('[data-modal-fechar]')) descartar();
  });
  document.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') descartar();
  });
}

document.addEventListener('DOMContentLoaded', inicializarConfirmacaoDeFormularios);

export { inicializarConfirmacaoDeFormularios };
