function formatarCpf(valor) {
  const digitos = valor.replace(/\D/g, '').slice(0, 11);
  return digitos
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
}

function cpfValido(cpf) {
  const digitos = cpf.replace(/\D/g, '');
  if (digitos.length !== 11 || /^(\d)\1{10}$/.test(digitos)) return false;

  let soma = 0;
  for (let i = 0; i < 9; i++) soma += parseInt(digitos[i], 10) * (10 - i);
  let resto = (soma * 10) % 11;
  if (resto === 10) resto = 0;
  if (resto !== parseInt(digitos[9], 10)) return false;

  soma = 0;
  for (let i = 0; i < 10; i++) soma += parseInt(digitos[i], 10) * (11 - i);
  resto = (soma * 10) % 11;
  if (resto === 10) resto = 0;
  if (resto !== parseInt(digitos[10], 10)) return false;

  return true;
}

function inicializarModalCpf() {
  const campo = document.getElementById('cpf-completar');
  const form = document.getElementById('form-completar-cpf');
  if (!campo || !form) return;

  campo.addEventListener('input', (evento) => {
    evento.target.value = formatarCpf(evento.target.value);
    campo.setCustomValidity('');
  });

  form.addEventListener('submit', (evento) => {
    if (!cpfValido(campo.value)) {
      evento.preventDefault();
      campo.setCustomValidity('CPF inválido');
      campo.reportValidity();
    }
  });
}

document.addEventListener('DOMContentLoaded', inicializarModalCpf);
