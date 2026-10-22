// Escolha de horário no calendário (só para professores).
//
// O professor clica num espaço livre e aparece um bloco de 1 hora. Dá para arrastar o
// bloco (muda dia e hora), puxar as bordas de cima e de baixo para esticar e confirmar
// no botão ✓, que abre o cadastro de curso já com dia e horário marcados.
// O bloco nunca fica por cima de outro curso: ao encostar, ele para na beirada ou
// pula para o espaço livre mais próximo.
//
// Só os cursos da semana mostrada bloqueiam. As datas de início e de término ainda não
// existem nesta etapa, então a conferência completa de conflitos (que considera o
// período de cada curso) continua sendo feita pelo servidor ao salvar o cadastro.
//
// Teclado: Tab até o dia, Enter escolhe um horário livre; setas movem (esquerda e direita
// trocam o dia), Shift + setas para cima/baixo esticam, Enter confirma e Esc cancela.

const PASSO = 15; // minutos: precisão ao clicar, arrastar e esticar
const DURACAO_PADRAO = 60; // minutos: tamanho do bloco ao clicar
const DURACAO_MINIMA = 15; // minutos
const ULTIMO_MINUTO = 24 * 60 - 1; // um curso não passa de 23:59
const DIAS_ENUM = ['DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO'];
const SVG_NS = 'http://www.w3.org/2000/svg';

function hhmm(minutos) {
  const h = String(Math.floor(minutos / 60)).padStart(2, '0');
  const m = String(minutos % 60).padStart(2, '0');
  return `${h}:${m}`;
}

function limitar(valor, minimo, maximo) {
  return Math.min(Math.max(valor, minimo), maximo);
}

function arredondar(minutos) {
  return Math.round(minutos / PASSO) * PASSO;
}

function piso(minutos) {
  return Math.floor(minutos / PASSO) * PASSO;
}

function criar(tag, classe) {
  const el = document.createElement(tag);
  el.className = classe;
  return el;
}

function criarIcone(formas) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', '16');
  svg.setAttribute('height', '16');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '2.5');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  formas.forEach(([tag, atributos]) => {
    const forma = document.createElementNS(SVG_NS, tag);
    Object.entries(atributos).forEach(([nome, valor]) => forma.setAttribute(nome, valor));
    svg.appendChild(forma);
  });
  return svg;
}

function criarBotao(classe, rotulo, icone) {
  const botao = criar('button', `calendario__selecao-botao ${classe}`);
  botao.type = 'button';
  botao.setAttribute('aria-label', rotulo);
  botao.title = rotulo;
  botao.appendChild(icone);
  return botao;
}

// faixa: horas exibidas ({inicio, fim}); dias: os 7 dias da semana; colunas: o elemento de
// cada dia (mesma ordem); eventos: aulas da semana, com dia, iniMin e fimMin em minutos.
export function ativarSelecao(raiz, { faixa, dias, colunas, eventos, urlNovoCurso }) {
  const limiteIni = faixa.inicio * 60;
  const duracaoGrade = faixa.fim * 60 - limiteIni; // altura total da coluna, em minutos
  const limiteFim = Math.min(faixa.fim * 60, ULTIMO_MINUTO);

  // Intervalos já ocupados por outros cursos, por dia.
  const ocupados = dias.map((dia) =>
    eventos
      .filter((e) => e.dia === dia.indice)
      .map((e) => ({ ini: e.iniMin, fim: e.fimMin }))
      .sort((a, b) => a.ini - b.ini)
  );

  // Espaços livres de um dia, em ordem.
  function lacunas(dia) {
    const livres = [];
    let cursor = limiteIni;
    for (const o of ocupados[dia]) {
      if (o.ini > cursor) livres.push({ ini: cursor, fim: Math.min(o.ini, limiteFim) });
      cursor = Math.max(cursor, o.fim);
    }
    if (cursor < limiteFim) livres.push({ ini: cursor, fim: limiteFim });
    return livres.filter((l) => l.fim > l.ini);
  }

  // Início livre mais próximo do desejado para um bloco desse tamanho (ou null se o dia
  // não tem nenhum espaço que comporte o bloco).
  function melhorInicio(dia, desejado, duracao) {
    let melhor = null;
    for (const l of lacunas(dia)) {
      if (l.fim - l.ini < duracao) continue;
      const inicio = limitar(desejado, l.ini, l.fim - duracao);
      const distancia = Math.abs(inicio - desejado);
      if (melhor === null || distancia < melhor.distancia) melhor = { inicio, distancia };
    }
    return melhor === null ? null : melhor.inicio;
  }

  // ---- Elementos -------------------------------------------------------------------

  const dica = raiz.querySelector('[data-calendario-dica]');
  const aviso = raiz.querySelector('[data-calendario-aviso]');
  raiz.classList.add('calendario--selecionavel');
  if (dica) dica.hidden = false;

  const bloco = criar('div', 'calendario__selecao');
  bloco.tabIndex = 0;
  bloco.setAttribute('role', 'group');
  if (dica && dica.id) bloco.setAttribute('aria-describedby', dica.id);

  const alcaTopo = criar('span', 'calendario__selecao-alca calendario__selecao-alca--topo');
  alcaTopo.dataset.alca = 'topo';
  const alcaBase = criar('span', 'calendario__selecao-alca calendario__selecao-alca--base');
  alcaBase.dataset.alca = 'base';
  const texto = criar('span', 'calendario__selecao-texto');

  const botaoOk = criarBotao(
    'calendario__selecao-botao--ok',
    'Usar este horário e cadastrar o curso',
    criarIcone([['polyline', { points: '20 6 9 17 4 12' }]])
  );
  const botaoCancelar = criarBotao(
    'calendario__selecao-botao--cancelar',
    'Cancelar a escolha deste horário',
    criarIcone([
      ['line', { x1: '18', y1: '6', x2: '6', y2: '18' }],
      ['line', { x1: '6', y1: '6', x2: '18', y2: '18' }],
    ])
  );
  const acoes = criar('span', 'calendario__selecao-acoes');
  acoes.append(botaoOk, botaoCancelar);
  bloco.append(alcaTopo, alcaBase, texto, acoes);

  // ---- Estado ----------------------------------------------------------------------

  let selecao = null; // { dia, ini, fim } (minutos desde 00:00)
  let arrasto = null; // { modo: 'mover' | 'topo' | 'base', deslocamento, duracao }

  function descricao() {
    return `${dias[selecao.dia].nome}, ${hhmm(selecao.ini)} às ${hhmm(selecao.fim)}`;
  }

  function anunciar(mensagem) {
    if (aviso) aviso.textContent = mensagem;
  }

  function desenharBloco() {
    const coluna = colunas[selecao.dia];
    if (bloco.parentElement !== coluna) {
      const tinhaFoco = document.activeElement === bloco;
      coluna.appendChild(bloco);
      if (tinhaFoco) bloco.focus({ preventScroll: true });
    }
    bloco.style.setProperty('--sel-topo', String((selecao.ini - limiteIni) / 60));
    bloco.style.setProperty('--sel-altura', String((selecao.fim - selecao.ini) / 60));
    texto.textContent = `${hhmm(selecao.ini)} – ${hhmm(selecao.fim)}`;
    bloco.setAttribute('aria-label', `Horário escolhido: ${descricao()}`);
  }

  function aplicar(nova) {
    selecao = nova;
    desenharBloco();
  }

  function definir(nova, origem) {
    aplicar(nova);
    bloco.focus({ preventScroll: origem !== 'teclado' });
    anunciar(`Horário escolhido: ${descricao()}`);
  }

  function limpar() {
    if (!selecao) return;
    const coluna = colunas[selecao.dia];
    selecao = null;
    bloco.remove();
    coluna.focus({ preventScroll: true });
    anunciar('Escolha de horário cancelada.');
  }

  function confirmar() {
    if (!selecao) return;
    const url = new URL(urlNovoCurso, location.href);
    url.searchParams.set('dia', DIAS_ENUM[selecao.dia]);
    url.searchParams.set('inicio', hhmm(selecao.ini));
    url.searchParams.set('fim', hhmm(selecao.fim));
    location.assign(url);
  }

  // O espaço livre que contém o bloco atual (o bloco nunca sai dele).
  function lacunaDoBloco() {
    return lacunas(selecao.dia).find((l) => l.ini <= selecao.ini && selecao.fim <= l.fim) ?? null;
  }

  // ---- Posição do ponteiro ---------------------------------------------------------

  function minutoEm(coluna, clientY) {
    const r = coluna.getBoundingClientRect();
    return limiteIni + ((clientY - r.top) / r.height) * duracaoGrade;
  }

  function colunaEm(clientX) {
    let escolhida = 0;
    colunas.forEach((coluna, i) => {
      if (clientX >= coluna.getBoundingClientRect().left) escolhida = i;
    });
    return escolhida;
  }

  // ---- Clique num espaço livre: cria o bloco ---------------------------------------

  colunas.forEach((coluna, dia) => {
    coluna.tabIndex = 0;
    coluna.setAttribute('aria-label', `${dias[dia].nome}: pressione Enter para escolher um horário livre`);

    coluna.addEventListener('click', (ev) => {
      // Só o espaço vazio da coluna; cliques em cursos e no próprio bloco seguem seu caminho.
      if (ev.target !== coluna) return;
      const bruto = minutoEm(coluna, ev.clientY);
      const lacuna = lacunas(dia).find((l) => bruto >= l.ini && bruto < l.fim);
      if (!lacuna) return;
      const duracao = Math.min(DURACAO_PADRAO, lacuna.fim - lacuna.ini);
      if (duracao < DURACAO_MINIMA) return; // espaço pequeno demais para um curso
      const inicio = limitar(piso(bruto), lacuna.ini, lacuna.fim - duracao);
      definir({ dia, ini: inicio, fim: inicio + duracao }, 'mouse');
    });

    coluna.addEventListener('keydown', (ev) => {
      if (ev.target !== coluna || (ev.key !== 'Enter' && ev.key !== ' ')) return;
      ev.preventDefault();
      const livres = lacunas(dia).filter((l) => l.fim - l.ini >= DURACAO_MINIMA);
      if (livres.length === 0) {
        anunciar(`Não há horário livre na ${dias[dia].nome}.`);
        return;
      }
      const lacuna = livres.find((l) => l.fim - l.ini >= DURACAO_PADRAO) ?? livres[0];
      const duracao = Math.min(DURACAO_PADRAO, lacuna.fim - lacuna.ini);
      definir({ dia, ini: lacuna.ini, fim: lacuna.ini + duracao }, 'teclado');
    });
  });

  // ---- Arrastar e esticar com o mouse ou o dedo ------------------------------------

  function aoMover(ev) {
    if (!arrasto || !selecao) return;

    if (arrasto.modo === 'mover') {
      const dia = colunaEm(ev.clientX);
      const bruto = minutoEm(colunas[dia], ev.clientY) - arrasto.deslocamento;
      const inicio = melhorInicio(dia, arredondar(bruto), arrasto.duracao);
      if (inicio !== null) aplicar({ dia, ini: inicio, fim: inicio + arrasto.duracao });
      return;
    }

    const lacuna = lacunaDoBloco();
    if (!lacuna) return;
    const minuto = arredondar(minutoEm(colunas[selecao.dia], ev.clientY));
    if (arrasto.modo === 'base') {
      aplicar({ ...selecao, fim: limitar(minuto, selecao.ini + DURACAO_MINIMA, lacuna.fim) });
    } else {
      aplicar({ ...selecao, ini: limitar(minuto, lacuna.ini, selecao.fim - DURACAO_MINIMA) });
    }
  }

  // Ao soltar o arrasto o navegador ainda dispara um "click" (na coluna, se o ponteiro
  // sair de cima do bloco), que criaria outro bloco no lugar. Este bloqueio engole só esse
  // clique e sai de cena no próximo toque, se o clique não vier.
  function engolirCliqueDoArrasto() {
    const engolir = (ev) => ev.stopPropagation();
    document.addEventListener('click', engolir, { capture: true, once: true });
    document.addEventListener(
      'pointerdown',
      () => document.removeEventListener('click', engolir, true),
      { capture: true, once: true }
    );
  }

  function soltar() {
    if (!arrasto) return;
    arrasto = null;
    bloco.classList.remove('calendario__selecao--arrastando');
    document.removeEventListener('pointermove', aoMover);
    document.removeEventListener('pointerup', soltar);
    document.removeEventListener('pointercancel', soltar);
    engolirCliqueDoArrasto();
  }

  bloco.addEventListener('pointerdown', (ev) => {
    if (ev.button !== 0 || ev.target.closest('.calendario__selecao-botao')) return;
    const alca = ev.target.closest('[data-alca]');
    arrasto = {
      modo: alca ? alca.dataset.alca : 'mover',
      deslocamento: minutoEm(colunas[selecao.dia], ev.clientY) - selecao.ini,
      duracao: selecao.fim - selecao.ini,
    };
    bloco.classList.add('calendario__selecao--arrastando');
    bloco.focus({ preventScroll: true });
    ev.preventDefault();
    document.addEventListener('pointermove', aoMover);
    document.addEventListener('pointerup', soltar);
    document.addEventListener('pointercancel', soltar);
  });

  // ---- Teclado ---------------------------------------------------------------------

  function moverPorTeclado(direcao) {
    const duracao = selecao.fim - selecao.ini;
    let inicio = melhorInicio(selecao.dia, selecao.ini + direcao * PASSO, duracao);
    if (inicio === null || inicio === selecao.ini) {
      // Encostou em outro curso: pula para o próximo espaço livre que comporte o bloco.
      const livres = lacunas(selecao.dia).filter((l) => l.fim - l.ini >= duracao);
      if (direcao > 0) {
        const proxima = livres.find((l) => l.ini >= selecao.fim);
        inicio = proxima ? proxima.ini : selecao.ini;
      } else {
        const anterior = [...livres].reverse().find((l) => l.fim <= selecao.ini);
        inicio = anterior ? anterior.fim - duracao : selecao.ini;
      }
    }
    aplicar({ ...selecao, ini: inicio, fim: inicio + duracao });
  }

  function esticarPorTeclado(delta) {
    const lacuna = lacunaDoBloco();
    if (!lacuna) return;
    aplicar({ ...selecao, fim: limitar(selecao.fim + delta, selecao.ini + DURACAO_MINIMA, lacuna.fim) });
  }

  function trocarDia(delta) {
    const dia = selecao.dia + delta;
    if (dia < 0 || dia >= colunas.length) return;
    const duracao = selecao.fim - selecao.ini;
    const inicio = melhorInicio(dia, selecao.ini, duracao);
    if (inicio === null) {
      anunciar(`Sem espaço livre para este horário na ${dias[dia].nome}.`);
      return;
    }
    aplicar({ dia, ini: inicio, fim: inicio + duracao });
  }

  bloco.addEventListener('keydown', (ev) => {
    if (ev.target !== bloco) return; // nos botões, Enter e Espaço funcionam do jeito normal
    const antes = selecao ? descricao() : '';
    switch (ev.key) {
      case 'ArrowUp':
      case 'ArrowDown': {
        const direcao = ev.key === 'ArrowUp' ? -1 : 1;
        if (ev.shiftKey) esticarPorTeclado(direcao * PASSO);
        else moverPorTeclado(direcao);
        break;
      }
      case 'ArrowLeft':
        trocarDia(-1);
        break;
      case 'ArrowRight':
        trocarDia(1);
        break;
      case 'Enter':
        confirmar();
        break;
      case 'Escape':
        limpar();
        break;
      default:
        return;
    }
    ev.preventDefault();
    if (selecao && descricao() !== antes) {
      anunciar(descricao());
      bloco.scrollIntoView({ block: 'nearest' });
    }
  });

  botaoOk.addEventListener('click', confirmar);
  botaoCancelar.addEventListener('click', limpar);
}