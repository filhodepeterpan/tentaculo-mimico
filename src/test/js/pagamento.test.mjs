import { readFile } from 'node:fs/promises';
import { createContext, SourceTextModule, SyntheticModule } from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const codigo = await readFile(new URL('../../main/resources/static/js/pages/pagamento.js', import.meta.url), 'utf8');

async function pagina(metodo) {
  const eventos = new Map();
  const elementos = new Map();
  const enviados = [];
  for (const id of ['painel-pix', 'painel-cartao', 'cartao-titulo', 'pix-confirmar', 'cartao-confirmar',
    'pix-copiar', 'campo-metodo', 'checkout-metodo', 'form-checkout-cartao', 'form-pagamento', 'pagamento']) {
    elementos.set(id, {
      disabled: false,
      addEventListener(nome, fn) { eventos.set(`${id}:${nome}`, fn); },
      setAttribute(nome, valor) { this[nome] = valor; },
      submit() {
        enviados.push({ id, creditoBloqueado: elementos.get('cartao-confirmar').disabled,
          pixBloqueado: elementos.get('pix-confirmar').disabled,
          ocupado: elementos.get('pagamento')['aria-busy'], metodo: elementos.get('checkout-metodo').value });
      },
    });
  }
  const contexto = createContext({
    document: {
      getElementById: id => elementos.get(id) ?? null,
      querySelector: seletor => seletor === '.pagamento' ? elementos.get('pagamento') : { value: metodo },
      querySelectorAll: () => [],
    },
    window: { addEventListener: (nome, fn) => eventos.set(`window:${nome}`, fn) },
  });
  const modulo = new SourceTextModule(codigo, { context: contexto });
  await modulo.link(async referencia => {
    const nomes = referencia.includes('qrcode') ? ['default'] : [
      'cvvValido', 'formatarNumeroCartao', 'formatarValidade', 'nomeImpressoValido', 'numeroCartaoValido', 'validadeValida',
    ];
    return new SyntheticModule(nomes, function () {
      for (const nome of nomes) this.setExport(nome, () => true);
    }, { context: contexto });
  });
  await modulo.evaluate();
  return { elementos, eventos, enviados };
}

for (const metodo of ['credito', 'debito']) {
  test(`${metodo}: bloqueia o botão antes de enviar e restaura ao voltar pelo histórico`, async () => {
    const { elementos, eventos, enviados } = await pagina(metodo);
    eventos.get('cartao-confirmar:click')();
    assert.deepEqual(enviados, [{ id: 'form-checkout-cartao', creditoBloqueado: true,
      pixBloqueado: true, ocupado: 'true', metodo }]);
    eventos.get('window:pageshow')({ persisted: true });
    assert.equal(elementos.get('cartao-confirmar').disabled, false);
    assert.equal(elementos.get('pagamento')['aria-busy'], 'false');
  });
}

test('Pix preserva o formulário existente', async () => {
  const { eventos, enviados } = await pagina('pix');
  eventos.get('pix-confirmar:click')();
  assert.equal(enviados[0].id, 'form-pagamento');
  assert.equal(enviados[0].pixBloqueado, true);
});
