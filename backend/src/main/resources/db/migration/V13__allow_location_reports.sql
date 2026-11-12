ALTER TABLE app.report ALTER COLUMN restaurant_id DROP NOT NULL;
ALTER TABLE app.report ADD CONSTRAINT approved_report_has_restaurant
    CHECK (state <> 'APPROVED' OR restaurant_id IS NOT NULL);
