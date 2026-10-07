-- Refunds are recorded by staff after returning the money outside the system; the order keeps a note of why.
ALTER TABLE ticket_orders DROP CONSTRAINT chk_ticket_orders_status;
ALTER TABLE ticket_orders ADD CONSTRAINT chk_ticket_orders_status CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED', 'REFUNDED'));
ALTER TABLE ticket_orders ADD COLUMN refunded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ticket_orders ADD COLUMN refund_note VARCHAR(200);
