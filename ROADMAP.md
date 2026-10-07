# Roadmap South Beach

Legenda: `[x]` feito · `[ ]` por fazer. Cada item é marcado quando implementado.

## Reservas

- [x] Formulário ligado à API Spring Boot
- [x] Validação e gravação dos pedidos, com referência por pedido
- [x] Endpoints protegidos para consultar, confirmar ou cancelar
- [x] Verificar disponibilidade real e evitar conflitos (capacidade por espaço e horário, de forma atómica)
- [x] Painel para a equipa (rever, confirmar e cancelar pedidos)
- [x] Mensagens por WhatsApp enviadas pela equipa (botão *WhatsApp* nas reservas e encomendas; abre a conversa com a mensagem pronta)
- [ ] Envio automático por WhatsApp (WhatsApp Business API) — pendente: conta Meta Business, número e modelos de mensagem aprovados
- [ ] Confirmações e lembretes por email/SMS — pendente: escolher fornecedor e configurar conta (código base pronto: email por SMTP e interface `SmsGateway`, desligados por omissão)
- [ ] Publicar a API e configurar a base de dados de produção — pendente: escolher alojamento

## Bilhetes

- [x] Prévia visual e interactiva com categorias e quantidades
- [x] Página de bilhetes ligada à API (eventos publicados, quantidades, reserva de bilhetes; sem pagamento)
- [x] Backend de eventos e vendas (eventos, encomendas com reserva de stock e expiração; sem pagamento)
- [x] Lotes, preços e inventário (categorias com preço, capacidade, janela de venda e limite por encomenda)
- [x] Anular e reembolsar encomendas pagas (registo do reembolso feito pela equipa; bilhetes anulados e lugares devolvidos)
- [ ] Pagamentos reais por M-Pesa, e-Mola e cartão
- [x] Emissão e envio de bilhetes QR (emissão ao marcar a encomenda como paga; envio por ligação privada, automático quando houver email/SMS)
- [x] Leitura à entrada (separador *Entrada*; validação única e contagem de entradas)
- [x] Relatórios de vendas (separador *Relatórios*: receita, bilhetes por categoria, vendas por dia, estados e exportação CSV)

## Eventos

- [x] Página estática com arquivo e formulário para eventos privados
- [x] Área de gestão para a equipa publicar eventos e actualizar datas, textos e bilhetes (`admin.html`: eventos, categorias, encomendas)
- [x] Lista de eventos em `events.html` e ecrã de venda por evento (`tickets.html?evento=`)
- [x] Cartaz do evento: carregar no painel e mostrar na página de bilhetes

## Conteúdo do site

- [x] Páginas informativas, menus, galeria e contactos
- [x] Gestão das fotos da galeria (adicionar, ordenar, ocultar e remover; separador *Galeria*)
- [x] CMS para editar o conteúdo sem alterar ficheiros do site (textos PT/EN, imagens e ligações; separador *Conteúdo do site*)

## Direcção visual

- [x] Direcção clara e arejada, com detalhes finos (inspirada em Nikki Beach e SALT), em todas as páginas públicas — `refined.css`, `refined.js`; branch `direcao-visual-clara`, por aprovar
- [x] Identidade de cor: azul, amarelo e preto do logótipo (`refined.css`, variáveis no topo)
- [ ] Aprovar e juntar à `main`
