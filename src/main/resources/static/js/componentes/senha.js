function alternarVisibilidadeSenha(botao) {
    const idInput = botao.getAttribute('aria-controls');
    const input = document.getElementById(idInput);
    if (!input) return;

    const visivel = botao.getAttribute('aria-pressed') === 'true';
    const novoEstado = !visivel;

    input.type = novoEstado ? 'text' : 'password';
    botao.setAttribute('aria-pressed', String(novoEstado));
    botao.setAttribute('aria-label', novoEstado ? 'Ocultar senha' : 'Mostrar senha');
}

function inicializarTogglesSenha() {
    document.querySelectorAll('.campo-senha__toggle').forEach((botao) => {
        botao.addEventListener('click', () => alternarVisibilidadeSenha(botao));
    });
}

document.addEventListener('DOMContentLoaded', inicializarTogglesSenha);

export { inicializarTogglesSenha };
