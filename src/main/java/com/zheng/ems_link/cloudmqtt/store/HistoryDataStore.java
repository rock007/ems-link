package com.zheng.ems_link.cloudmqtt.store;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface HistoryDataStore {
    boolean hasType(String projectId, String type);

    Set<String> deviceIds(String projectId, String type);

    Set<String> keys(String projectId, String type);

    List<HistoryPoint> query(String projectId, String type, Collection<String> deviceIds,
                             Collection<String> keys, LocalDateTime start, LocalDateTime end, int limit);

    record HistoryPoint(String type, String id, LocalDateTime sampledAt, String key, BigDecimal value) { }
}
