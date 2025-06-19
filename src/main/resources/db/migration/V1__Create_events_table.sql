-- Event store table for high-performance event sourcing
CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    stream_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    version BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id VARCHAR(36),
    causation_id VARCHAR(36),
    
    CONSTRAINT events_stream_version_unique UNIQUE (stream_id, version)
);

-- Indexes for optimal query performance
CREATE INDEX IF NOT EXISTS idx_events_stream_id ON events (stream_id);
CREATE INDEX IF NOT EXISTS idx_events_stream_version ON events (stream_id, version);
CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events (timestamp);
CREATE INDEX IF NOT EXISTS idx_events_correlation_id ON events (correlation_id);
CREATE INDEX IF NOT EXISTS idx_events_event_type ON events (event_type);

-- Partial index for recent events (performance optimization)
CREATE INDEX IF NOT EXISTS idx_events_recent ON events (timestamp, stream_id) 
    WHERE timestamp > NOW() - INTERVAL '1 day';

-- JSONB GIN index for event data queries
CREATE INDEX IF NOT EXISTS idx_events_data_gin ON events USING GIN (event_data);

-- Table for tracking projection positions
CREATE TABLE IF NOT EXISTS projection_positions (
    projection_name VARCHAR(255) PRIMARY KEY,
    position BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Table for saga state management
CREATE TABLE IF NOT EXISTS saga_instances (
    saga_id VARCHAR(255) PRIMARY KEY,
    saga_type VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    compensation_data JSONB
);

CREATE INDEX IF NOT EXISTS idx_saga_type_status ON saga_instances (saga_type, status);
CREATE INDEX IF NOT EXISTS idx_saga_start_time ON saga_instances (start_time);

-- Performance monitoring table
CREATE TABLE IF NOT EXISTS performance_metrics (
    id BIGSERIAL PRIMARY KEY,
    metric_name VARCHAR(255) NOT NULL,
    metric_value DECIMAL(20,6) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    tags JSONB
);

CREATE INDEX IF NOT EXISTS idx_performance_name_time ON performance_metrics (metric_name, timestamp);
CREATE INDEX IF NOT EXISTS idx_performance_timestamp ON performance_metrics (timestamp);

-- Event store statistics view
CREATE OR REPLACE VIEW event_store_stats AS
SELECT 
    COUNT(*) as total_events,
    COUNT(DISTINCT stream_id) as total_streams,
    COUNT(DISTINCT event_type) as total_event_types,
    MIN(timestamp) as oldest_event,
    MAX(timestamp) as newest_event,
    AVG(EXTRACT(EPOCH FROM (timestamp - LAG(timestamp) OVER (ORDER BY timestamp)))) as avg_interval_seconds
FROM events;

-- Stream statistics view
CREATE OR REPLACE VIEW stream_stats AS
SELECT 
    stream_id,
    COUNT(*) as event_count,
    MAX(version) as current_version,
    MIN(timestamp) as first_event,
    MAX(timestamp) as last_event,
    COUNT(DISTINCT event_type) as event_types
FROM events 
GROUP BY stream_id
ORDER BY event_count DESC;

-- Performance monitoring functions
CREATE OR REPLACE FUNCTION record_performance_metric(
    p_metric_name VARCHAR(255),
    p_metric_value DECIMAL(20,6),
    p_metric_type VARCHAR(50),
    p_tags JSONB DEFAULT NULL
) RETURNS VOID AS $$
BEGIN
    INSERT INTO performance_metrics (metric_name, metric_value, metric_type, tags)
    VALUES (p_metric_name, p_metric_value, p_metric_type, p_tags);
END;
$$ LANGUAGE plpgsql;

-- Cleanup function for old performance metrics
CREATE OR REPLACE FUNCTION cleanup_old_metrics(days_to_keep INTEGER DEFAULT 30) RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM performance_metrics 
    WHERE timestamp < NOW() - INTERVAL '1 day' * days_to_keep;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;