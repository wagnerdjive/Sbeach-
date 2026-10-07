# WhatsApp — envio automático ao cliente

O site envia mensagens ao cliente pela **WhatsApp Cloud API** (Meta): bilhete pago, reserva confirmada, reserva cancelada e lembrete. Está **desligado por omissão**.

## Fase 1 — demonstração, só texto simples (sem templates, sem custo por modelo)

Activar com variáveis de ambiente no servidor (o token é um segredo: nunca no Git, nunca em ficheiros do repositório):

| Variável | Valor |
|---|---|
| `NOTIFY_WHATSAPP_ENABLED` | `true` |
| `WHATSAPP_PHONE_NUMBER_ID` | identificador do número (na Meta: *WhatsApp → API Setup*) |
| `WHATSAPP_TOKEN` | token de acesso (de preferência o de um *utilizador de sistema*, que não expira em 24 horas) |
| `WHATSAPP_API_VERSION` | opcional, por omissão `v21.0` |

**Limite do texto simples:** o WhatsApp só entrega texto livre a quem **escreveu para o número da empresa nas últimas 24 horas** (a "janela de atendimento"). Para qualquer outro cliente a Meta recusa (erro `131047`); o site regista o motivo no log e **nunca desfaz a reserva nem o pagamento**. Para a demonstração: a pessoa que vai receber envia primeiro uma mensagem ("oi") ao número da empresa e, nas 24 horas seguintes, recebe as mensagens normalmente.

Numa conta de teste da Meta, as mensagens só chegam a destinatários autorizados (erro `131030`).

## Fase 2 — modelos (templates) para aprovação da Meta

Fora da janela de 24 horas só se pode escrever com **modelos aprovados**. Todos os modelos abaixo são de categoria **UTILITY** (mensagens sobre algo que o cliente pediu), o que os mantém mais baratos e mais fáceis de aprovar do que os de marketing. Idioma: `pt_PT`.

Regras da Meta que estes textos já respeitam: sem conteúdo promocional; as variáveis `{{n}}` nunca estão no início ou no fim do texto nem juntas umas às outras; cada uma tem um exemplo. Na submissão, preencher os **exemplos** indicados.

### 1. `bilhetes_pagos`
- **Corpo:** `Olá! O pagamento da sua encomenda {{1}} foi recebido. Os seus bilhetes para {{2}} já estão disponíveis. Mostre o código QR à entrada: cada código vale para uma pessoa.`
- **Botão (URL):** texto `Ver os meus bilhetes` → `https://southbeach.techtarget.host/ticket.html?t={{1}}` (o `{{1}}` do botão é o código privado do bilhete)
- **Exemplos:** corpo `TK-1F8BDAD3721B`, `Sunset Sessions`; botão `UFatpVu51hURAi3Fi66hhvTTvg-bGXxc`

### 2. `encomenda_reservada`
- **Corpo:** `Olá {{1}}! Reservámos os seus bilhetes para {{2}} (encomenda {{3}}, total {{4}}) até às {{5}}. Para concluir a compra, a nossa equipa vai contactá-lo; também pode responder a esta mensagem.`
- **Exemplos:** `Ana`, `Sunset Sessions`, `TK-1F8BDAD3721B`, `3000 MZN`, `14:30`

### 3. `reserva_confirmada`
- **Corpo:** `Olá {{1}}! A sua reserva {{2}} no South Beach está confirmada para {{3}}, às {{4}}, para {{5}} pessoas. Se precisar de alterar, responda a esta mensagem.`
- **Exemplos:** `Ana`, `SB-9DB9564E9CFD`, `sábado, 24 de outubro`, `19:00`, `4`

### 4. `reserva_cancelada`
- **Corpo:** `Olá {{1}}. A sua reserva {{2}} no South Beach, marcada para {{3}} às {{4}}, foi cancelada. Se quiser marcar outra data, responda a esta mensagem.`
- **Exemplos:** `Ana`, `SB-9DB9564E9CFD`, `sábado, 24 de outubro`, `19:00`

### 5. `lembrete_reserva`
- **Corpo:** `Olá {{1}}! Lembramos que tem uma reserva no South Beach amanhã, {{2}}, às {{3}}, para {{4}} pessoas (referência {{5}}). Até já!`
- **Exemplos:** `Ana`, `24 de outubro`, `19:00`, `4`, `SB-9DB9564E9CFD`

### 6. `reembolso_registado`
- **Corpo:** `Olá {{1}}. O reembolso da encomenda {{2}} ({{3}}) foi registado e os bilhetes correspondentes ficaram anulados. Qualquer dúvida, responda a esta mensagem.`
- **Exemplos:** `Ana`, `TK-1F8BDAD3721B`, `3000 MZN`

### Como submeter
1. Meta Business Suite → *WhatsApp Manager* → *Modelos de mensagem* → *Criar modelo*.
2. Categoria **Utilidade**, idioma **Português (Portugal)**, nome exactamente como acima.
3. Colar o corpo, adicionar os exemplos pedidos e (no `bilhetes_pagos`) o botão de URL dinâmico.
4. A aprovação costuma demorar de minutos a algumas horas. Depois de aprovados, o envio com modelos passa a funcionar para qualquer cliente, dentro ou fora das 24 horas.

### O que falta no código quando os modelos estiverem aprovados
Hoje cada mensagem é texto livre. Com os modelos aprovados, acrescenta-se um segundo gateway que envia `type: "template"` (nome, idioma `pt_PT` e os parâmetros `{{n}}` pela ordem acima) e usa o texto simples como alternativa dentro da janela de 24 horas. As mensagens do site já existem em `ReservationMessages` e `TicketNotifier`; os parâmetros saem dos mesmos dados.

## Notas
- O **nome que o cliente vê** no WhatsApp é o nome verificado do número na Meta. Se esse nome não for o do South Beach, as mensagens aparecem em nome de outra empresa; convém registar o número do South Beach (ou alterar o nome verificado) antes de usar com clientes reais.
- As mensagens no site continuam a poder ser enviadas à mão pelo botão **WhatsApp** do painel (abre a conversa com o texto pronto), que não depende de nada disto.
