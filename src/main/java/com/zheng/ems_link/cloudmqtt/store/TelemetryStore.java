package com.zheng.ems_link.cloudmqtt.store;

import com.zheng.ems_link.cloudmqtt.record.TelemetryMessage;

public interface TelemetryStore {
    void save(TelemetryMessage message, byte[] rawPayload);
}
