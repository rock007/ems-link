package com.zheng.ems_link.cloudmqtt.record;

import com.zheng.ems_link.cloudmqtt.HistoryErrorCode;

import java.util.List;

public record HistoryQueryOutcome(HistoryErrorCode errorCode, List<HistoryResponseMessage> pages) {
   public   static HistoryQueryOutcome success(List<HistoryResponseMessage> pages) {
        return new HistoryQueryOutcome(null, pages);
    }

   public   static HistoryQueryOutcome error(HistoryErrorCode errorCode, String projectId) {
        return new HistoryQueryOutcome(errorCode, List.of(HistoryResponseMessage.error(projectId, 1, errorCode.message())));
    }

   public boolean success() {
        return errorCode == null;
    }
}
