/**
 * navegacao-teclado.js
 * Navegação pelo teclado: setas movem o foco entre os elementos
 * interativos da página e Enter ativa o elemento focado.
 */
(function () {
    'use strict';

    var SELETOR_FOCAVEIS = [
        'a[href]',
        'button:not([disabled])',
        'input:not([disabled]):not([type="hidden"])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        'summary',
        '[tabindex]:not([tabindex="-1"])'
    ].join(',');

    // Campos em que as setas têm função própria (mover cursor, trocar opção...)
    var TIPOS_DE_TEXTO = ['text', 'email', 'password', 'search', 'tel', 'url', 'number', 'date', 'time'];

    function estaVisivel(el) {
        if (el.closest('[hidden], [aria-hidden="true"]') && el.tagName === 'INPUT') return false;
        var estilo = window.getComputedStyle(el);
        if (estilo.visibility === 'hidden' || estilo.display === 'none') return false;
        var r = el.getBoundingClientRect();
        return r.width > 0 && r.height > 0;
    }

    function eCampoDeEdicao(el) {
        if (!el) return false;
        if (el.isContentEditable) return true;
        var tag = el.tagName;
        if (tag === 'TEXTAREA' || tag === 'SELECT') return true;
        if (tag === 'INPUT') {
            var tipo = (el.getAttribute('type') || 'text').toLowerCase();
            return TIPOS_DE_TEXTO.indexOf(tipo) !== -1;
        }
        return false;
    }

    function obterRaiz() {
        var modal = document.querySelector('dialog[open], [role="dialog"]:not([hidden]), [aria-modal="true"]:not([hidden])');
        return modal && estaVisivel(modal) ? modal : document;
    }

    function listarFocaveis() {
        return Array.prototype.filter.call(
            obterRaiz().querySelectorAll(SELETOR_FOCAVEIS),
            estaVisivel
        );
    }

    function centro(el) {
        var r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    }

    // Procura o elemento mais próximo na direção da seta pressionada
    function encontrarVizinho(atual, candidatos, direcao) {
        var c0 = centro(atual);
        var melhor = null;
        var melhorPontuacao = Infinity;

        candidatos.forEach(function (el) {
            if (el === atual) return;
            var c = centro(el);
            var dx = c.x - c0.x;
            var dy = c.y - c0.y;
            var principal, lateral;

            switch (direcao) {
                case 'ArrowDown':  if (dy <= 1)  return; principal = dy;  lateral = Math.abs(dx); break;
                case 'ArrowUp':    if (dy >= -1) return; principal = -dy; lateral = Math.abs(dx); break;
                case 'ArrowRight': if (dx <= 1)  return; principal = dx;  lateral = Math.abs(dy); break;
                case 'ArrowLeft':  if (dx >= -1) return; principal = -dx; lateral = Math.abs(dy); break;
                default: return;
            }

            var pontuacao = principal + lateral * 2;
            if (pontuacao < melhorPontuacao) {
                melhorPontuacao = pontuacao;
                melhor = el;
            }
        });

        return melhor;
    }

    document.addEventListener('keydown', function (e) {
        if (e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;

        var ativo = document.activeElement;
        var emBody = !ativo || ativo === document.body;

        // ENTER: links e botões já funcionam nativamente; aqui cobrimos o resto
        if (e.key === 'Enter') {
            if (emBody || eCampoDeEdicao(ativo)) return;
            var tag = ativo.tagName;
            if (tag === 'A' || tag === 'BUTTON' || tag === 'SUMMARY' || tag === 'TEXTAREA') return;
            if (tag === 'INPUT') {
                var tipo = (ativo.getAttribute('type') || '').toLowerCase();
                if (tipo === 'checkbox' || tipo === 'radio') {
                    e.preventDefault();
                    ativo.click();
                }
                return;
            }
            e.preventDefault();
            ativo.click();
            return;
        }

        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].indexOf(e.key) === -1) return;

        // Não atrapalha digitação/seleção em campos de formulário
        if (eCampoDeEdicao(ativo)) return;
        // Não atrapalha grupos de rádio (as setas trocam a opção)
        if (ativo && ativo.tagName === 'INPUT' && ativo.type === 'radio') return;

        var focaveis = listarFocaveis();
        if (!focaveis.length) return;

        e.preventDefault();

        // Nada focado ainda: foca o primeiro elemento
        if (emBody || focaveis.indexOf(ativo) === -1) {
            focaveis[0].focus();
            return;
        }

        var proximo = encontrarVizinho(ativo, focaveis, e.key);
        if (proximo) proximo.focus();
    });
})();
