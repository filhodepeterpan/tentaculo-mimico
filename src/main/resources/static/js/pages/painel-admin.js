async function carregarResumo() {
    const resposta = await fetch('/api/admin/resumo');

    if (resposta.status === 403) {
        window.location.href = '/acesso-negado';
        return;
    }

    if (!resposta.ok) {
        const erro = document.getElementById('painel-erro');
        erro.textContent = 'Não foi possível carregar o painel. Tente novamente.';
        erro.hidden = false;
        return;
    }

    const dados = await resposta.json();
    document.getElementById('total-alunos').textContent = dados.alunos;
    document.getElementById('total-professores').textContent = dados.professores;
    document.getElementById('total-cursos').textContent = dados.cursosAtivos;
    document.getElementById('total-matriculas').textContent = dados.matriculas;
}

carregarResumo();