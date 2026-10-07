# Bilheteira própria do South Beach

## O que foi criado nesta etapa

`tickets.html` é uma prévia interactiva e bilingue da experiência directa de bilhetes. Permite testar categorias, quantidades, resumo e a apresentação de M-Pesa, e-Mola e cartões. O conteúdo usa um evento fictício e informa em todas as etapas que não há venda nem cobrança. Não recolhe dados pessoais, não emite QR codes e não liga para a Tabater.

## Integração com a API

O fluxo do visitante tem dois passos:

1. **`events.html`** (`events-live.js`) mostra a lista de eventos publicados, cada um com cartaz, data, local, preço mínimo ("a partir de") e estado (esgotado ou venda em breve). Se não houver eventos publicados ou a API não responder, mantém-se a página estática com "Sem eventos anunciados".
2. **`tickets.html?evento=<slug>`** (`tickets-live.js`) é o ecrã de venda desse evento: categorias, quantidades, total e formulário que cria uma encomenda em `POST /api/orders`, a qual reserva os bilhetes durante 15 minutos. Há uma ligação "← Todos os eventos" e, se o evento não existir, uma mensagem. Sem `?evento=`, a página redirecciona para a lista quando há eventos; sem eventos ou sem API, mostra a prévia de demonstração.

**Não há pagamento**: a página diz-o ao cliente e a equipa conclui a compra por contacto. Para o desenvolvimento local, publique um evento pela API administrativa (ver `backend/README.md`) ou pelo painel `admin.html`.

## O que é necessário para vender bilhetes reais

O site actual é estático. Uma venda segura precisa de um serviço próprio e armazenamento durável, porque o browser não pode confirmar um pagamento nem proteger segredos do comerciante. Arquitectura proposta:

1. **Gestão autenticada:** criar/editar eventos, fases e lotes, capacidade, preços, datas de venda, regras de grupo/lounge, códigos promocionais e permissões da equipa.
2. **Reserva atómica de inventário:** criar encomenda pendente com expiração e bloquear a quantidade pedida para evitar sobre-venda em compras simultâneas.
3. **Pagamento no servidor:** adaptadores para M-Pesa, e-Mola e cartões; validar notificações assinadas do provedor, estado e valor no servidor; tratar repetição de notificações de forma idempotente. O retorno do browser nunca marca uma encomenda como paga.
4. **Emissão e entrega:** após confirmação do provedor, emitir um bilhete individual com identificador aleatório, QR assinado, recibo e envio por email/SMS. Nunca incluir dados sensíveis no QR.
5. **Porta e operação:** scanner autenticado para validar cada QR uma única vez, painel de vendas, exportação, reconciliação, cancelamentos/reembolsos e registo de auditoria.
6. **Dados e continuidade:** South Beach controla domínio, base de dados, acessos, cópias de segurança e exportação dos clientes, respeitando consentimento e retenção aplicáveis.

## Esboço do modelo de dados

- `events`: slug, título, descrição, local, início/fim, estado e política de entrada.
- `ticket_types`: evento, nome, preço em MZN, capacidade, quantidade vendida/reservada, janela de venda e regras de grupo.
- `orders`: referência, contacto mínimo necessário, total, moeda, estado, expiração e timestamps.
- `order_items`: lote, quantidade, preço congelado no momento da compra.
- `payments`: provedor, método, referência externa, montante, estado e timestamps; nunca guardar PAN/CVV de cartão.
- `tickets`: identificador aleatório, encomenda, lote, estado, emissão e validação/entrada.
- `staff`, `roles`, `audit_log`: acesso por função e histórico das acções administrativas.

## Critério para abrir vendas

Não activar vendas até escolher os provedores e obter contas de comerciante em nome do South Beach, confirmar liquidação e taxas, completar testes de pagamento e reembolso, configurar domínio/API, email/SMS e autenticação administrativa, e validar operação de entrada. A escolha de M-Pesa + e-Mola + cartões é preferência de canais, não uma conta/API já configurada.

O lançamento deve ser feito com eventos, preços, datas e capacidade confirmados pelo South Beach. Valores de fases antigas do exemplo Tabater não são reutilizados.
