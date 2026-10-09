// Funções puras de formatação e validação de cartão. Nada aqui lê o DOM nem envia dados a lugar nenhum.

export function somenteDigitos(texto) {
  return String(texto ?? '').replace(/\D/g, '');
}

/** 'amex' (34/37), 'outro' (demais). Só serve para decidir tamanhos de número e CVV. */
export function detectarBandeira(numero) {
  const d = somenteDigitos(numero);
  return /^3[47]/.test(d) ? 'amex' : 'outro';
}

/** Agrupa o número digitado: 4-4-4-4 (ou 4-6-5 no Amex). */
export function formatarNumeroCartao(numero) {
  const amex = detectarBandeira(numero) === 'amex';
  const d = somenteDigitos(numero).slice(0, amex ? 15 : 16);
  const grupos = amex ? [4, 6, 5] : [4, 4, 4, 4];
  const partes = [];
  let inicio = 0;
  for (const tamanho of grupos) {
    if (inicio >= d.length) break;
    partes.push(d.slice(inicio, inicio + tamanho));
    inicio += tamanho;
  }
  return partes.join(' ');
}

/** Algoritmo de Luhn: pega número digitado errado, não garante que o cartão exista. */
export function luhnValido(numero) {
  const d = somenteDigitos(numero);
  if (d.length < 13) return false;
  let soma = 0;
  let dobrar = false;
  for (let i = d.length - 1; i >= 0; i -= 1) {
    let n = d.charCodeAt(i) - 48;
    if (dobrar) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    soma += n;
    dobrar = !dobrar;
  }
  return soma % 10 === 0;
}

export function numeroCartaoValido(numero) {
  const d = somenteDigitos(numero);
  const tamanhoEsperado = detectarBandeira(d) === 'amex' ? 15 : 16;
  return d.length === tamanhoEsperado && luhnValido(d);
}

/** '1' -> '1', '12' -> '12', '123' -> '12/3', '1225' -> '12/25'. */
export function formatarValidade(texto) {
  const d = somenteDigitos(texto).slice(0, 4);
  return d.length <= 2 ? d : `${d.slice(0, 2)}/${d.slice(2)}`;
}

/** MM/AA válido e ainda não vencido (o cartão vale até o último dia do mês indicado). */
export function validadeValida(texto, agora = new Date()) {
  const m = /^(\d{2})\/(\d{2})$/.exec(texto ?? '');
  if (!m) return false;
  const mes = Number(m[1]);
  const ano = 2000 + Number(m[2]);
  if (mes < 1 || mes > 12) return false;
  const anoAtual = agora.getFullYear();
  const mesAtual = agora.getMonth() + 1;
  if (ano < anoAtual || (ano === anoAtual && mes < mesAtual)) return false;
  return ano <= anoAtual + 20;
}

export function cvvValido(cvv, numero) {
  const tamanho = detectarBandeira(numero) === 'amex' ? 4 : 3;
  return somenteDigitos(cvv).length === tamanho && /^\d+$/.test(cvv);
}

/** Pelo menos duas palavras com letras (como impresso no cartão). */
export function nomeImpressoValido(nome) {
  const partes = String(nome ?? '').trim().split(/\s+/).filter(Boolean);
  return partes.length >= 2 && partes.every((p) => /\p{L}/u.test(p));
}
