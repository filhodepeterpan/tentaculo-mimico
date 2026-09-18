import '../componentes/alert.js';

document.querySelector('.curso-form__formulario')?.addEventListener('submit', (evento) => {
  const diasMarcados = document.querySelectorAll('input[name="diasSemana"]:checked');
  const horaInicio = document.getElementById('hora-inicio');
  const horaFim = document.getElementById('hora-fim');
  const erroDiasSemana = document.getElementById('erro-dias-semana');

  if (diasMarcados.length === 0) {
    evento.preventDefault();
    erroDiasSemana.hidden = false;
    return;
  }
  erroDiasSemana.hidden = true;

  if (horaInicio.value && horaFim.value && horaFim.value <= horaInicio.value) {
    evento.preventDefault();
    horaFim.setCustomValidity('O horário de término precisa ser depois do início');
    horaFim.reportValidity();
    return;
  }

  const botao = evento.target.querySelector('button[type="submit"]');
  if (botao) botao.disabled = true;
});

document.getElementById('hora-fim')?.addEventListener('input', (evento) => {
  evento.target.setCustomValidity('');
});
