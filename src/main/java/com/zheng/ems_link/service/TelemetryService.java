package com.zheng.ems_link.service;


import com.zheng.ems_link.domain.Telemetry;
import com.zheng.ems_link.repository.TelemetryRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Singleton
public class TelemetryService {

    private final TelemetryRepository repository;

    public TelemetryService(TelemetryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void store(byte[] rawPayload) {
        if (rawPayload.length > 64 * 1024) {
            throw new IllegalArgumentException("MQTT payload exceeds 64 KiB");
        }

        String json = new String(rawPayload, StandardCharsets.UTF_8);

        // 练习阶段先固定；正式项目应解析 JSON 并校验字段。
        Telemetry telemetry = new Telemetry();
        telemetry.setDeviceId("unknown");
        telemetry.setEventType("telemetry");
        telemetry.setPayload(json);
        telemetry.setReceivedAt(Instant.now());

        repository.save(telemetry);
    }
}