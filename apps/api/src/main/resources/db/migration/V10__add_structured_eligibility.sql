-- Store structured eligibility alongside raw text for reliable relevance matching.
ALTER TABLE extracted_action ADD COLUMN structured_eligibility_json TEXT;
