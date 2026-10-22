import '../componentes/alert.js';
import '../componentes/imagem.js';

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

  // Data de início e de término precisam cair num dos dias marcados (0 = domingo).
  const valoresDias = ['DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO'];
  const marcados = Array.from(diasMarcados, (c) => c.value);
  for (const [id, qual] of [['data-inicio', 'início'], ['data-fim', 'término']]) {
    const campo = document.getElementById(id);
    if (!campo || !campo.value) continue;
    const diaDaData = valoresDias[new Date(`${campo.value}T00:00:00`).getDay()];
    if (!marcados.includes(diaDaData)) {
      evento.preventDefault();
      campo.setCustomValidity(`A data de ${qual} precisa cair em um dos dias da semana marcados`);
      campo.reportValidity();
      return;
    }
  }

  const botao = evento.target.querySelector('button[type="submit"]');
  if (botao) botao.disabled = true;
});

document.getElementById('hora-fim')?.addEventListener('input', (evento) => {
  evento.target.setCustomValidity('');
});

['data-inicio', 'data-fim'].forEach((id) => {
  document.getElementById(id)?.addEventListener('input', (evento) => {
    evento.target.setCustomValidity('');
  });
});

document.querySelectorAll('input[name="diasSemana"]').forEach((caixa) => {
  caixa.addEventListener('change', () => {
    ['data-inicio', 'data-fim'].forEach((id) => document.getElementById(id)?.setCustomValidity(''));
  });
});
