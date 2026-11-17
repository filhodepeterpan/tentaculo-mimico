// Menu do usuário no header (<details data-menu-usuario>): fecha ao clicar fora, apertar ESC
// ou ao navegar com Tab para fora dele.

const menus = document.querySelectorAll('[data-menu-usuario]');

document.addEventListener('click', (evento) => {
  menus.forEach((menu) => {
    if (menu.open && !menu.contains(evento.target)) menu.open = false;
  });
});

document.addEventListener('keydown', (evento) => {
  if (evento.key !== 'Escape') return;
  menus.forEach((menu) => {
    if (!menu.open) return;
    menu.open = false;
    menu.querySelector('summary')?.focus();
  });
});

menus.forEach((menu) => {
  menu.addEventListener('focusout', (evento) => {
    if (menu.open && evento.relatedTarget && !menu.contains(evento.relatedTarget)) menu.open = false;
  });
});
