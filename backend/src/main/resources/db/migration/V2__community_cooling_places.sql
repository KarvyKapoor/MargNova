
ALTER TABLE cooling_places
ADD COLUMN seating_available BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE cooling_places
ADD COLUMN water_available BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE cooling_places
ADD COLUMN shade_type VARCHAR(40);

ALTER TABLE cooling_places
ADD COLUMN submitted_by VARCHAR(100);

ALTER TABLE cooling_places
ADD COLUMN verification_status VARCHAR(20)
NOT NULL DEFAULT 'PENDING';

ALTER TABLE cooling_places
ADD COLUMN updated_at TIMESTAMP;

UPDATE cooling_places
SET verification_status =
    CASE
        WHEN is_verified = TRUE THEN 'VERIFIED'
        ELSE 'PENDING'
    END;

CREATE INDEX idx_cooling_places_location
ON cooling_places(lat, lng);

CREATE INDEX idx_cooling_places_verification
ON cooling_places(verification_status);
