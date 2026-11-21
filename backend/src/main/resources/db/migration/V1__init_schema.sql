-- SafeRoute Schema Migration V1
-- Creates baseline persistence tables for location nodes, cooling amenities, and evaluation audits.

CREATE TABLE IF NOT EXISTS location_nodes (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    shade DOUBLE PRECISION NOT NULL,
    cooling_name VARCHAR(128),
    data_trust VARCHAR(32) NOT NULL DEFAULT 'SIMULATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cooling_places (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    shade_score DOUBLE PRECISION NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    opening_hours VARCHAR(128),
    description VARCHAR(255),
    data_trust VARCHAR(32) NOT NULL DEFAULT 'SIMULATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS route_evaluations (
    id VARCHAR(64) PRIMARY KEY,
    origin VARCHAR(64) NOT NULL,
    destination VARCHAR(64) NOT NULL,
    profile VARCHAR(32) NOT NULL,
    temperature_c DOUBLE PRECISION NOT NULL,
    recommended_route_id VARCHAR(64),
    suitability_score DOUBLE PRECISION,
    heat_score DOUBLE PRECISION,
    safety_score DOUBLE PRECISION,
    duration_min DOUBLE PRECISION,
    distance_m INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed baseline illustrative demo location nodes
INSERT INTO location_nodes (id, name, lat, lng, shade, cooling_name, data_trust)
VALUES
    ('station', 'Transit Hub', 23.2599, 77.4126, 0.18, NULL, 'SIMULATED'),
    ('market', 'Market Crossing', 23.2610, 77.4142, 0.25, NULL, 'SIMULATED'),
    ('park', 'Central Green', 23.2620, 77.4110, 0.82, 'Central Green Rest Point', 'SIMULATED'),
    ('library', 'Civic Library', 23.2630, 77.4134, 0.48, 'Civic Library (demo point)', 'SIMULATED'),
    ('clinic', 'Community Clinic Junction', 23.2578, 77.4144, 0.35, NULL, 'SIMULATED'),
    ('lake', 'Lake Walk', 23.2600, 77.4092, 0.72, NULL, 'SIMULATED');

-- Seed illustrative cooling and rest opportunities
INSERT INTO cooling_places (id, name, category, lat, lng, shade_score, is_verified, opening_hours, description, data_trust)
VALUES
    ('park-cooling', 'Central Green Rest Point', 'PARK', 23.2620, 77.4110, 0.82, FALSE, '06:00 - 20:00', 'Public shaded park area with benches and high tree cover.', 'SIMULATED'),
    ('library-cooling', 'Civic Library (Indoor Climate Haven)', 'LIBRARY', 23.2630, 77.4134, 0.48, FALSE, '09:00 - 18:00', 'Air-conditioned public building offering resting seating.', 'SIMULATED'),
    ('lake-cooling', 'Lake Walk Shaded Pavilion', 'REST_POINT', 23.2600, 77.4092, 0.72, FALSE, '24/7', 'Covered gazebo with breeze and tree shade near the lake promenade.', 'SIMULATED');
