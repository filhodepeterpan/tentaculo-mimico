function inicializarCampoImagem(input) {
  const wrapper = input.closest('.campo-imagem');
  const placeholder = wrapper.querySelector('[data-campo-imagem-placeholder]');
  const preview = wrapper.querySelector('[data-campo-imagem-preview]');

  input.addEventListener('change', () => {
    const arquivo = input.files[0];

    if (!arquivo) {
      preview.hidden = true;
      placeholder.hidden = false;
      return;
    }

    const leitor = new FileReader();
    leitor.onload = (evento) => {
      preview.src = evento.target.result;
      preview.hidden = false;
      placeholder.hidden = true;
    };
    leitor.readAsDataURL(arquivo);
  });
}

function inicializarCamposImagem() {
  document.querySelectorAll('.campo-imagem__input').forEach(inicializarCampoImagem);
}

document.addEventListener('DOMContentLoaded', inicializarCamposImagem);
