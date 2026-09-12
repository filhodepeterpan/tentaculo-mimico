function inicializarAlertas() {
  document.querySelectorAll('.alerta__fechar').forEach((botao) => {
    botao.addEventListener('click', () => {
      botao.closest('.alerta').remove();
    });
  });
}

document.addEventListener('DOMContentLoaded', inicializarAlertas);

export { inicializarAlertas };
