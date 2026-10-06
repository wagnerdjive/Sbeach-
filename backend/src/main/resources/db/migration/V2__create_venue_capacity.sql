CREATE TABLE venue_capacity (
    venue VARCHAR(24) PRIMARY KEY,
    capacity INTEGER NOT NULL,
    CONSTRAINT chk_venue_capacity_positive CHECK (capacity > 0),
    CONSTRAINT chk_venue_capacity_venue CHECK (venue IN ('RESTAURANT', 'BEACH_BAR', 'SPORTS_BAR'))
);

-- Placeholder seat capacities: South Beach must confirm the real values.
INSERT INTO venue_capacity (venue, capacity) VALUES ('RESTAURANT', 60);
INSERT INTO venue_capacity (venue, capacity) VALUES ('BEACH_BAR', 80);
INSERT INTO venue_capacity (venue, capacity) VALUES ('SPORTS_BAR', 50);
