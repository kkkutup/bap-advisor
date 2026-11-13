ALTER TABLE core.submission_documents
    ALTER COLUMN ocr_confidence TYPE DOUBLE PRECISION USING ocr_confidence::double precision;
