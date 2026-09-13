function normalizar(texto) {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

function inicializarBusca() {
  const campoBusca = document.getElementById('busca-cursos');
  const cards = document.querySelectorAll('.curso-card');
  const mensagemVazio = document.getElementById('cursos__vazio');

  campoBusca?.addEventListener('input', () => {
    const termos = normalizar(campoBusca.value).trim().split(/\s+/).filter(Boolean);
    let algumVisivel = false;

    cards.forEach((card) => {
      const nomeNormalizado = normalizar(card.dataset.nome);
      const corresponde = termos.length === 0 || termos.every((termo) => nomeNormalizado.includes(termo));
      card.hidden = !corresponde;
      if (corresponde) algumVisivel = true;
    });

    mensagemVazio.hidden = algumVisivel;
  });
}

document.addEventListener('DOMContentLoaded', inicializarBusca);
