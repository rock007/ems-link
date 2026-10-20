
create table telemetry (
    id bigserial primary key,
    device_id varchar(100) not null,
    event_type varchar(50) not null,
    payload jsonb not null,
    received_at timestamptz not null
);

create index idx_telemetry_device_received_at
    on telemetry (device_id, received_at desc);
    
CREATE TABLE mqtt_telemetry_message (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL,
    file_type VARCHAR(16) NOT NULL CHECK (file_type IN ('realtime', 'full')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload JSONB NOT NULL
);

CREATE INDEX idx_mqtt_telemetry_message_project_received
    ON mqtt_telemetry_message (project_id, received_at DESC);

CREATE TABLE telemetry_point (
    message_id BIGINT NOT NULL REFERENCES mqtt_telemetry_message(id),
    project_id VARCHAR(128) NOT NULL,
    device_type VARCHAR(128) NOT NULL,
    device_id VARCHAR(256) NOT NULL,
    sampled_at TIMESTAMP(3) NOT NULL,
    point_key VARCHAR(256) NOT NULL,
    point_value NUMERIC NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, device_type, device_id, sampled_at, point_key)
);

CREATE INDEX idx_telemetry_point_query
    ON telemetry_point (project_id, device_type, device_id, point_key, sampled_at DESC);

    
    