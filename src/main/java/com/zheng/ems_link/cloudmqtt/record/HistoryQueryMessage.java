package com.zheng.ems_link.cloudmqtt.record;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record HistoryQueryMessage(String projectId, String fileType, Query query) {
    @Serdeable
    public record Query(String type, List<String> ids, List<String> keys, String startTime, String endTime) { }
}
