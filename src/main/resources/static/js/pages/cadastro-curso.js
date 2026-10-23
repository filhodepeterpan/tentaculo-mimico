import '../componentes/alert.js';
import '../componentes/imagem.js';

// Data (yyyy-MM-dd) e hora (HH:mm) de agora no fuso do servidor.
function agoraNoFuso() {
  const partes = {};
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
    .formatToParts(new Date())
    .forEach((p) => {
      partes[p.type] = p.value;
    });
  return { data: `${partes.year}-${partes.month}-${partes.day}`, hora: `${partes.hour}:${partes.minute}` };
}

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

  // Os cursos só acontecem das 07:00 às 22:00 (o servidor confere de novo).
  if (horaInicio.value && horaInicio.value < '07:00') {
    evento.preventDefault();
    horaInicio.setCustomValidity('O curso não pode começar antes das 07:00');
    horaInicio.reportValidity();
    return;
  }
  if (horaFim.value && horaFim.value > '22:00') {
    evento.preventDefault();
    horaFim.setCustomValidity('O curso não pode terminar depois das 22:00');
    horaFim.reportValidity();
    return;
  }

  // Curso novo: a primeira aula não pode estar no passado. Na edição quem decide é o
  // servidor, porque só vale para quem muda a data de início, os dias ou o horário.
  const editando = (evento.target.getAttribute('action') || '').endsWith('/editar');
  const dataInicio = document.getElementById('data-inicio');
  if (!editando && dataInicio && dataInicio.value && horaInicio.value) {
    const agora = agoraNoFuso();
    const hora = horaInicio.value.slice(0, 5);
    if (dataInicio.value < agora.data || (dataInicio.value === agora.data && hora <= agora.hora)) {
      evento.preventDefault();
      dataInicio.setCustomValidity('A primeira aula já passou: escolha um dia e um horário a partir de agora');
      dataInicio.reportValidity();
      return;
    }
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

// Mudar o horário de início também limpa o aviso de "primeira aula no passado" da data.
document.getElementById('hora-inicio')?.addEventListener('input', (evento) => {
  evento.target.setCustomValidity('');
  document.getElementById('data-inicio')?.setCustomValidity('');
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
