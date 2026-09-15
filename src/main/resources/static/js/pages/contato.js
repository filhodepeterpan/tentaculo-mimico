import '../componentes/alert.js';

function inicializarContato() {
  const form = document.querySelector('.contato__formulario');
  const sucesso = document.getElementById('contato-sucesso');

  form?.addEventListener('submit', (evento) => {
    evento.preventDefault();
    sucesso.hidden = false;
    form.reset();
  });
}

document.addEventListener('DOMContentLoaded', inicializarContato);
