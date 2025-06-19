-- Add unique constraint for stream_id and version
ALTER TABLE events ADD CONSTRAINT uk_events_stream_version UNIQUE (stream_id, version);

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_events_stream_id ON events (stream_id);
CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events (timestamp);
CREATE INDEX IF NOT EXISTS idx_events_correlation_id ON events (correlation_id);
CREATE INDEX IF NOT EXISTS idx_events_event_type ON events (event_type);

-- JSONB GIN index for event data queries
CREATE INDEX IF NOT EXISTS idx_events_data_gin ON events USING GIN (event_data);