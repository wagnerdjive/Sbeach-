(() => {
  const selected = { standard: 0, vip: 0, lounge: 0 };
  const dictionaries = {
    pt: {
      kicker:'BILHETES SOUTH BEACH', title:'Os seus próximos<br /><em>momentos começam aqui.</em>', intro:'Uma bilheteira própria, com eventos, pagamentos e confirmações geridos directamente pelo South Beach.', previewLabel:'PRÉVIA INTERACTIVA', previewTitle:'Como será <em>escolher bilhetes.</em>', demoBadge:'DEMONSTRAÇÃO', notice:'Esta é uma prévia do checkout. Nenhum evento está à venda e nenhum pagamento será iniciado.', demoEvent:'EVENTO DE DEMONSTRAÇÃO', eventName:'South Beach Sunset Sessions', eventDetails:'South Beach Maputo · Data e programa a anunciar', tagMusic:'Música', tagOcean:'À beira-mar', standardName:'NORMAL', standardDesc:'Acesso geral', standardNote:'Exemplo de categoria', vipName:'VIP', vipDesc:'Acesso VIP', vipNote:'Exemplo de categoria', loungeName:'LOUNGE', loungeDesc:'Mesa ou lounge', loungeNote:'Capacidade e consumo a definir', pricePending:'Preço a definir', orderLabel:'O SEU PEDIDO', checkoutTitle:'Resumo', selectedTickets:'Bilhetes seleccionados', totalEstimate:'Total estimado', selectHint:'Escolha uma categoria para ver o resumo.', paymentLabel:'Meios de pagamento previstos', cards:'Cartão', checkoutButton:'Continuar', paymentHint:'Os métodos aparecem como prévia. A integração e a confirmação de pagamento ainda não estão activas.', ownershipLabel:'FEITO PARA O SOUTH BEACH', ownershipTitle:'Do anúncio à<br /><em>entrada no evento.</em>', ownershipOne:'Eventos e lotes publicados pela própria equipa.', ownershipTwo:'Confirmações e bilhetes digitais emitidos pela plataforma.', ownershipThree:'Validação de entrada e relatórios de vendas num só painel.', dialogLabel:'PRÓXIMO PASSO', dialogTitle:'Checkout próprio, <em>em preparação.</em>', dialogCopy:'A selecção foi demonstrada. Para começar a vender, o South Beach precisa ligar a bilheteira ao seu comerciante M-Pesa, e-Mola e cartões, e activar o serviço de confirmação e emissão de bilhetes.', dialogButton:'Entendi', chosen:'seleccionado(s)', ariaMinus:'Diminuir quantidade', ariaPlus:'Aumentar quantidade'
    },
    en: {
      kicker:'SOUTH BEACH TICKETS', title:'Your next<br /><em>moments start here.</em>', intro:'A ticketing experience owned by South Beach, with events, payments and confirmations managed directly by the team.', previewLabel:'INTERACTIVE PREVIEW', previewTitle:'Choosing <em>your tickets.</em>', demoBadge:'DEMO', notice:'This is a checkout preview. No event is on sale and no payment will be started.', demoEvent:'DEMO EVENT', eventName:'South Beach Sunset Sessions', eventDetails:'South Beach Maputo · Date and programme to be announced', tagMusic:'Music', tagOcean:'By the ocean', standardName:'STANDARD', standardDesc:'General admission', standardNote:'Sample category', vipName:'VIP', vipDesc:'VIP access', vipNote:'Sample category', loungeName:'LOUNGE', loungeDesc:'Table or lounge', loungeNote:'Capacity and spend to be confirmed', pricePending:'Price to be set', orderLabel:'YOUR ORDER', checkoutTitle:'Summary', selectedTickets:'Selected tickets', totalEstimate:'Estimated total', selectHint:'Choose a category to see your order.', paymentLabel:'Planned payment methods', cards:'Card', checkoutButton:'Continue', paymentHint:'Payment methods are shown as a preview. Payment and confirmation are not active.', ownershipLabel:'BUILT FOR SOUTH BEACH', ownershipTitle:'From event listing<br /><em>to the door.</em>', ownershipOne:'Events and ticket tiers published by the team.', ownershipTwo:'Confirmations and digital tickets issued by the platform.', ownershipThree:'Door check-in and sales reports in one dashboard.', dialogLabel:'NEXT STEP', dialogTitle:'South Beach checkout, <em>in preparation.</em>', dialogCopy:'This selection is a preview. Before sales open, South Beach needs to connect its merchant accounts for M-Pesa, e-Mola and cards and activate payment confirmation and ticket issuance.', dialogButton:'Got it', chosen:'selected', ariaMinus:'Decrease quantity', ariaPlus:'Increase quantity'
    }
  };
  const dialog = document.querySelector('#checkout-dialog');
  const labels = document.querySelectorAll('[data-ticket-text]');
  const isEnglish = () => { try { return localStorage.getItem('southBeachLanguage') === 'en'; } catch (_) { return false; } };
  function renderLanguage() {
    const language = isEnglish() ? 'en' : 'pt';
    labels.forEach((node) => { const value = dictionaries[language][node.dataset.ticketText]; if (value) node.innerHTML = value; });
    document.querySelectorAll('[data-quantity-change]').forEach((button) => button.setAttribute('aria-label', button.dataset.quantityChange === '1' ? dictionaries[language].ariaPlus : dictionaries[language].ariaMinus));
    updateSummary();
  }
  function updateSummary() {
    const count = Object.values(selected).reduce((sum, quantity) => sum + quantity, 0);
    document.querySelector('#ticket-count').textContent = String(count);
    document.querySelectorAll('[data-quantity]').forEach((output) => { output.value = String(selected[output.dataset.quantity]); output.textContent = String(selected[output.dataset.quantity]); });
    document.querySelector('#checkout-preview').disabled = count === 0;
    const hint = document.querySelector('.checkout-card .checkout-hint');
    if (count) hint.textContent = `${count} ${dictionaries[isEnglish() ? 'en' : 'pt'].chosen}`;
    else hint.textContent = dictionaries[isEnglish() ? 'en' : 'pt'].selectHint;
  }
  document.querySelectorAll('[data-quantity-change]').forEach((button) => button.addEventListener('click', () => {
    const key = button.dataset.category;
    selected[key] = Math.max(0, Math.min(10, selected[key] + Number(button.dataset.quantityChange)));
    updateSummary();
  }));
  document.querySelector('[data-language-toggle]')?.addEventListener('click', () => setTimeout(renderLanguage, 0));
  document.querySelector('#checkout-preview').addEventListener('click', () => dialog.showModal());
  document.querySelectorAll('.preview-dialog-close, .preview-dialog-ok').forEach((button) => button.addEventListener('click', () => dialog.close()));
  dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
  renderLanguage();
})();
