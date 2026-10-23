// Escolha de horário no calendário (só para professores).
//
// O professor clica num espaço livre e aparece um bloco de 1 hora. Dá para arrastar o
// bloco (muda dia e hora), puxar as bordas de cima e de baixo para esticar e confirmar
// no botão ✓, que abre o cadastro de curso já com dia e horário marcados.
// O bloco nunca fica por cima de outro curso: ao encostar, ele para na beirada ou
// pula para o espaço livre mais próximo.
//
// Só dá para escolher horários entre 07:00 e 22:00 e que ainda não passaram: as faixas
// listradas da grade são as que não aceitam bloco. O servidor confere tudo de novo ao salvar.
//
// Mais de um dia: Shift + clique em outro dia (no celular, tocar e segurar nele) ou
// Shift + Enter no bloco cria outro bloco ao lado. Como todos os dias de um curso têm o mesmo horário, os blocos andam juntos: mover
// ou esticar um deles muda o horário de todos (só a troca de dia é de um bloco por vez).
// Enter (ou ✓) confirma todos os dias; Esc (ou ✗) tira um dia por vez e, no último, cancela.
//
// Só os cursos da semana mostrada bloqueiam. A data do primeiro dia escolhido vai junto na
// URL (como data de início sugerida), mas a data de término é escolhida no formulário; a
// conferência completa de conflitos (que considera o período de cada curso) continua
// sendo feita pelo servidor ao salvar o cadastro.
//
// Teclado: Tab até o dia, Enter escolhe um horário livre; setas movem (esquerda e direita
// trocam o dia), Shift + setas para cima/baixo esticam, + aumenta 30 min (estica para baixo),
// - diminui 30 min (recolhe para cima), Shift + Enter adiciona o dia ao lado, Enter confirma
// e Esc cancela (um dia por vez).

const PASSO = 15; // minutos: precisão ao clicar, arrastar e esticar
const DURACAO_PADRAO = 60; // minutos: tamanho do bloco ao clicar
const DURACAO_MINIMA = 15; // minutos
const PASSO_MAIS_MENOS = 30; // minutos: quanto as teclas + e - esticam ou recolhem o bloco
const JANELA_INICIO = 7 * 60; // 07:00: nenhum curso começa antes
const JANELA_FIM = 22 * 60; // 22:00: nenhum curso termina depois
const FUSO = 'America/Sao_Paulo'; // o mesmo fuso do servidor, para o "agora" bater com a validação
const DIAS_ENUM = ['DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO'];
const TEMPO_SEGURAR = 500; // ms: quanto tempo o toque precisa durar para valer como "segurar"
const TOLERANCIA_TOQUE = 10; // px: se o dedo andar mais que isso, é rolagem ou arrasto, não "segurar"
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

// Data (yyyy-MM-dd) e minutos desde 00:00 de agora, no fuso do servidor.
function agoraNoFuso() {
  const partes = {};
  new Intl.DateTimeFormat('en-CA', {
    timeZone: FUSO,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
    .formatToParts(new Date())
    .forEach((p) => {
      partes[p.type] = p.value;
    });
  return {
    data: `${partes.year}-${partes.month}-${partes.day}`,
    minutos: Number(partes.hour) * 60 + Number(partes.minute),
  };
}

// "Terça", "Terça e Quinta", "Terça, Quarta e Quinta"
function listar(nomes) {
  if (nomes.length <= 1) return nomes.join('');
  return `${nomes.slice(0, -1).join(', ')} e ${nomes[nomes.length - 1]}`;
}

// Interseção de duas listas de intervalos {ini, fim}.
function intersecao(a, b) {
  const comuns = [];
  for (const x of a) {
    for (const y of b) {
      const ini = Math.max(x.ini, y.ini);
      const fim = Math.min(x.fim, y.fim);
      if (fim > ini) comuns.push({ ini, fim });
    }
  }
  return comuns.sort((p, q) => p.ini - q.ini);
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
  const origem = faixa.inicio * 60; // minuto em que a grade começa (topo das colunas)
  const fimGrade = faixa.fim * 60;
  const duracaoGrade = fimGrade - origem; // altura total da coluna, em minutos
  const permitidoIni = Math.max(origem, JANELA_INICIO); // onde um bloco pode começar
  const permitidoFim = Math.min(fimGrade, JANELA_FIM); // onde um bloco pode terminar

  // Intervalos já ocupados por cursos, por dia.
  const dosCursos = dias.map((dia) =>
    eventos
      .filter((e) => e.dia === dia.indice)
      .map((e) => ({ ini: e.iniMin, fim: e.fimMin }))
  );

  // O que já passou num dia: o dia inteiro, se for antes de hoje; se for hoje, até o próximo
  // múltiplo de PASSO depois de agora (assim o bloco nunca começa antes do horário atual).
  function passadoDoDia(dia) {
    const agora = agoraNoFuso();
    const data = dias[dia].data;
    if (data < agora.data) return { ini: 0, fim: 24 * 60 };
    if (data === agora.data) return { ini: 0, fim: (Math.floor(agora.minutos / PASSO) + 1) * PASSO };
    return null;
  }

  function ocupados(dia) {
    const lista = [...dosCursos[dia]];
    const passado = passadoDoDia(dia);
    if (passado) lista.push(passado);
    return lista.sort((a, b) => a.ini - b.ini);
  }

  // Espaços livres de um dia, em ordem (dentro da janela das 07:00 às 22:00).
  function lacunas(dia) {
    const livres = [];
    let cursor = permitidoIni;
    for (const o of ocupados(dia)) {
      if (o.ini > cursor) livres.push({ ini: cursor, fim: Math.min(o.ini, permitidoFim) });
      cursor = Math.max(cursor, o.fim);
    }
    if (cursor < permitidoFim) livres.push({ ini: cursor, fim: permitidoFim });
    return livres.filter((l) => l.fim > l.ini);
  }

  // Espaços livres ao mesmo tempo em todos os dias da lista.
  function lacunasComuns(listaDias) {
    return listaDias.map(lacunas).reduce((a, b) => intersecao(a, b));
  }

  // O horário cabe num espaço livre desse dia?
  function cabe(dia, ini, fim) {
    return lacunas(dia).some((l) => l.ini <= ini && fim <= l.fim);
  }

  // Início livre mais próximo do desejado para um bloco desse tamanho, valendo para todos os
  // dias da lista (ou null se não há nenhum espaço que comporte o bloco).
  function melhorInicio(listaDias, desejado, duracao) {
    let melhor = null;
    for (const l of lacunasComuns(listaDias)) {
      if (l.fim - l.ini < duracao) continue;
      const inicio = limitar(desejado, l.ini, l.fim - duracao);
      const distancia = Math.abs(inicio - desejado);
      if (melhor === null || distancia < melhor.distancia) melhor = { inicio, distancia };
    }
    return melhor === null ? null : melhor.inicio;
  }

  // ---- Faixas indisponíveis (já passaram ou fora das 07:00 às 22:00) ------------------

  function faixasIndisponiveis(dia) {
    const lista = [];
    if (permitidoIni > origem) lista.push({ ini: origem, fim: permitidoIni });
    if (permitidoFim < fimGrade) lista.push({ ini: permitidoFim, fim: fimGrade });
    const passado = passadoDoDia(dia);
    if (passado) lista.push({ ini: origem, fim: Math.min(passado.fim, fimGrade) });

    const unidas = [];
    for (const f of lista.sort((a, b) => a.ini - b.ini)) {
      if (f.fim <= f.ini) continue;
      const ultima = unidas[unidas.length - 1];
      if (ultima && f.ini <= ultima.fim) ultima.fim = Math.max(ultima.fim, f.fim);
      else unidas.push({ ...f });
    }
    return unidas;
  }

  // Ficam por baixo dos cursos e não recebem cliques (ver calendario.css).
  colunas.forEach((coluna, dia) => {
    faixasIndisponiveis(dia).forEach((f) => {
      const faixaEl = criar('div', 'calendario__indisponivel');
      faixaEl.setAttribute('aria-hidden', 'true');
      faixaEl.style.setProperty('--ind-topo', String((f.ini - origem) / 60));
      faixaEl.style.setProperty('--ind-altura', String((f.fim - f.ini) / 60));
      coluna.prepend(faixaEl);
    });
  });

  // ---- Elementos -------------------------------------------------------------------

  const dica = raiz.querySelector('[data-calendario-dica]');
  const aviso = raiz.querySelector('[data-calendario-aviso]');
  raiz.classList.add('calendario--selecionavel');
  if (dica) dica.hidden = false;

  // Um bloco por dia escolhido. Todos têm o mesmo horário (guardado em "horario").
  function criarBloco(dia) {
    const el = criar('div', 'calendario__selecao');
    el.tabIndex = 0;
    el.setAttribute('role', 'group');
    if (dica && dica.id) el.setAttribute('aria-describedby', dica.id);

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
      'Tirar este dia da escolha',
      criarIcone([
        ['line', { x1: '18', y1: '6', x2: '6', y2: '18' }],
        ['line', { x1: '6', y1: '6', x2: '18', y2: '18' }],
      ])
    );
    const acoes = criar('span', 'calendario__selecao-acoes');
    acoes.append(botaoOk, botaoCancelar);
    el.append(alcaTopo, alcaBase, texto, acoes);

    const bloco = { dia, el, texto };
    botaoOk.addEventListener('click', confirmar);
    botaoCancelar.addEventListener('click', () => removerBloco(bloco));
    el.addEventListener('pointerdown', (ev) => iniciarArrasto(ev, bloco));
    el.addEventListener('keydown', (ev) => aoTeclar(ev, bloco));
    return bloco;
  }

  // ---- Estado ----------------------------------------------------------------------

  let horario = null; // { ini, fim } (minutos desde 00:00), o mesmo para todos os dias
  let blocos = []; // um por dia escolhido, na ordem em que foram criados
  let arrasto = null; // { bloco, modo: 'mover' | 'topo' | 'base', deslocamento, duracao }
  let ignorarProximoClique = false; // depois de "tocar e segurar", o toque solto não cria outro bloco

  function diasEscolhidos() {
    return blocos.map((b) => b.dia);
  }

  function descricao() {
    const nomes = [...blocos].sort((a, b) => a.dia - b.dia).map((b) => dias[b.dia].nome);
    return `${listar(nomes)}, ${hhmm(horario.ini)} às ${hhmm(horario.fim)}`;
  }

  function anunciar(mensagem) {
    if (aviso) aviso.textContent = mensagem;
  }

  function desenharBlocos() {
    blocos.forEach((b) => {
      const coluna = colunas[b.dia];
      if (b.el.parentElement !== coluna) {
        const tinhaFoco = document.activeElement === b.el;
        coluna.appendChild(b.el);
        if (tinhaFoco) b.el.focus({ preventScroll: true });
      }
      b.el.style.setProperty('--sel-topo', String((horario.ini - origem) / 60));
      b.el.style.setProperty('--sel-altura', String((horario.fim - horario.ini) / 60));
      b.texto.textContent = `${hhmm(horario.ini)} – ${hhmm(horario.fim)}`;
      b.el.setAttribute(
        'aria-label',
        `Horário escolhido: ${dias[b.dia].nome}, ${hhmm(horario.ini)} às ${hhmm(horario.fim)}`
      );
    });
  }

  function aplicarHorario(novo) {
    horario = novo;
    desenharBlocos();
  }

  // Começa uma escolha nova (descarta os dias que já estavam escolhidos).
  function recomecar(dia, ini, fim, origemDoEvento) {
    blocos.forEach((b) => b.el.remove());
    blocos = [criarBloco(dia)];
    horario = { ini, fim };
    desenharBlocos();
    blocos[0].el.focus({ preventScroll: origemDoEvento !== 'teclado' });
    anunciar(`Horário escolhido: ${descricao()}`);
  }

  // Cancela a escolha inteira.
  function limpar() {
    if (!horario) return;
    const coluna = colunas[blocos[blocos.length - 1].dia];
    blocos.forEach((b) => b.el.remove());
    blocos = [];
    horario = null;
    coluna.focus({ preventScroll: true });
    anunciar('Escolha de horário cancelada.');
  }

  // Tira um dia da escolha; se era o último, cancela tudo.
  function removerBloco(bloco) {
    if (blocos.length <= 1) {
      limpar();
      return;
    }
    const indice = blocos.indexOf(bloco);
    blocos.splice(indice, 1);
    bloco.el.remove();
    blocos[Math.max(indice - 1, 0)].el.focus({ preventScroll: true });
    anunciar(`Dia removido. Horário escolhido: ${descricao()}`);
  }

  // Acrescenta um dia, sempre no mesmo horário dos que já estão escolhidos.
  function adicionarDia(novoDia, origemDoEvento) {
    if (!horario) return false;
    if (blocos.some((b) => b.dia === novoDia)) {
      anunciar(`${dias[novoDia].nome} já está escolhido.`);
      return false;
    }
    if (!cabe(novoDia, horario.ini, horario.fim)) {
      anunciar(`Sem espaço livre para este horário na ${dias[novoDia].nome}.`);
      return false;
    }
    const bloco = criarBloco(novoDia);
    blocos.push(bloco);
    desenharBlocos();
    bloco.el.focus({ preventScroll: origemDoEvento !== 'teclado' });
    anunciar(`Dia adicionado. Horário escolhido: ${descricao()}`);
    return true;
  }

  // Dia livre mais próximo, para os lados, onde o horário escolhido cabe: primeiro à direita
  // do bloco, depois à esquerda. Pula os dias já escolhidos.
  function diaVizinhoLivre(origemDia) {
    const usados = diasEscolhidos();
    for (const passo of [1, -1]) {
      for (let d = origemDia + passo; d >= 0 && d < colunas.length; d += passo) {
        if (usados.includes(d)) continue;
        if (cabe(d, horario.ini, horario.fim)) return d;
      }
    }
    return null;
  }

  function adicionarAoLado(bloco) {
    const vizinho = diaVizinhoLivre(bloco.dia);
    if (vizinho === null) {
      anunciar('Não há outro dia com este horário livre para adicionar.');
      return;
    }
    adicionarDia(vizinho, 'teclado');
  }

  function confirmar() {
    if (!horario) return;
    // O tempo passa com a página aberta: confere de novo se o horário ainda vale.
    const indisponivel = blocos.find((b) => !cabe(b.dia, horario.ini, horario.fim));
    if (indisponivel) {
      anunciar(`Este horário não está mais disponível na ${dias[indisponivel.dia].nome}. Escolha outro.`);
      return;
    }
    const ordenados = [...blocos].sort((a, b) => a.dia - b.dia);
    const url = new URL(urlNovoCurso, location.href);
    ordenados.forEach((b) => url.searchParams.append('dia', DIAS_ENUM[b.dia]));
    url.searchParams.set('data', dias[ordenados[0].dia].data); // yyyy-MM-dd do primeiro dia escolhido
    url.searchParams.set('inicio', hhmm(horario.ini));
    url.searchParams.set('fim', hhmm(horario.fim));
    location.assign(url);
  }

  // O espaço livre (comum a todos os dias escolhidos) que contém o horário atual: o bloco
  // nunca sai dele.
  function lacunaDoHorario() {
    return (
      lacunasComuns(diasEscolhidos()).find((l) => l.ini <= horario.ini && horario.fim <= l.fim) ?? null
    );
  }

  // ---- Posição do ponteiro ---------------------------------------------------------

  function minutoEm(coluna, clientY) {
    const r = coluna.getBoundingClientRect();
    return origem + ((clientY - r.top) / r.height) * duracaoGrade;
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

    // Shift + clique não deve selecionar texto da página.
    coluna.addEventListener('mousedown', (ev) => {
      if (ev.shiftKey) ev.preventDefault();
    });

    // Celular: tocar e segurar em outro dia faz o papel do Shift + clique (soma o dia, no
    // mesmo horário). Mexer o dedo cancela, para não atrapalhar a rolagem da página.
    let temporizador = null;
    let inicioToque = null;
    const cancelarSegurar = () => {
      clearTimeout(temporizador);
      temporizador = null;
      inicioToque = null;
    };

    coluna.addEventListener('pointerdown', (ev) => {
      ignorarProximoClique = false;
      if (ev.pointerType === 'mouse' || ev.target !== coluna || !horario) return;
      inicioToque = { x: ev.clientX, y: ev.clientY };
      temporizador = setTimeout(() => {
        temporizador = null;
        ignorarProximoClique = true;
        if (adicionarDia(dia, 'toque')) navigator.vibrate?.(30);
      }, TEMPO_SEGURAR);
    });
    coluna.addEventListener('pointermove', (ev) => {
      if (inicioToque && Math.hypot(ev.clientX - inicioToque.x, ev.clientY - inicioToque.y) > TOLERANCIA_TOQUE) {
        cancelarSegurar();
      }
    });
    ['pointerup', 'pointercancel', 'pointerleave'].forEach((nome) =>
      coluna.addEventListener(nome, cancelarSegurar)
    );
    // Segurar o dedo costuma abrir o menu do navegador; aqui ele atrapalharia.
    coluna.addEventListener('contextmenu', (ev) => {
      if (ignorarProximoClique) ev.preventDefault();
    });

    coluna.addEventListener('click', (ev) => {
      // Só o espaço vazio da coluna; cliques em cursos e no próprio bloco seguem seu caminho.
      if (ev.target !== coluna) return;
      if (ignorarProximoClique) {
        ignorarProximoClique = false;
        return;
      }

      // Shift + clique com um horário já escolhido: soma este dia, no mesmo horário.
      if (ev.shiftKey && horario) {
        adicionarDia(dia, 'mouse');
        return;
      }

      const bruto = minutoEm(coluna, ev.clientY);
      const lacuna = lacunas(dia).find((l) => bruto >= l.ini && bruto < l.fim);
      if (!lacuna) {
        anunciar('Este horário não está disponível: já passou ou fica fora do período das 07:00 às 22:00.');
        return;
      }
      const duracao = Math.min(DURACAO_PADRAO, lacuna.fim - lacuna.ini);
      if (duracao < DURACAO_MINIMA) {
        anunciar('Espaço pequeno demais para um curso.');
        return;
      }
      const inicio = limitar(piso(bruto), lacuna.ini, lacuna.fim - duracao);
      recomecar(dia, inicio, inicio + duracao, 'mouse');
    });

    coluna.addEventListener('keydown', (ev) => {
      if (ev.target !== coluna || (ev.key !== 'Enter' && ev.key !== ' ')) return;
      ev.preventDefault();

      // Shift + Enter numa coluna com um horário já escolhido: soma este dia.
      if (ev.shiftKey && horario) {
        adicionarDia(dia, 'teclado');
        return;
      }

      const livres = lacunas(dia).filter((l) => l.fim - l.ini >= DURACAO_MINIMA);
      if (livres.length === 0) {
        anunciar(`Não há horário livre na ${dias[dia].nome}.`);
        return;
      }
      const lacuna = livres.find((l) => l.fim - l.ini >= DURACAO_PADRAO) ?? livres[0];
      const duracao = Math.min(DURACAO_PADRAO, lacuna.fim - lacuna.ini);
      recomecar(dia, lacuna.ini, lacuna.ini + duracao, 'teclado');
    });
  });

  // ---- Arrastar e esticar com o mouse ou o dedo ------------------------------------

  function aoMover(ev) {
    if (!arrasto || !horario) return;
    const { bloco } = arrasto;

    if (arrasto.modo === 'mover') {
      const outros = blocos.filter((b) => b !== bloco).map((b) => b.dia);
      let dia = colunaEm(ev.clientX);
      // Com vários dias, o bloco não pode ir para um dia que já está escolhido.
      if (outros.includes(dia)) dia = bloco.dia;
      const bruto = minutoEm(colunas[dia], ev.clientY) - arrasto.deslocamento;
      const inicio = melhorInicio([...outros, dia], arredondar(bruto), arrasto.duracao);
      if (inicio !== null) {
        bloco.dia = dia;
        aplicarHorario({ ini: inicio, fim: inicio + arrasto.duracao });
      }
      return;
    }

    const lacuna = lacunaDoHorario();
    if (!lacuna) return;
    const minuto = arredondar(minutoEm(colunas[bloco.dia], ev.clientY));
    if (arrasto.modo === 'base') {
      aplicarHorario({ ...horario, fim: limitar(minuto, horario.ini + DURACAO_MINIMA, lacuna.fim) });
    } else {
      aplicarHorario({ ...horario, ini: limitar(minuto, lacuna.ini, horario.fim - DURACAO_MINIMA) });
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
    arrasto.bloco.el.classList.remove('calendario__selecao--arrastando');
    arrasto = null;
    document.removeEventListener('pointermove', aoMover);
    document.removeEventListener('pointerup', soltar);
    document.removeEventListener('pointercancel', soltar);
    engolirCliqueDoArrasto();
  }

  function iniciarArrasto(ev, bloco) {
    if (ev.button !== 0 || ev.target.closest('.calendario__selecao-botao')) return;
    const alca = ev.target.closest('[data-alca]');
    arrasto = {
      bloco,
      modo: alca ? alca.dataset.alca : 'mover',
      deslocamento: minutoEm(colunas[bloco.dia], ev.clientY) - horario.ini,
      duracao: horario.fim - horario.ini,
    };
    bloco.el.classList.add('calendario__selecao--arrastando');
    bloco.el.focus({ preventScroll: true });
    ev.preventDefault();
    document.addEventListener('pointermove', aoMover);
    document.addEventListener('pointerup', soltar);
    document.addEventListener('pointercancel', soltar);
  }

  // ---- Teclado ---------------------------------------------------------------------

  function moverPorTeclado(direcao) {
    const listaDias = diasEscolhidos();
    const duracao = horario.fim - horario.ini;
    let inicio = melhorInicio(listaDias, horario.ini + direcao * PASSO, duracao);
    if (inicio === null || inicio === horario.ini) {
      // Encostou em outro curso: pula para o próximo espaço livre que comporte o bloco.
      const livres = lacunasComuns(listaDias).filter((l) => l.fim - l.ini >= duracao);
      if (direcao > 0) {
        const proxima = livres.find((l) => l.ini >= horario.fim);
        inicio = proxima ? proxima.ini : horario.ini;
      } else {
        const anterior = [...livres].reverse().find((l) => l.fim <= horario.ini);
        inicio = anterior ? anterior.fim - duracao : horario.ini;
      }
    }
    aplicarHorario({ ini: inicio, fim: inicio + duracao });
  }

  function esticarPorTeclado(delta) {
    const lacuna = lacunaDoHorario();
    if (!lacuna) return;
    aplicarHorario({
      ...horario,
      fim: limitar(horario.fim + delta, horario.ini + DURACAO_MINIMA, lacuna.fim),
    });
  }

  // Troca o dia de um bloco (os outros ficam onde estão).
  function trocarDia(bloco, delta) {
    const outros = blocos.filter((b) => b !== bloco).map((b) => b.dia);

    if (outros.length === 0) {
      // Um dia só: o bloco pode deslizar para o horário livre mais próximo no dia novo.
      const dia = bloco.dia + delta;
      if (dia < 0 || dia >= colunas.length) return;
      const duracao = horario.fim - horario.ini;
      const inicio = melhorInicio([dia], horario.ini, duracao);
      if (inicio === null) {
        anunciar(`Sem espaço livre para este horário na ${dias[dia].nome}.`);
        return;
      }
      bloco.dia = dia;
      aplicarHorario({ ini: inicio, fim: inicio + duracao });
      return;
    }

    // Vários dias: o horário é o mesmo em todos, então o bloco só vai para um dia (não
    // escolhido) onde esse horário cabe.
    for (let d = bloco.dia + delta; d >= 0 && d < colunas.length; d += delta) {
      if (outros.includes(d)) continue;
      if (cabe(d, horario.ini, horario.fim)) {
        bloco.dia = d;
        desenharBlocos();
        return;
      }
    }
    anunciar(`Sem espaço livre para este horário à ${delta > 0 ? 'direita' : 'esquerda'}.`);
  }

  function aoTeclar(ev, bloco) {
    if (ev.target !== bloco.el) return; // nos botões, Enter e Espaço funcionam do jeito normal
    const antes = horario ? descricao() : '';
    switch (ev.key) {
      case 'ArrowUp':
      case 'ArrowDown': {
        const direcao = ev.key === 'ArrowUp' ? -1 : 1;
        if (ev.shiftKey) esticarPorTeclado(direcao * PASSO);
        else moverPorTeclado(direcao);
        break;
      }
      case '+':
      case '=': // o "+" sem Shift em muitos teclados é a tecla "="
        esticarPorTeclado(PASSO_MAIS_MENOS);
        break;
      case '-':
        esticarPorTeclado(-PASSO_MAIS_MENOS);
        break;
      case 'ArrowLeft':
        trocarDia(bloco, -1);
        break;
      case 'ArrowRight':
        trocarDia(bloco, 1);
        break;
      case 'Enter':
        if (ev.shiftKey) adicionarAoLado(bloco);
        else confirmar();
        break;
      case 'Escape':
        removerBloco(bloco);
        break;
      default:
        return;
    }
    ev.preventDefault();
    if (horario && descricao() !== antes) {
      anunciar(descricao());
      if (bloco.el.isConnected) bloco.el.scrollIntoView({ block: 'nearest' });
    }
  }
}