import '../componentes/alert.js';
import '../componentes/senha.js';
import '../componentes/modal-cpf.js'

document.getElementById('confirmar-senha')?.addEventListener('input', (evento) => {
  evento.target.setCustomValidity('');
});

document.querySelector('.cadastro__formulario')?.addEventListener('submit', (evento) => {
  const senha = document.getElementById('senha');
  const confirmarSenha = document.getElementById('confirmar-senha');

  if (senha && confirmarSenha && senha.value !== confirmarSenha.value) {
    evento.preventDefault();
    confirmarSenha.setCustomValidity('As senhas não coincidem');
    confirmarSenha.reportValidity();
    return;
  }

  const botao = evento.target.querySelector('button[type="submit"]');
  if (botao) botao.disabled = true;
});
