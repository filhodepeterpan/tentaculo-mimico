import '../componentes/modal.js';

document.getElementById('modal-matricula-confirmar')?.addEventListener('click', () => {
    document.getElementById('form-matricula')?.submit();
});