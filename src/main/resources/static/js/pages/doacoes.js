function lerValor(texto) {
  let t = String(texto).replace(/R\$/g, '').replace(/\s/g, '');
  if (/^\d{1,3}(\.\d{3})+,\d{1,2}$/.test(t)) t = t.replace(/\./g, '').replace(',', '.');
  else if (/^\d+,\d{1,2}$/.test(t)) t = t.replace(',', '.');
  else if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
  const centavos = Math.round(Number(t) * 100);
  return Number.isSafeInteger(centavos) && centavos >= 100 && centavos <= 1000000
    ? (centavos / 100).toFixed(2) : null;
}

function inicializarEnvioDoacao() {
  const form = document.querySelector('.doacoes__formulario');
  const campoOutro = document.getElementById('valor-outro');
  const campoValor = document.getElementById('doacao-valor');
  const erro = document.getElementById('doacao-erro-valor');
  const botao = document.querySelector('.doacoes__doar');
  let enviando = false;

  document.querySelectorAll('input[name="valorDoacao"]').forEach((radio) => {
    radio.addEventListener('change', () => { if (campoOutro) campoOutro.value = ''; });
  });
  campoOutro?.addEventListener('input', () => {
    document.querySelectorAll('input[name="valorDoacao"]').forEach((radio) => { radio.checked = false; });
  });

  form?.addEventListener('submit', (evento) => {
    if (enviando) { evento.preventDefault(); return; }
    const selecionado = document.querySelector('input[name="valorDoacao"]:checked');
    const valor = lerValor(selecionado?.value ?? campoOutro?.value ?? '');
    if (!valor) {
      evento.preventDefault();
      erro.textContent = 'Informe um valor entre R$ 1,00 e R$ 10.000,00, com até duas casas decimais.';
      erro.hidden = false;
      campoOutro?.focus();
      return;
    }
    erro.hidden = true;
    campoValor.value = valor;
    enviando = true;
    botao.disabled = true;
    botao.querySelector('.doacoes__doar-texto').textContent = 'Abrindo PagBank…';
  });

  // Ao voltar pelo botão Voltar, o navegador pode restaurar o formulário do cache.
  window.addEventListener('pageshow', () => {
    enviando = false;
    botao.disabled = false;
    botao.querySelector('.doacoes__doar-texto').textContent = 'Doar';
  });
}

function inicializarAgradecimento() {
  const modal = document.getElementById('modal-agradecimento-doacao');
  if (modal?.dataset.mostrar !== 'true') return;
  const url = new URL(window.location.href);
  url.searchParams.delete('retorno');
  window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
  window.bootstrap.Modal.getOrCreateInstance(modal).show();
}

document.addEventListener('DOMContentLoaded', () => {
  inicializarEnvioDoacao();
  inicializarAgradecimento();
});
