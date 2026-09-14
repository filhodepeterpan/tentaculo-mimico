import '../componentes/alert.js';
import '../componentes/senha.js';
import '../componentes/modal-cpf.js'

document.querySelector('.login__formulario')?.addEventListener('submit', (evento) => {
  const botao = evento.target.querySelector('button[type="submit"]');
  if (botao) botao.disabled = true;
});