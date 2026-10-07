# Roadmap South Beach

Legenda: `[x]` feito · `[ ]` por fazer. Cada item é marcado quando implementado.

## Reservas

- [x] Formulário ligado à API Spring Boot
- [x] Validação e gravação dos pedidos, com referência por pedido
- [x] Endpoints protegidos para consultar, confirmar ou cancelar
- [x] Verificar disponibilidade real e evitar conflitos (capacidade por espaço e horário, de forma atómica)
- [x] Painel para a equipa (rever, confirmar e cancelar pedidos)
- [ ] Confirmações e lembretes por email/SMS — pendente: escolher fornecedor e configurar conta (código base pronto: email por SMTP e interface `SmsGateway`, desligados por omissão)
- [ ] Publicar a API e configurar a base de dados de produção — pendente: escolher alojamento

## Bilhetes

- [x] Prévia visual e interactiva com categorias e quantidades
- [x] Página de bilhetes ligada à API (eventos publicados, quantidades, reserva de bilhetes; sem pagamento)
- [x] Backend de eventos e vendas (eventos, encomendas com reserva de stock e expiração; sem pagamento)
- [x] Lotes, preços e inventário (categorias com preço, capacidade, janela de venda e limite por encomenda)
- [ ] Pagamentos reais por M-Pesa, e-Mola e cartão
- [x] Emissão e envio de bilhetes QR (emissão ao marcar a encomenda como paga; envio por ligação privada, automático quando houver email/SMS)
- [x] Leitura à entrada (separador *Entrada*; validação única e contagem de entradas)
- [ ] Relatórios de vendas

## Eventos

- [x] Página estática com arquivo e formulário para eventos privados
- [x] Área de gestão para a equipa publicar eventos e actualizar datas, textos e bilhetes (`admin.html`: eventos, categorias, encomendas)
- [x] Lista de eventos em `events.html` e ecrã de venda por evento (`tickets.html?evento=`)
- [x] Cartaz do evento: carregar no painel e mostrar na página de bilhetes

## Conteúdo do site

- [x] Páginas informativas, menus, galeria e contactos
- [x] CMS para editar o conteúdo sem alterar ficheiros do site (textos PT/EN, imagens e ligações; separador *Conteúdo do site*)
