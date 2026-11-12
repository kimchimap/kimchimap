CREATE INDEX report_approved_revision_date ON app.report_review((revision_id::text),created_at)
    WHERE decision='APPROVED';
