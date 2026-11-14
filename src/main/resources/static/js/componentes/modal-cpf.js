// Máscara e validação de CPF para os formulários marcados com [data-completar-cpf]:
// o modal "Complete seu cadastro" (fragments/modal-cpf.html) e a página /cadastro/google.
// O campo de CPF é o <input> cujo name começa com "cpf" ("cpf-completar" no modal, "cpf" na página).

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

function inicializarFormulariosComCpf() {
  document.querySelectorAll('form[data-completar-cpf]').forEach((form) => {
    // O campo de CPF só existe se a conta ainda não tem CPF (no modal pode ser só a data).
    const campo = form.querySelector('input[name^="cpf"]');
    if (!campo) return;

    campo.addEventListener('input', (evento) => {
      evento.target.value = formatarCpf(evento.target.value);
      campo.setCustomValidity('');
    });

    // Reaplica a máscara no valor que o servidor devolveu depois de um erro.
    if (campo.value) campo.value = formatarCpf(campo.value);

    form.addEventListener('submit', (evento) => {
      if (!cpfValido(campo.value)) {
        evento.preventDefault();
        campo.setCustomValidity('CPF inválido');
        campo.reportValidity();
      }
    });
  });
}

// "Agora não", clique fora e ESC fecham o modal. Não depende do modal.js estar carregado na página.
function inicializarFechamentoDoModal() {
  const modal = document.getElementById('modal-completar-cpf');
  if (!modal) return;

  modal.querySelectorAll('[data-completar-fechar]').forEach((gatilho) => {
    gatilho.addEventListener('click', () => {
      modal.hidden = true;
    });
  });

  document.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') modal.hidden = true;
  });
}

function inicializar() {
  inicializarFormulariosComCpf();
  inicializarFechamentoDoModal();
}

// O script é um módulo (carrega depois do HTML), então o DOM já pode estar pronto.
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', inicializar);
} else {
  inicializar();
}

export { formatarCpf, cpfValido };
