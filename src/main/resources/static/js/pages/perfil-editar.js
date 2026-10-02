import '../componentes/alert.js';
import '../componentes/imagem.js';

document.querySelector('.perfil-editar__formulario')?.addEventListener('submit', (evento) => {
  const botao = evento.target.querySelector('button[type="submit"]');
  if (botao) botao.disabled = true;
});