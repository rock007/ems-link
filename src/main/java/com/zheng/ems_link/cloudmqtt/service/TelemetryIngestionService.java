package com.zheng.ems_link.cloudmqtt.service;

import com.zheng.ems_link.cloudmqtt.record.TelemetryMessage;
import com.zheng.ems_link.cloudmqtt.store.TelemetryStore;
import jakarta.inject.Singleton;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Singleton
public class TelemetryIngestionService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final TelemetryStore store;

    public TelemetryIngestionService(TelemetryStore store) {
        this.store = store;
    }

    public void ingest(String topic, TelemetryMessage message, byte[] rawPayload) {
        if (message == null || blank(message.projectId()) || message.projectId().matches(".*[/+#].*")) {
            throw new IllegalArgumentException("Invalid telemetry projectId");
        }
        validateTopic(topic, message.projectId());
        validate(message);
        store.save(message, rawPayload);
    }

    private static void validateTopic(String topic, String projectId) {
        String expected = "ems/v1/" + projectId + "/telemetry/up";
        if (!expected.equals(topic)) throw new IllegalArgumentException("MQTT topic projectId does not match payload");
    }

    private static void validate(TelemetryMessage message) {
        if (message == null || blank(message.projectId()) || (!"realtime".equals(message.fileType()) && !"full".equals(message.fileType()))
                || message.data() == null || message.data().isEmpty()) {
            throw new IllegalArgumentException("Invalid telemetry message envelope");
        }
        for (TelemetryMessage.DeviceSample sample : message.data()) {
            if (sample == null || blank(sample.type()) || blank(sample.id()) || sample.keys() == null || sample.keys().isEmpty()) {
                throw new IllegalArgumentException("Invalid device sample");
            }
            try {
                LocalDateTime.parse(sample.time(), TIME_FORMAT);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("Sample time must use yyyy-MM-dd HH:mm:ss.SSS", exception);
            }
            for (TelemetryMessage.Measurement measurement : sample.keys()) {
                if (measurement == null || blank(measurement.key()) || measurement.value() == null) {
                    throw new IllegalArgumentException("Invalid measurement");
                }
            }
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
