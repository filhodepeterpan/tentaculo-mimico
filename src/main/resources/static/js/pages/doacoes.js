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

// Aceita "10", "10,5", "R$ 1.234,56", "12.50". Devolve número (ou NaN).
function lerValor(texto) {
  let t = String(texto).replace(/R\$/gi, '').replace(/\s/g, '');
  if (!t) return NaN;
  if (t.includes(',')) t = t.replace(/\./g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return NaN;
  return Number(t);
}

function inicializarEnvioDoacao() {
  const form = document.querySelector('.doacoes__formulario');
  const campoOutro = document.getElementById('valor-outro');
  const campoValor = document.getElementById('doacao-valor');
  const campoMensagem = document.getElementById('doacao-mensagem');
  const mensagem = document.getElementById('mensagem-doacao');

  form?.addEventListener('submit', (evento) => {
    const selecionado = document.querySelector('input[name="valorDoacao"]:checked');
    const valor = selecionado ? Number(selecionado.value) : lerValor(campoOutro?.value ?? '');

    if (!Number.isFinite(valor) || valor < 1) {
      evento.preventDefault();
      campoOutro?.focus();
      return;
    }

    // Segue o envio normal: o servidor guarda a doação e leva o doador à tela de pagamento.
    campoValor.value = valor.toFixed(2);
    campoMensagem.value = mensagem?.value ?? '';
  });
}

document.addEventListener('DOMContentLoaded', () => {
  inicializarValorDoacao();
  inicializarEnvioDoacao();
});