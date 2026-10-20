package com.zheng.ems_link.cloudmqtt.service;

import com.zheng.ems_link.cloudmqtt.*;
import com.zheng.ems_link.cloudmqtt.record.HistoryQueryMessage;
import com.zheng.ems_link.cloudmqtt.record.HistoryQueryOutcome;
import com.zheng.ems_link.cloudmqtt.record.HistoryResponseMessage;
import com.zheng.ems_link.cloudmqtt.record.TelemetryMessage;
import com.zheng.ems_link.cloudmqtt.store.HistoryDataStore;
import io.micronaut.json.JsonMapper;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class HistoryQueryService {
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int MAX_KEYS = 5;

    private final HistoryDataStore store;
    private final CloudMqttProperties properties;
    private final JsonMapper jsonMapper;
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor(thread -> {
        Thread worker = new Thread(thread, "history-query");
        worker.setDaemon(true);
        return worker;
    });

    public HistoryQueryService(HistoryDataStore store, CloudMqttProperties properties, JsonMapper jsonMapper) {
        this.store = store;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public HistoryQueryOutcome execute(String topic, HistoryQueryMessage message) {
        String projectId = projectIdFrom(topic, message);
        try {
            return queryExecutor.submit(() -> executeNow(topic, message, projectId))
                    .get(Math.max(1, properties.getHistoryQueryTimeoutSeconds()), TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            return HistoryQueryOutcome.error(HistoryErrorCode.QUERY_TIMEOUT, projectId);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return HistoryQueryOutcome.error(HistoryErrorCode.INTERNAL_ERROR, projectId);
        } catch (ExecutionException execution) {
            return mapFailure(projectId, execution.getCause());
        }
    }

    private HistoryQueryOutcome executeNow(String topic, HistoryQueryMessage message, String projectId) {
        try {
            HistoryQueryMessage.Query query = validate(topic, message, projectId);
            if (!store.hasType(projectId, query.type())) {
                return HistoryQueryOutcome.error(HistoryErrorCode.INVALID_TYPE, projectId);
            }
            Set<String> catalogDevices = store.deviceIds(projectId, query.type());
            Set<String> catalogKeys = store.keys(projectId, query.type());
            Collection<String> deviceIds = resolveDevices(query.ids(), catalogDevices);
            validateKeys(query.keys(), catalogKeys);
            int limit = properties.getMaxHistoryValues() + 1;
            List<HistoryDataStore.HistoryPoint> points = store.query(
                    projectId, query.type(), deviceIds, query.keys(),
                    LocalDateTime.parse(query.startTime(), TIME_FORMAT),
                    LocalDateTime.parse(query.endTime(), TIME_FORMAT),
                    limit);
            if (points.size() > properties.getMaxHistoryValues()) {
                return HistoryQueryOutcome.error(HistoryErrorCode.RESULT_COUNT_EXCEEDED, projectId);
            }
            return HistoryQueryOutcome.success(paginate(projectId, group(points)));
        } catch (HistoryRejected rejected) {
            return HistoryQueryOutcome.error(rejected.code, projectId);
        }
    }

    private HistoryQueryMessage.Query validate(String topic, HistoryQueryMessage message, String projectId) {
        if (blank(projectId) || projectId.matches(".*[/+#].*") || message == null || message.query() == null) {
            throw new HistoryRejected(HistoryErrorCode.INVALID_REQUEST);
        }
        if (!("ems/v1/" + projectId + "/history").equals(topic)
                || (message.projectId() != null && !projectId.equals(message.projectId()))) {
            throw new HistoryRejected(HistoryErrorCode.INVALID_REQUEST);
        }
        HistoryQueryMessage.Query query = message.query();
        if (blank(query.type()) || query.keys() == null || query.keys().isEmpty()
                || blank(query.startTime()) || blank(query.endTime())) {
            throw new HistoryRejected(HistoryErrorCode.INVALID_REQUEST);
        }
        if (query.keys().size() > MAX_KEYS) {
            throw new HistoryRejected(HistoryErrorCode.TOO_MANY_KEYS);
        }
        if (hasBlankOrDuplicate(query.keys())) {
            throw new HistoryRejected(HistoryErrorCode.INVALID_KEY);
        }
        if (query.ids() != null && hasBlankOrDuplicate(query.ids())) {
            throw new HistoryRejected(HistoryErrorCode.INVALID_REQUEST);
        }
        LocalDateTime start;
        LocalDateTime end;
        try {
            start = LocalDateTime.parse(query.startTime(), TIME_FORMAT);
            end = LocalDateTime.parse(query.endTime(), TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new HistoryRejected(HistoryErrorCode.INVALID_TIME_RANGE);
        }
        if (end.isBefore(start) || end.isAfter(start.plus(1, ChronoUnit.MONTHS))) {
            throw new HistoryRejected(HistoryErrorCode.INVALID_TIME_RANGE);
        }
        return query;
    }

    private Collection<String> resolveDevices(List<String> requested, Set<String> catalogDevices) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        if (!catalogDevices.containsAll(requested)) {
            throw new HistoryRejected(HistoryErrorCode.DEVICE_MISMATCH);
        }
        return requested;
    }

    private static void validateKeys(List<String> requested, Set<String> catalogKeys) {
        Map<String, List<String>> byLower = new LinkedHashMap<>();
        for (String catalogKey : catalogKeys) {
            byLower.computeIfAbsent(catalogKey.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(catalogKey);
        }
        for (String key : requested) {
            List<String> matches = byLower.getOrDefault(key.toLowerCase(Locale.ROOT), List.of());
            if (matches.size() > 1) {
                throw new HistoryRejected(HistoryErrorCode.AMBIGUOUS_KEY);
            }
            if (!catalogKeys.contains(key)) {
                throw new HistoryRejected(HistoryErrorCode.INVALID_KEY);
            }
        }
    }

    private List<HistoryResponseMessage> paginate(String projectId, List<TelemetryMessage.DeviceSample> samples)
            throws HistoryRejected {
        if (samples.isEmpty()) {
            return List.of(HistoryResponseMessage.page(projectId, 1, true, List.of()));
        }
        List<HistoryResponseMessage> pages = new ArrayList<>();
        List<TelemetryMessage.DeviceSample> current = new ArrayList<>();
        int currentKeys = 0;
        int pageNo = 1;
        for (TelemetryMessage.DeviceSample sample : samples) {
            int sampleKeys = sample.keys().size();
            List<TelemetryMessage.DeviceSample> candidate = new ArrayList<>(current);
            candidate.add(sample);
            if (!current.isEmpty() && (currentKeys + sampleKeys > properties.getHistoryPageSize()
                    || oversized(HistoryResponseMessage.page(projectId, pageNo, false, candidate)))) {
                pages.add(HistoryResponseMessage.page(projectId, pageNo++, false, List.copyOf(current)));
                current = new ArrayList<>();
                currentKeys = 0;
            }
            current.add(sample);
            currentKeys += sampleKeys;
            if (oversized(HistoryResponseMessage.page(projectId, pageNo, false, current))) {
                throw new HistoryRejected(HistoryErrorCode.INTERNAL_ERROR);
            }
        }
        pages.add(HistoryResponseMessage.page(projectId, pageNo, true, List.copyOf(current)));
        return pages;
    }

    private boolean oversized(HistoryResponseMessage page) {
        try {
            return jsonMapper.writeValueAsBytes(page).length > properties.getMaxPublishBytes();
        } catch (IOException exception) {
            throw new HistoryRejected(HistoryErrorCode.INTERNAL_ERROR);
        }
    }

    private static List<TelemetryMessage.DeviceSample> group(List<HistoryDataStore.HistoryPoint> points) {
        Map<String, TelemetryMessage.DeviceSample> samples = new LinkedHashMap<>();
        for (HistoryDataStore.HistoryPoint point : points) {
            String stamp = point.sampledAt().format(TIME_FORMAT);
            String groupKey = point.id() + '\n' + stamp;
            TelemetryMessage.DeviceSample sample = samples.computeIfAbsent(groupKey,
                    ignored -> new TelemetryMessage.DeviceSample(point.type(), point.id(), stamp, new ArrayList<>()));
            sample.keys().add(new TelemetryMessage.Measurement(point.key(), point.value()));
        }
        return new ArrayList<>(samples.values());
    }

    private HistoryQueryOutcome mapFailure(String projectId, Throwable cause) {
        if (cause instanceof HistoryRejected rejected) {
            return HistoryQueryOutcome.error(rejected.code, projectId);
        }
        if (cause instanceof IllegalStateException) {
            return HistoryQueryOutcome.error(HistoryErrorCode.DATA_UNAVAILABLE, projectId);
        }
        return HistoryQueryOutcome.error(HistoryErrorCode.INTERNAL_ERROR, projectId);
    }

   public   static String projectIdFrom(String topic, HistoryQueryMessage message) {
        if (message != null && !blank(message.projectId())) {
            return message.projectId();
        }
        if (topic == null) {
            return "";
        }
        String[] parts = topic.split("/");
        if (parts.length == 4 && "ems".equals(parts[0]) && "v1".equals(parts[1]) && "history".equals(parts[3])) {
            return parts[2];
        }
        return "";
    }

    private static boolean hasBlankOrDuplicate(List<String> values) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (blank(value) || !seen.add(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @PreDestroy
    void shutdown() {
        queryExecutor.shutdownNow();
    }

    private static final class HistoryRejected extends RuntimeException {
        private final HistoryErrorCode code;

        private HistoryRejected(HistoryErrorCode code) {
            super(code.message());
            this.code = code;
        }
    }
}
