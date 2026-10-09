// Abas, busca por nome e aceitar/recusar matrículas.
// Usado pelo perfil do professor (todos os cursos) e pela página do curso (só aquele curso).

export function normalizar(texto) {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

export function inicializarAbas(raiz = document) {
  const abas = raiz.querySelectorAll('[role="tab"]');
  const paineis = raiz.querySelectorAll('[role="tabpanel"]');

  abas.forEach((aba) => {
    aba.addEventListener('click', () => {
      abas.forEach((a) => a.setAttribute('aria-selected', 'false'));
      paineis.forEach((p) => { p.hidden = true; });

      aba.setAttribute('aria-selected', 'true');
      const painel = document.getElementById(aba.getAttribute('aria-controls'));
      if (painel) painel.hidden = false;
    });
  });
}

export function inicializarBusca(idCampo, idLista, idVazio) {
  const campoBusca = document.getElementById(idCampo);
  const lista = document.getElementById(idLista);
  const mensagemVazio = document.getElementById(idVazio);
  if (!campoBusca || !lista) return;

  campoBusca.addEventListener('input', () => {
    const termos = normalizar(campoBusca.value).trim().split(/\s+/).filter(Boolean);
    const itens = lista.querySelectorAll('.aluno-item');
    let algumVisivel = false;

    itens.forEach((item) => {
      const nomeNormalizado = normalizar(item.dataset.nome);
      const corresponde = termos.length === 0 || termos.every((termo) => nomeNormalizado.includes(termo));
      item.hidden = !corresponde;
      if (corresponde) algumVisivel = true;
    });

    if (mensagemVazio) mensagemVazio.hidden = algumVisivel;
  });
}

/**
 * Confirmação dos modais "modal-aceitar-<id>" e "modal-recusar-<id>": envia o formulário oculto
 * #form-acao-perfil para /matriculas/<id>/aceitar ou /recusar.
 * `voltar: 'curso'` pede ao servidor para voltar à página do curso em vez de ir ao perfil.
 */
export function inicializarRespostaMatriculas({ voltar } = {}) {
  document.addEventListener('click', (evento) => {
    const botao = evento.target.closest('[id$="-confirmar"]');
    if (!botao) return;

    const acao = ['aceitar', 'recusar'].find((a) => botao.id.startsWith(`modal-${a}-`));
    if (!acao) return;

    const form = document.getElementById('form-acao-perfil');
    if (!form) return;

    const prefixo = `modal-${acao}-`;
    const matriculaId = botao.id.slice(prefixo.length, -'-confirmar'.length);
    const consulta = voltar ? `?voltar=${encodeURIComponent(voltar)}` : '';
    form.action = `/matriculas/${encodeURIComponent(matriculaId)}/${acao}${consulta}`;
    form.submit();
  });
}
