import '../componentes/modal.js';

// Excluir conta: confirma no modal e envia o form oculto (POST /perfil-excluir).
// Se a conta não puder ser excluída, o servidor volta pra cá com um modal explicando o motivo.
document.getElementById('modal-excluir-conta-confirmar')?.addEventListener('click', () => {
  const form = document.getElementById('form-acao-perfil');
  if (!form) return;
  form.action = '/perfil-excluir';
  form.submit();
});
