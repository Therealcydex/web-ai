ALTER TABLE stayora.reviews ADD COLUMN response text;
ALTER TABLE stayora.reviews ADD COLUMN response_at timestamptz;
ALTER TABLE stayora.reviews ADD CONSTRAINT reviews_response_on_stay CHECK (direction = 'STAY' OR response IS NULL);
ALTER TABLE stayora.reviews ADD CONSTRAINT reviews_response_dated CHECK ((response IS NULL) = (response_at IS NULL));
ALTER TABLE stayora.reviews ADD CONSTRAINT reviews_response_length CHECK (response IS NULL OR char_length(response) BETWEEN 2 AND 1000);
