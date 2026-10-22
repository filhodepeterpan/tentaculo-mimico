

function normalizar(texto) {
    return texto
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase();
}


function inicializarBusca() {
    const campoBusca = document.getElementById('admin-alunos-busca');
    const itens = document.querySelectorAll('.admin-alunos__item');
    const mensagemVazio = document.getElementById('admin-alunos-vazio');

    campoBusca?.addEventListener('input', () => {
        const termos = normalizar(campoBusca.value).trim().split(/\s+/).filter(Boolean);
        let algumVisivel = false;

        itens.forEach((item) => {
            const alvo = normalizar(item.dataset.busca ?? '');
            const corresponde = termos.length === 0 || termos.some((termo) => alvo.includes(termo));
            item.hidden = !corresponde;
            if (corresponde) algumVisivel = true;
        });

        mensagemVazio.hidden = algumVisivel;
    });
}

document.addEventListener('DOMContentLoaded', inicializarBusca);