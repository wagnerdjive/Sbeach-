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
- [ ] Backend de eventos e vendas
- [ ] Lotes, preços e inventário
- [ ] Pagamentos reais por M-Pesa, e-Mola e cartão
- [ ] Emissão e envio de bilhetes QR
- [ ] Leitura à entrada e relatórios

## Eventos

- [x] Página estática com arquivo e formulário para eventos privados
- [ ] Área de gestão para a equipa publicar eventos e actualizar datas, textos e bilhetes

## Conteúdo do site

- [x] Páginas informativas, menus, galeria e contactos
- [ ] CMS para editar o conteúdo sem alterar ficheiros do site
