import qrcode from '../vendor/qrcode.mjs';
import {
  cvvValido,
  formatarNumeroCartao,
  formatarValidade,
  nomeImpressoValido,
  numeroCartaoValido,
  validadeValida,
} from '../componentes/validacao-cartao.js';

const $ = (id) => document.getElementById(id);

const TITULOS = { credito: 'Cartão de crédito', debito: 'Cartão de débito' };
const CAMPOS_CARTAO = ['cartao-numero', 'cartao-nome', 'cartao-validade', 'cartao-cvv'];

// ---------- Escolha do método ----------

function metodoSelecionado() {
  return document.querySelector('input[name="metodo"]:checked')?.value ?? 'pix';
}

function aplicarMetodo() {
  const metodo = metodoSelecionado();
  const pix = metodo === 'pix';
  $('painel-pix').hidden = !pix;
  $('painel-cartao').hidden = pix;
  if (!pix) $('cartao-titulo').textContent = TITULOS[metodo];
}

// ---------- Pix ----------

function desenharQr() {
  const alvo = $('pix-qr');
  const payload = alvo?.dataset.payload;
  if (!alvo || !payload) return;
  try {
    const qr = qrcode(0, 'M');
    qr.addData(payload);
    qr.make();
    alvo.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true });
    const svg = alvo.querySelector('svg');
    svg?.setAttribute('role', 'img');
    svg?.setAttribute('aria-label', 'QR Code do Pix para pagamento');
  } catch (erro) {
    alvo.textContent = 'Não foi possível gerar o QR Code. Use o código “copia e cola”.';
  }
}

function formatarTempo(segundos) {
  const m = String(Math.floor(segundos / 60)).padStart(2, '0');
  const s = String(segundos % 60).padStart(2, '0');
  return `${m}:${s}`;
}

function iniciarContagem() {
  const area = $('pix-area');
  const relogio = $('pix-tempo');
  if (!area || !relogio) return;

  const duracao = Number(area.dataset.duracao) || 900;
  const fim = Date.now() + duracao * 1000;

  const atualizar = () => {
    const restante = Math.max(0, Math.ceil((fim - Date.now()) / 1000));
    relogio.textContent = formatarTempo(restante);
    if (restante > 0) return false;
    area.hidden = true;
    $('pix-confirmar').hidden = true;
    $('pix-expirado').hidden = false;
    return true;
  };

  if (atualizar()) return;
  const timer = setInterval(() => {
    if (atualizar()) clearInterval(timer);
  }, 1000);
}

async function copiarCodigo() {
  const campo = $('pix-codigo');
  const status = $('pix-copiar-status');
  let copiou = false;
  try {
    await navigator.clipboard.writeText(campo.value);
    copiou = true;
  } catch (erro) {
    campo.select();
    try { copiou = document.execCommand('copy'); } catch (e) { copiou = false; }
  }
  status.textContent = copiou ? 'Código copiado!' : 'Não foi possível copiar. Selecione o código e copie manualmente.';
  setTimeout(() => { status.textContent = ''; }, 3000);
}

// ---------- Cartão ----------

function mostrarErro(idCampo, mensagem) {
  const campo = $(idCampo);
  const erro = $(`${idCampo}-erro`);
  if (!campo || !erro) return;
  const temErro = Boolean(mensagem);
  campo.classList.toggle('campo__input--erro', temErro);
  campo.setAttribute('aria-invalid', String(temErro));
  if (temErro) campo.setAttribute('aria-describedby', erro.id);
  else campo.removeAttribute('aria-describedby');
  erro.textContent = mensagem || '';
  erro.hidden = !temErro;
}

const REGRAS = {
  'cartao-numero': () => (numeroCartaoValido($('cartao-numero').value) ? '' : 'Confira o número do cartão.'),
  'cartao-nome': () => (nomeImpressoValido($('cartao-nome').value) ? '' : 'Informe o nome como está no cartão.'),
  'cartao-validade': () => (validadeValida($('cartao-validade').value) ? '' : 'Informe uma validade válida (MM/AA).'),
  'cartao-cvv': () => (cvvValido($('cartao-cvv').value, $('cartao-numero').value) ? '' : 'Código de segurança inválido.'),
};

function validarCampo(idCampo) {
  const mensagem = REGRAS[idCampo]();
  mostrarErro(idCampo, mensagem);
  return mensagem === '';
}

function validarCartao() {
  // Valida todos (para mostrar todos os erros de uma vez) e foca o primeiro com problema.
  const resultados = CAMPOS_CARTAO.map((id) => [id, validarCampo(id)]);
  const primeiroInvalido = resultados.find(([, ok]) => !ok);
  if (primeiroInvalido) $(primeiroInvalido[0]).focus();
  return !primeiroInvalido;
}

function prepararCamposCartao() {
  const numero = $('cartao-numero');
  const validade = $('cartao-validade');
  const cvv = $('cartao-cvv');

  numero?.addEventListener('input', () => {
    const formatado = formatarNumeroCartao(numero.value);
    if (formatado !== numero.value) numero.value = formatado;
    cvv.maxLength = /^3[47]/.test(formatado) ? 4 : 3;
    if (numero.getAttribute('aria-invalid') === 'true') validarCampo('cartao-numero');
  });
  validade?.addEventListener('input', () => {
    const formatado = formatarValidade(validade.value);
    if (formatado !== validade.value) validade.value = formatado;
    if (validade.getAttribute('aria-invalid') === 'true') validarCampo('cartao-validade');
  });
  cvv?.addEventListener('input', () => {
    cvv.value = cvv.value.replace(/\D/g, '');
    if (cvv.getAttribute('aria-invalid') === 'true') validarCampo('cartao-cvv');
  });

  CAMPOS_CARTAO.forEach((id) => {
    $(id)?.addEventListener('blur', () => { if ($(id).value !== '') validarCampo(id); });
    // Enter dispara o pagamento (os campos não estão num <form>, de propósito).
    $(id)?.addEventListener('keydown', (evento) => {
      if (evento.key === 'Enter') {
        evento.preventDefault();
        $('cartao-confirmar').click();
      }
    });
  });
}

// ---------- Envio ----------

function bloquearBotoes(bloqueado) {
  ['pix-confirmar', 'cartao-confirmar'].forEach((id) => {
    const botao = $(id);
    if (botao) botao.disabled = bloqueado;
  });
  document.querySelector('.pagamento')?.setAttribute('aria-busy', String(bloqueado));
}

function enviar(metodo) {
  $('campo-metodo').value = metodo;
  bloquearBotoes(true);
  // Apaga o que foi digitado: o dado do cartão não precisa ficar na página depois do envio.
  CAMPOS_CARTAO.forEach((id) => { if ($(id)) $(id).value = ''; });
  $('form-pagamento').submit();
}

function iniciar() {
  if (!$('painel-pix')) return; // tela de "pagamento concluído": nada a fazer

  desenharQr();
  iniciarContagem();
  prepararCamposCartao();
  aplicarMetodo();

  document.querySelectorAll('input[name="metodo"]').forEach((r) => r.addEventListener('change', aplicarMetodo));
  $('pix-copiar')?.addEventListener('click', copiarCodigo);
  $('pix-confirmar')?.addEventListener('click', () => enviar('pix'));
  $('cartao-confirmar')?.addEventListener('click', () => {
    if (validarCartao()) enviar(metodoSelecionado());
  });

  // Voltar pelo histórico restaura a página do cache com os botões travados: destrava.
  window.addEventListener('pageshow', (evento) => { if (evento.persisted) bloquearBotoes(false); });
}

iniciar();
