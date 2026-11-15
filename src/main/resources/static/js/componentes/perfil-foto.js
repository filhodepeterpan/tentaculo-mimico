// Troca de foto direto no perfil: "Trocar foto" abre o seletor de arquivos e, ao escolher
// uma imagem, envia o formulário (POST /perfil/foto). A abertura/fechamento da foto ampliada
// é do modal.js (data-modal-abrir / data-modal-fechar / ESC).

const TAMANHO_MAXIMO = 10 * 1024 * 1024; // igual a spring.servlet.multipart.max-file-size

const botao = document.getElementById('botao-trocar-foto');
const entrada = document.getElementById('input-foto-perfil');
const formulario = document.getElementById('form-foto-perfil');
const mensagemErro = document.getElementById('foto-perfil-erro');

function mostrarErro(texto) {
  if (!mensagemErro) return;
  mensagemErro.textContent = texto;
  mensagemErro.hidden = !texto;
}

if (botao && entrada && formulario) {
  botao.addEventListener('click', () => {
    mostrarErro('');
    entrada.click();
  });

  entrada.addEventListener('change', () => {
    const arquivo = entrada.files && entrada.files[0];
    if (!arquivo) return;

    if (!arquivo.type.startsWith('image/')) {
      mostrarErro('Escolha um arquivo de imagem.');
      entrada.value = '';
      return;
    }
    if (arquivo.size > TAMANHO_MAXIMO) {
      mostrarErro('A imagem deve ter no máximo 10 MB.');
      entrada.value = '';
      return;
    }

    botao.disabled = true;
    botao.textContent = 'Enviando...';
    formulario.submit();
  });
}
