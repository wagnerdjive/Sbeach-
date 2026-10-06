# Reservas próprias

## Implementado

`reservations.html` substitui o redireccionamento para a página de reservas existente por um formulário PT/EN com nome, telefone, data, horário preferido, número de pessoas, espaço e notas opcionais. Datas anteriores a hoje (fuso horário de Maputo) são bloqueadas. O formulário prepara um email para `turigest@southbeach.co.mz`; a pessoa revê e envia o pedido no seu próprio programa de email. O texto deixa claro que data e horário são preferências e que só a equipa confirma a reserva.

## Ainda não é uma agenda de reservas

O pedido depende de email e não fica guardado num painel. Não há verificação de disponibilidade, confirmação automática, prevenção de reservas duplicadas, lista de espera nem gestão de mesas. O browser não tem acesso a uma base de dados própria ou ao calendário do restaurante.

## Para aceitar e gerir reservas no próprio site

1. Criar um serviço autenticado e uma base de dados controlada pelo South Beach.
2. Configurar os espaços, horários de funcionamento, capacidade por mesa/área, duração de cada reserva, limites por grupo e períodos bloqueados.
3. Verificar disponibilidade e criar a reserva de modo atómico no servidor, para impedir conflitos de horário ou mesa.
4. Enviar confirmação e lembretes por email/SMS; permitir à equipa confirmar, alterar, cancelar e registar chegadas num painel protegido por funções.
5. Registar consentimento, limitar dados pessoais ao necessário e definir prazo de retenção, exportação e cópias de segurança.

Os horários e regras de capacidade devem ser fornecidos e confirmados pelo South Beach antes de activar reservas automáticas. O site actual não inventa essa disponibilidade.
