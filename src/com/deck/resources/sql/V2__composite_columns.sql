-- ============================================================================
-- Deck — V2 composite launch columns
-- ----------------------------------------------------------------------------
-- Adds support for the COMPOSITE launch type: run a background command, wait
-- a fixed delay, then open a URL. Solves the "start a local server, then open
-- localhost" pattern (RegiQuiz) without a general sequence engine.
--
-- All three columns are nullable — every existing row is a non-composite tile
-- and stays valid untouched.
-- ============================================================================

ALTER TABLE apps ADD COLUMN composite_startup  TEXT;
ALTER TABLE apps ADD COLUMN composite_delay_ms INTEGER;
ALTER TABLE apps ADD COLUMN composite_url      TEXT;
