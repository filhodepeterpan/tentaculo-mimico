const formulario = document.getElementById("form-recuperar");
const blocoSucesso = document.getElementById("recuperar-sucesso");
const blocoErro = document.getElementById("recuperar-erro");

function esconderFeedback() {
    blocoSucesso.hidden = true;
    blocoErro.hidden = true;
}

// Botão "fechar" dos alertas
document.querySelectorAll(".recuperar .alerta__fechar").forEach((botao) => {
    botao.addEventListener("click", () => {
        botao.closest("[hidden], #recuperar-sucesso, #recuperar-erro").hidden = true;
    });
});

formulario.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    esconderFeedback();

    const botao = formulario.querySelector("[type='submit']");
    botao.disabled = true;

    try {
        const resposta = await fetch(formulario.action, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams(new FormData(formulario)),
        });

        if (resposta.ok) {
            blocoSucesso.hidden = false;
            formulario.reset();
        } else {
            blocoErro.hidden = false;
        }
    } catch {
        blocoErro.hidden = false;
    } finally {
        botao.disabled = false;
    }
});
