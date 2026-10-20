package com.zheng.ems_link.cloudmqtt.record;

import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import java.util.List;

@Serdeable
public record TelemetryMessage(String projectId, String fileType, List<DeviceSample> data) {
    @Serdeable
    public record DeviceSample(String type, String id, String time, List<Measurement> keys) { }
    @Serdeable
    public record Measurement(String key, BigDecimal value) { }
}
