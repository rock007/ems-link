package com.example.cloudmqtt;

import org.junit.jupiter.api.Test;

import com.zheng.ems_link.cloudmqtt.service.TelemetryIngestionService;
import com.zheng.ems_link.cloudmqtt.record.TelemetryMessage;
import com.zheng.ems_link.cloudmqtt.store.TelemetryStore;

import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelemetryIngestionServiceTest {
    @Test
    void persistsAValidMessage() {
        CapturingStore store = new CapturingStore();
        TelemetryMessage message = sampleMessage();

        new TelemetryIngestionService(store).ingest("ems/v1/prj01/telemetry/up", message, "{}".getBytes());

        assertEquals(message, store.message);
    }

    @Test
    void rejectsTopicAndPayloadProjectMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new TelemetryIngestionService(new CapturingStore())
                .ingest("ems/v1/other/telemetry/up", sampleMessage(), "{}".getBytes()));
    }

    private static TelemetryMessage sampleMessage() {
        return new TelemetryMessage("prj01", "realtime", List.of(
                new TelemetryMessage.DeviceSample("pcs", "pcs001", "2026-10-19 21:00:00.000",
                        List.of(new TelemetryMessage.Measurement("Ua", new BigDecimal("748.6"))))));
    }

    private static final class CapturingStore implements TelemetryStore {
        private TelemetryMessage message;
        @Override public void save(TelemetryMessage message, byte[] rawPayload) { this.message = message; }
    }
}
