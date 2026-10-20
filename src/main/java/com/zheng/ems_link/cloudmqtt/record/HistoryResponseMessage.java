package com.zheng.ems_link.cloudmqtt.record;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryResponseMessage(
        String projectId,
        String fileType,
        int pageNo,
        boolean isLast,
        List<TelemetryMessage.DeviceSample> data,
        String errorMsg
) {
    public static HistoryResponseMessage page(String projectId, int pageNo, boolean isLast,
                                       List<TelemetryMessage.DeviceSample> data) {
        return new HistoryResponseMessage(projectId, "history", pageNo, isLast, data, null);
    }

    public static HistoryResponseMessage error(String projectId, int pageNo, String errorMsg) {
        return new HistoryResponseMessage(projectId, "history", pageNo, true, List.of(), errorMsg);
    }
}
