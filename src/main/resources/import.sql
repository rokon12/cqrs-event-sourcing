-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_events_stream_id ON events (stream_id);
CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events (timestamp);
CREATE INDEX IF NOT EXISTS idx_events_correlation_id ON events (correlation_id);
CREATE INDEX IF NOT EXISTS idx_events_event_type ON events (event_type);

-- JSONB GIN index for event data queries
CREATE INDEX IF NOT EXISTS idx_events_data_gin ON events USING GIN (event_data);

-- Add indexes for snapshots
CREATE INDEX IF NOT EXISTS idx_snapshots_aggregate_id ON snapshots (aggregate_id);
CREATE INDEX IF NOT EXISTS idx_snapshots_aggregate_type ON snapshots (aggregate_type);
CREATE INDEX IF NOT EXISTS idx_snapshots_version ON snapshots (version);
CREATE INDEX IF NOT EXISTS idx_snapshots_created_at ON snapshots (created_at);

-- JSONB GIN index for snapshot state data queries
CREATE INDEX IF NOT EXISTS idx_snapshots_state_data_gin ON snapshots USING GIN (state_data);
