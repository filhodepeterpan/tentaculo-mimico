// Calendário da semana (domingo a sábado). Lê GET /api/calendario/semana e desenha a
// grade: uma coluna por dia, uma linha por hora, e cada aula posicionada no horário
// que ocupa. Tudo é montado com createElement/textContent (nada de innerHTML), porque
// o nome do curso vem do banco.

const HORAS_MINIMAS_VISIVEIS = 6;
const FAIXA_PADRAO = { inicio: 8, fim: 18 }; // semana sem aulas
const FAIXA_PROFESSOR = { inicio: 7, fim: 22 }; // professor vê ao menos isso, para poder escolher horários livres

function emMinutos(hhmm) {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
}

function rotuloHora(hora) {
  return String(hora).padStart(2, '0') + ':00';
}

function formatarDataCurta(iso) {
  const [, mes, dia] = iso.split('-');
  return `${dia}/${mes}`;
}

function criar(tag, classe, texto) {
  const el = document.createElement(tag);
  if (classe) el.className = classe;
  if (texto !== undefined) el.textContent = texto;
  return el;
}

// Faixa de horas exibida: da hora cheia da primeira aula até a hora cheia seguinte
// ao fim da última, com um mínimo de linhas para a grade não ficar achatada.
// Para o professor (ampliar) a faixa nunca é menor que FAIXA_PROFESSOR.
function calcularFaixa(eventos, ampliar) {
  let inicio = FAIXA_PADRAO.inicio;
  let fim = FAIXA_PADRAO.fim;

  if (eventos.length > 0) {
    const primeiroInicio = Math.min(...eventos.map((e) => emMinutos(e.inicio)));
    const ultimoFim = Math.max(...eventos.map((e) => emMinutos(e.fim)));

    inicio = Math.floor(primeiroInicio / 60);
    fim = Math.ceil(ultimoFim / 60);

    while (fim - inicio < HORAS_MINIMAS_VISIVEIS) {
      if (fim < 24) fim += 1;
      else if (inicio > 0) inicio -= 1;
      else break;
    }
  }

  if (ampliar) {
    inicio = Math.min(inicio, FAIXA_PROFESSOR.inicio);
    fim = Math.max(fim, FAIXA_PROFESSOR.fim);
  }
  return { inicio, fim };
}

// Aulas que se cruzam no mesmo dia dividem a largura da coluna.
// Preenche e.col (posição) e e.cols (quantas colunas o grupo tem).
function distribuirColunas(eventosDoDia) {
  const ordenados = [...eventosDoDia].sort((a, b) => a.iniMin - b.iniMin || a.fimMin - b.fimMin);
  let grupo = [];
  let fimDoGrupo = -1;
  let fimPorColuna = [];

  const fecharGrupo = () => {
    grupo.forEach((e) => {
      e.cols = fimPorColuna.length;
    });
    grupo = [];
    fimPorColuna = [];
  };

  for (const e of ordenados) {
    if (grupo.length > 0 && e.iniMin >= fimDoGrupo) fecharGrupo();

    let coluna = fimPorColuna.findIndex((fim) => fim <= e.iniMin);
    if (coluna === -1) {
      coluna = fimPorColuna.length;
      fimPorColuna.push(e.fimMin);
    } else {
      fimPorColuna[coluna] = e.fimMin;
    }
    e.col = coluna;
    grupo.push(e);
    fimDoGrupo = Math.max(fimDoGrupo, e.fimMin);
  }
  fecharGrupo();
}

function criarEvento(e, faixa, dia, urlCurso, imgPadrao) {
  const link = criar('a', 'calendario__evento');
  link.href = urlCurso + encodeURIComponent(e.cursoId);
  link.setAttribute(
    'aria-label',
    `${e.nome}, ${dia.nome}, ${e.inicio} às ${e.fim}`
  );
  // O horário saiu do bloco, mas continua disponível ao passar o mouse.
  link.title = `${e.nome} — ${e.inicio} às ${e.fim}`;
  link.style.setProperty('--ev-topo', String((e.iniMin - faixa.inicio * 60) / 60));
  link.style.setProperty('--ev-altura', String((e.fimMin - e.iniMin) / 60));
  link.style.setProperty('--ev-col', String(e.col));
  link.style.setProperty('--ev-cols', String(e.cols));

  const imagem = criar('img', 'calendario__evento-imagem');
  imagem.alt = '';
  imagem.loading = 'lazy';
  imagem.src = e.imagemCapa || imgPadrao;
  imagem.addEventListener('error', () => {
    if (imagem.src !== new URL(imgPadrao, location.href).href) imagem.src = imgPadrao;
  });
  link.appendChild(imagem);

  const texto = criar('span', 'calendario__evento-texto');
  texto.appendChild(criar('span', 'calendario__evento-nome', e.nome));
  link.appendChild(texto);

  return link;
}

function desenhar(raiz, semana, deslocamento) {
  const grade = raiz.querySelector('[data-calendario-grade]');
  const rolagem = raiz.querySelector('[data-calendario-rolagem]');
  const mensagem = raiz.querySelector('[data-calendario-mensagem]');
  const periodo = raiz.querySelector('[data-calendario-periodo]');
  const urlCurso = raiz.dataset.urlCurso;
  const imgPadrao = raiz.dataset.imgPadrao;
  const ehProfessor = raiz.dataset.professor === 'true';

  const { dias } = semana;
  periodo.textContent = `${formatarDataCurta(dias[0].data)} a ${formatarDataCurta(dias[6].data)}`;

  const eventos = semana.eventos.map((e) => ({
    ...e,
    iniMin: emMinutos(e.inicio),
    fimMin: emMinutos(e.fim),
  }));
  const faixa = calcularFaixa(eventos, ehProfessor);
  const totalHoras = faixa.fim - faixa.inicio;

  grade.replaceChildren();
  grade.style.setProperty('--cal-horas', String(totalHoras));

  // Linha 1: cabeçalho (canto vazio + 7 dias)
  grade.appendChild(criar('div', 'calendario__canto'));
  dias.forEach((dia) => {
    const cab = criar('div', 'calendario__dia' + (dia.hoje ? ' calendario__dia--hoje' : ''));
    cab.appendChild(criar('span', 'calendario__dia-nome', dia.nome));
    cab.appendChild(criar('span', 'calendario__dia-numero', String(dia.diaMes)));
    if (dia.hoje) cab.setAttribute('aria-current', 'date');
    grade.appendChild(cab);
  });

  // Linha 2: horas + 7 colunas
  const horas = criar('div', 'calendario__horas');
  horas.setAttribute('aria-hidden', 'true');
  for (let i = 0; i < totalHoras; i++) {
    const rotulo = criar(
      'span',
      'calendario__hora' + (i === 0 ? ' calendario__hora--primeira' : ''),
      rotuloHora(faixa.inicio + i)
    );
    rotulo.style.setProperty('--hora-indice', String(i));
    horas.appendChild(rotulo);
  }
  grade.appendChild(horas);

  const colunas = [];
  dias.forEach((dia) => {
    const coluna = criar('div', 'calendario__coluna' + (dia.hoje ? ' calendario__coluna--hoje' : ''));
    coluna.setAttribute('role', 'group');
    coluna.setAttribute('aria-label', dia.nome);

    const doDia = eventos.filter((e) => e.dia === dia.indice);
    distribuirColunas(doDia);
    doDia.forEach((e) => coluna.appendChild(criarEvento(e, faixa, dia, urlCurso, imgPadrao)));

    grade.appendChild(coluna);
    colunas.push(coluna);
  });

  rolagem.hidden = false;
  if (eventos.length === 0) {
    mensagem.textContent =
      deslocamento === 0 ? 'Nenhum curso acontece nesta semana.' : 'Nenhum curso acontece na próxima semana.';
    mensagem.classList.remove('calendario__mensagem--erro');
    mensagem.hidden = false;
  } else {
    mensagem.hidden = true;
  }

  return { faixa, dias, colunas, eventos };
}

// Só professores escolhem horário no calendário: o código disso fica num módulo à parte,
// carregado apenas para eles. Se falhar, o calendário continua funcionando só para consulta.
async function liberarSelecao(raiz, contexto) {
  try {
    const { ativarSelecao } = await import('./calendario-selecao.js');
    ativarSelecao(raiz, { ...contexto, urlNovoCurso: raiz.dataset.urlNovoCurso });
  } catch (erro) {
    console.error('Calendário: não foi possível ativar a escolha de horário.', erro);
  }
}

// Última requisição de cada calendário: se o usuário trocar de semana enquanto uma resposta
// ainda está a caminho, a resposta antiga é ignorada.
const requisicoes = new WeakMap();

// "Semana atual" (esquerda) só aparece na próxima semana; "Próxima semana" (direita) fica
// desabilitado quando a próxima semana já está na tela.
function atualizarNavegacao(raiz, deslocamento) {
  const atual = raiz.querySelector('[data-calendario-semana-atual]');
  const proxima = raiz.querySelector('[data-calendario-proxima-semana]');
  if (atual) atual.hidden = deslocamento === 0;
  if (proxima) proxima.disabled = deslocamento !== 0;
}

// deslocamento: 0 = semana atual, 1 = próxima semana.
async function carregar(raiz, deslocamento, moverFoco = false) {
  const mensagem = raiz.querySelector('[data-calendario-mensagem]');
  const aviso = raiz.querySelector('[data-calendario-aviso]');
  const periodo = raiz.querySelector('[data-calendario-periodo]');
  const numero = (requisicoes.get(raiz) ?? 0) + 1;
  requisicoes.set(raiz, numero);

  try {
    const url = new URL(raiz.dataset.url, location.href);
    url.searchParams.set('deslocamento', String(deslocamento));
    const resposta = await fetch(url, { headers: { Accept: 'application/json' } });
    if (!resposta.ok) throw new Error(`HTTP ${resposta.status}`);
    const semana = await resposta.json();
    if (requisicoes.get(raiz) !== numero) return;

    const contexto = desenhar(raiz, semana, deslocamento);
    atualizarNavegacao(raiz, deslocamento);
    if (moverFoco) {
      // O botão clicado some ou fica desabilitado: o foco vai para o botão que o substitui.
      const seletor = deslocamento === 0 ? '[data-calendario-proxima-semana]' : '[data-calendario-semana-atual]';
      raiz.querySelector(seletor)?.focus();
    }
    if (aviso) {
      aviso.textContent = `Mostrando ${deslocamento === 0 ? 'a semana atual' : 'a próxima semana'}: ${periodo.textContent}.`;
    }
    if (raiz.dataset.professor === 'true') liberarSelecao(raiz, contexto);
  } catch (erro) {
    console.error('Calendário: não foi possível carregar a semana.', erro);
    mensagem.textContent = 'Não foi possível carregar a agenda da semana. Tente novamente mais tarde.';
    mensagem.classList.add('calendario__mensagem--erro');
    mensagem.hidden = false;
  }
}

function iniciar(raiz) {
  if (raiz.dataset.calendarioIniciado) return;
  raiz.dataset.calendarioIniciado = 'true';

  raiz
    .querySelector('[data-calendario-proxima-semana]')
    ?.addEventListener('click', () => carregar(raiz, 1, true));
  raiz
    .querySelector('[data-calendario-semana-atual]')
    ?.addEventListener('click', () => carregar(raiz, 0, true));

  carregar(raiz, 0);
}

function iniciarTodos() {
  document.querySelectorAll('[data-calendario]').forEach(iniciar);
}

// Como módulo, o script já roda depois do HTML pronto; o teste cobre os dois casos.
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', iniciarTodos);
} else {
  iniciarTodos();
}