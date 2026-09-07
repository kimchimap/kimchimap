CREATE INDEX origin_record_restaurant_review ON app.origin_record(restaurant_id, review_status, reviewed_at);
CREATE INDEX designation_restaurant ON app.designation(restaurant_id);
CREATE INDEX evidence_restaurant ON app.evidence(restaurant_id);
CREATE INDEX evidence_source ON app.evidence(source_id);
