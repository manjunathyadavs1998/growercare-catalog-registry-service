-- =====================================================================================================
-- DEV/TEST ONLY (GC_FLYWAY_LOCATIONS + classpath:db/dev-seed). SPEC-CAT-01 R31: the products case-service seeded
-- (same ids, D3) plus 9004, withdrawn in India. Product names are fictitious.
-- =====================================================================================================
INSERT INTO catalog.product (product_id, product_code, bu_code, name, product_kind, status, version, updated_at) VALUES
    (9001, 'AZX-325SC', 'CP',    'Azoxa Duo 325 SC',    'FUNGICIDE',   'ACTIVE', 1, TIMESTAMPTZ '2026-11-01T00:00:00Z'),
    (9002, 'LMB-50CS',  'CP',    'Lambdor 5 CS',        'INSECTICIDE', 'ACTIVE', 1, TIMESTAMPTZ '2026-11-01T00:00:00Z'),
    (9003, 'HYC-6668',  'SEEDS', 'Hybrid corn HC-6668', 'SEED',        'ACTIVE', 1, TIMESTAMPTZ '2026-11-01T00:00:00Z'),
    (9004, 'MCZ-75WP',  'CP',    'Mancozan 75 WP',      'FUNGICIDE',   'ACTIVE', 1, TIMESTAMPTZ '2026-11-01T00:00:00Z');

INSERT INTO catalog.product_ingredient (product_id, position, name, concentration) VALUES
    (9001, 1, 'Azoxystrobin', '200 g/L'), (9001, 2, 'Difenoconazole', '125 g/L'),
    (9002, 1, 'Lambda-cyhalothrin', '50 g/L'),
    (9004, 1, 'Mancozeb', '75 %');

INSERT INTO catalog.product_pack (product_id, position, pack_size) VALUES
    (9001, 1, '100 mL'), (9001, 2, '500 mL'), (9002, 1, '250 mL'), (9003, 1, '4 kg'), (9004, 1, '1 kg');

INSERT INTO catalog.registration (country_code, product_id, registration_no, status, valid_until, version) VALUES
    ('IN', 9001, 'CIR-123456/2024', 'REGISTERED', DATE '2029-12-31', 1),
    ('IN', 9002, 'CIR-223344/2023', 'REGISTERED', DATE '2028-12-31', 1),
    ('IN', 9003, 'SEED-IN-6668',    'REGISTERED', NULL,              1),
    ('BR', 9001, 'MAPA-10120',      'REGISTERED', DATE '2030-06-30', 1),
    ('IN', 9004, 'CIR-110022/2019', 'WITHDRAWN',  DATE '2025-03-31', 1);

INSERT INTO catalog.batch (product_id, batch_no, mfg_date, expiry_date, plant_code, status, version) VALUES
    (9001, 'AZX2604-117', DATE '2026-04-10', DATE '2028-04-09', 'IN-GOA', 'RELEASED', 1),
    (9002, 'LMB2602-031', DATE '2026-02-02', DATE '2028-02-01', 'IN-GOA', 'RELEASED', 1),
    (9003, 'HYC2605-009', DATE '2026-05-20', DATE '2027-05-19', 'IN-HYD', 'RELEASED', 1);
