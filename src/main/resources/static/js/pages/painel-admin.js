let paginaAtual = 0;

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

function montarUrlHistorico() {
    const params = new URLSearchParams();
    params.set('pagina', paginaAtual);

    const tipo = document.getElementById('filtro-tipo').value;
    const usuario = document.getElementById('filtro-usuario').value;
    const de = document.getElementById('filtro-de').value;
    const ate = document.getElementById('filtro-ate').value;

    if (tipo) params.set('tipo', tipo);
    if (usuario) params.set('usuario', usuario);
    if (de) params.set('de', de);
    if (ate) params.set('ate', ate);

    return `/api/admin/logs?${params.toString()}`;
}

async function carregarHistorico() {
    const resposta = await fetch(montarUrlHistorico());

    if (resposta.status === 403) {
        window.location.href = '/acesso-negado';
        return;
    }

    if (!resposta.ok) {
        const erro = document.getElementById('painel-erro');
        erro.textContent = 'Não foi possível carregar o histórico. Tente novamente.';
        erro.hidden = false;
        return;
    }

    const dados = await resposta.json();
    const listaLogs = dados.content;

    const container = document.getElementById('historico-lista');
    const botaoMais = document.getElementById('historico-mais');
    const msgVazio = document.getElementById('historico-vazio');

    if (paginaAtual === 0) {
        container.innerHTML = '';
        msgVazio.hidden = true;
    }

    if (listaLogs.length === 0 && paginaAtual === 0) {
        msgVazio.hidden = false;
        botaoMais.hidden = true;
        return;
    }

    listaLogs.forEach(log => {
        const data = new Date(log.dataHora).toLocaleString('pt-BR');

        const div = document.createElement('div');
        div.className = 'historico-item';

        const linha1 = document.createElement('p');
        const strongAutor = document.createElement('strong');
        strongAutor.textContent = log.autor;
        linha1.appendChild(strongAutor);
        linha1.appendChild(document.createTextNode(` realizou: ${log.descricao}`));

        const linha2 = document.createElement('p');
        const smallData = document.createElement('small');
        smallData.textContent = `${data} - Alvo: ${log.alvoNome}`;
        linha2.appendChild(smallData);

        div.appendChild(linha1);
        div.appendChild(linha2);
        container.appendChild(div);
    });

    botaoMais.hidden = dados.last;
}

function aplicarFiltros() {
    paginaAtual = 0;
    carregarHistorico();
}

document.getElementById('historico-mais').addEventListener('click', () => {
    paginaAtual++;
    carregarHistorico();
});

document.getElementById('filtro-tipo').addEventListener('change', aplicarFiltros);
document.getElementById('filtro-usuario').addEventListener('change', aplicarFiltros);
document.getElementById('filtro-de').addEventListener('change', aplicarFiltros);
document.getElementById('filtro-ate').addEventListener('change', aplicarFiltros);

document.getElementById('filtro-limpar').addEventListener('click', () => {
    document.getElementById('filtro-tipo').value = '';
    document.getElementById('filtro-usuario').value = '';
    document.getElementById('filtro-de').value = '';
    document.getElementById('filtro-ate').value = '';
    aplicarFiltros();
});

carregarResumo();
carregarHistorico();