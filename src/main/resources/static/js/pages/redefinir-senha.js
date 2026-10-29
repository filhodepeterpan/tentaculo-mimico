const formulario = document.getElementById("form-redefinir");

// Sem token a página mostra só o aviso de link inválido, não há formulário.
if (formulario) {
    const blocoSucesso = document.getElementById("redefinir-sucesso");
    const blocoErro = document.getElementById("redefinir-erro");
    const textoErro = blocoErro.querySelector(".alerta__mensagem");

    function mostrarErro(mensagem) {
        textoErro.textContent = mensagem;
        blocoSucesso.hidden = true;
        blocoErro.hidden = false;
    }

    // Botão "fechar" dos alertas
    [blocoSucesso, blocoErro].forEach((bloco) => {
        bloco.querySelector(".alerta__fechar")?.addEventListener("click", () => {
            bloco.hidden = true;
        });
    });

    formulario.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        blocoSucesso.hidden = true;
        blocoErro.hidden = true;

        const dados = new FormData(formulario);

        if (dados.get("novaSenha") !== dados.get("confirmacaoSenha")) {
            mostrarErro("As senhas não coincidem.");
            return;
        }

        const botao = formulario.querySelector("[type='submit']");
        botao.disabled = true;

        try {
            const resposta = await fetch(formulario.action, {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: new URLSearchParams(dados),
            });

            if (resposta.ok) {
                blocoSucesso.hidden = false;
                formulario.hidden = true;
                setTimeout(() => {
                    window.location.href = "/login";
                }, 2000);
            } else {
                // O back devolve a mensagem de erro no corpo (400)
                const mensagem = await resposta.text();
                mostrarErro(mensagem || "Não foi possível redefinir a senha.");
                botao.disabled = false;
            }
        } catch {
            mostrarErro("Falha de conexão. Tente novamente.");
            botao.disabled = false;
        }
    });
}
