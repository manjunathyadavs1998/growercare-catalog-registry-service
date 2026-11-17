-- catalog-registry-service (schema catalog, SPEC-CAT-01). Statement ids: catalog.<name>

-- name: countryExists
SELECT EXISTS (SELECT 1 FROM catalog.country c WHERE c.country_code = :country)

-- name: buExists
SELECT EXISTS (SELECT 1 FROM catalog.business_unit b WHERE b.bu_code = :bu)

-- D5: registered and valid in the country; name contains q or code starts with q; keyset pages by (lower(name), id).
-- name: search
SELECT p.product_id, p.product_code, p.bu_code, p.name, p.product_kind, p.status, p.version,
       r.country_code, r.registration_no, r.status AS registration_status, r.valid_until, r.version AS registration_version
  FROM catalog.product p
  JOIN catalog.registration r ON r.product_id = p.product_id AND r.country_code = :country
 WHERE r.status = 'REGISTERED' AND (r.valid_until IS NULL OR r.valid_until >= :today)
   AND (CAST(:namePattern AS text) IS NULL OR lower(p.name) LIKE :namePattern ESCAPE '\' OR p.product_code LIKE :codePattern ESCAPE '\')
   AND (CAST(:kind AS text) IS NULL OR p.product_kind = :kind)
   AND (CAST(:afterName AS text) IS NULL OR (lower(p.name), p.product_id) > (CAST(:afterName AS text), CAST(:afterId AS bigint)))
 ORDER BY lower(p.name), p.product_id
 LIMIT :limit

-- name: product
SELECT p.product_id, p.product_code, p.bu_code, p.name, p.product_kind, p.status, p.version
  FROM catalog.product p WHERE p.product_id = :productId

-- name: ingredients
SELECT i.name, i.concentration FROM catalog.product_ingredient i WHERE i.product_id = :productId ORDER BY i.position

-- name: packs
SELECT k.pack_size FROM catalog.product_pack k WHERE k.product_id = :productId ORDER BY k.position

-- name: registrations
SELECT r.country_code, r.product_id, r.registration_no, r.status, r.valid_until, r.version
  FROM catalog.registration r WHERE r.product_id = :productId ORDER BY r.country_code

-- name: registration
SELECT r.country_code, r.product_id, r.registration_no, r.status, r.valid_until, r.version
  FROM catalog.registration r WHERE r.country_code = :country AND r.product_id = :productId

-- name: batch
SELECT b.product_id, b.batch_no, b.mfg_date, b.expiry_date, b.plant_code, b.status, b.version
  FROM catalog.batch b WHERE b.product_id = :productId AND b.batch_no = :batchNo

-- name: lockProduct
SELECT p.product_id, p.product_code, p.bu_code, p.name, p.product_kind, p.status, p.version
  FROM catalog.product p WHERE p.product_code = :code FOR UPDATE

-- name: insertProduct
INSERT INTO catalog.product (product_code, bu_code, name, product_kind, status, version)
VALUES (:code, :bu, :name, :kind, :status, :version)
ON CONFLICT (product_code) DO NOTHING
RETURNING product_id

-- name: updateProduct
UPDATE catalog.product
   SET bu_code = :bu, name = :name, product_kind = :kind, status = :status, version = :version, updated_at = now()
 WHERE product_id = :productId

-- name: deleteIngredients
DELETE FROM catalog.product_ingredient WHERE product_id = :productId

-- name: insertIngredient
INSERT INTO catalog.product_ingredient (product_id, position, name, concentration) VALUES (:productId, :position, :name, :concentration)

-- name: deletePacks
DELETE FROM catalog.product_pack WHERE product_id = :productId

-- name: insertPack
INSERT INTO catalog.product_pack (product_id, position, pack_size) VALUES (:productId, :position, :packSize)

-- name: lockRegistration
SELECT r.country_code, r.product_id, r.registration_no, r.status, r.valid_until, r.version
  FROM catalog.registration r WHERE r.country_code = :country AND r.product_id = :productId FOR UPDATE

-- name: saveRegistration
INSERT INTO catalog.registration (country_code, product_id, registration_no, status, valid_until, version)
VALUES (:country, :productId, :registrationNo, :status, :validUntil, :version)
ON CONFLICT (country_code, product_id) DO UPDATE
   SET registration_no = EXCLUDED.registration_no, status = EXCLUDED.status, valid_until = EXCLUDED.valid_until,
       version = EXCLUDED.version, updated_at = now()

-- name: lockBatch
SELECT b.product_id, b.batch_no, b.mfg_date, b.expiry_date, b.plant_code, b.status, b.version
  FROM catalog.batch b WHERE b.product_id = :productId AND b.batch_no = :batchNo FOR UPDATE

-- name: saveBatch
INSERT INTO catalog.batch (product_id, batch_no, mfg_date, expiry_date, plant_code, status, version)
VALUES (:productId, :batchNo, :mfgDate, :expiryDate, :plantCode, :status, :version)
ON CONFLICT (product_id, batch_no) DO UPDATE
   SET mfg_date = EXCLUDED.mfg_date, expiry_date = EXCLUDED.expiry_date, plant_code = EXCLUDED.plant_code,
       status = EXCLUDED.status, version = EXCLUDED.version, updated_at = now()
