-- Captions. A video whose content is in its narration is unusable to anyone deaf or hard of
-- hearing, and to anyone watching at a ward station without headphones.
--
-- One track per video, English. Multiple languages would need a table of its own; nothing in the
-- product is translated yet, so a column is honest about what exists.
ALTER TABLE media_asset
    ADD COLUMN caption_key TEXT;
