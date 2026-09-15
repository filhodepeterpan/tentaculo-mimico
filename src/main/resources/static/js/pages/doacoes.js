import '../componentes/modal.js';

function inicializarValorDoacao() {
  const radios = document.querySelectorAll('input[name="valorDoacao"]');
  const campoOutro = document.getElementById('valor-outro');

  radios.forEach((radio) => {
    radio.addEventListener('change', () => {
      if (campoOutro) campoOutro.value = '';
    });
  });

  campoOutro?.addEventListener('input', () => {
    radios.forEach((radio) => { radio.checked = false; });
  });
}

function inicializarEnvioDoacao() {
  const form = document.querySelector('.doacoes__formulario');
  const modal = document.getElementById('modal-doacao-sucesso');
  const campoOutro = document.getElementById('valor-outro');

  form?.addEventListener('submit', (evento) => {
    evento.preventDefault();

    const algumValorSelecionado = document.querySelector('input[name="valorDoacao"]:checked');
    if (!algumValorSelecionado && !campoOutro.value.trim()) {
      campoOutro.focus();
      return;
    }

    if (modal) modal.hidden = false;
    form.reset();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  inicializarValorDoacao();
  inicializarEnvioDoacao();
});
