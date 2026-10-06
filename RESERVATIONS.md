# Reservas próprias

## Implementado

`reservations.html` substitui o redireccionamento para a página de reservas existente por um formulário PT/EN com nome, telefone, data, horário preferido, número de pessoas, espaço e notas opcionais. Datas anteriores a hoje (fuso horário de Maputo) são bloqueadas. O formulário envia o pedido à API Spring Boot e mostra a referência devolvida. Cada pedido começa em `PENDING`; a equipa confirma ou cancela através da API administrativa protegida.

O código e instruções para iniciar a API estão em [`backend/README.md`](backend/README.md). Usa H2 em ficheiro para desenvolvimento local e PostgreSQL quando configurado para produção.

## Ainda não é uma agenda de reservas

Os pedidos ficam guardados, mas o sistema ainda não é um calendário de mesas. Não há verificação de disponibilidade, confirmação automática, prevenção de sobreposições, lista de espera ou gestão de mesas. A API ainda não envia notificações por email/SMS; a equipa tem de consultar os pedidos e contactar os clientes. O formulário precisa de um backend acessível, configuração CORS e base de dados de produção para funcionar no site alojado.

## Para aceitar e gerir reservas no próprio site

1. Configurar espaços, horários, capacidade por mesa/área, duração, limites por grupo e períodos bloqueados.
2. Verificar disponibilidade e atribuir mesas/horários no servidor de modo atómico para impedir sobreposições.
3. Adicionar confirmações e lembretes por email/SMS e criar um painel protegido para a equipa rever, alterar, cancelar e registar chegadas.
4. Registar consentimento, limitar dados pessoais ao necessário e definir prazo de retenção, exportação e cópias de segurança.

Os horários e regras de capacidade devem ser fornecidos e confirmados pelo South Beach antes de activar reservas automáticas. A API guarda a data e hora pedidas; não afirma que estão disponíveis.
