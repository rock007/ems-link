package com.example.cloudmqtt;

import com.zheng.ems_link.cloudmqtt.CloudMqttProperties;
import com.zheng.ems_link.cloudmqtt.store.HistoryDataStore;
import com.zheng.ems_link.cloudmqtt.HistoryErrorCode;
import com.zheng.ems_link.cloudmqtt.record.HistoryQueryMessage;
import com.zheng.ems_link.cloudmqtt.record.HistoryQueryOutcome;
import com.zheng.ems_link.cloudmqtt.service.HistoryQueryService;
import com.zheng.ems_link.cloudmqtt.record.HistoryResponseMessage;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryQueryServiceTest {
    @Test
    void returnsEmptySuccessPageWhenNoPointsMatch() {
        MemoryStore store = catalog("pcs", Set.of("pcs001"), Set.of("Ua"));
        HistoryQueryOutcome outcome = service(store, 100_000, 1_000)
                .execute("ems/v1/prj01/history", request(List.of("pcs001"), List.of("Ua"),
                        "2023-05-11 17:07:00.000", "2023-05-12 17:07:00.000"));

        assertTrue(outcome.success());
        HistoryResponseMessage page = outcome.pages().getFirst();
        assertEquals("history", page.fileType());
        assertEquals(1, page.pageNo());
        assertTrue(page.isLast());
        assertTrue(page.data().isEmpty());
        assertNull(page.errorMsg());
    }

    @Test
    void pagesByMeasurementCount() {
        MemoryStore store = catalog("pcs", Set.of("pcs001"), Set.of("Ua"));
        store.points = List.of(
                point("pcs001", "2023-05-11T17:07:00", "Ua", "1"),
                point("pcs001", "2023-05-11T17:08:00", "Ua", "2"),
                point("pcs001", "2023-05-11T17:09:00", "Ua", "3"));
        HistoryQueryOutcome outcome = service(store, 100_000, 2)
                .execute("ems/v1/prj01/history", request(List.of("pcs001"), List.of("Ua"),
                        "2023-05-11 17:07:00.000", "2023-05-11 18:07:00.000"));

        assertTrue(outcome.success());
        assertEquals(2, outcome.pages().size());
        assertEquals(2, outcome.pages().get(0).data().size());
        assertEquals(false, outcome.pages().get(0).isLast());
        assertEquals(1, outcome.pages().get(1).data().size());
        assertTrue(outcome.pages().get(1).isLast());
        assertEquals(2, outcome.pages().get(1).pageNo());
    }

    @Test
    void rejectsMoreThanFiveKeys() {
        MemoryStore store = catalog("pcs", Set.of("pcs001"), Set.of("a", "b", "c", "d", "e", "f"));
        HistoryQueryOutcome outcome = service(store, 100_000, 1_000)
                .execute("ems/v1/prj01/history", request(List.of("pcs001"),
                        List.of("a", "b", "c", "d", "e", "f"),
                        "2023-05-11 17:07:00.000", "2023-05-12 17:07:00.000"));

        assertEquals(HistoryErrorCode.TOO_MANY_KEYS, outcome.errorCode());
        assertTrue(outcome.pages().getFirst().isLast());
        assertEquals(HistoryErrorCode.TOO_MANY_KEYS.message(), outcome.pages().getFirst().errorMsg());
    }

    @Test
    void rejectsTimeRangeLongerThanOneMonth() {
        MemoryStore store = catalog("pcs", Set.of("pcs001"), Set.of("Ua"));
        HistoryQueryOutcome outcome = service(store, 100_000, 1_000)
                .execute("ems/v1/prj01/history", request(List.of("pcs001"), List.of("Ua"),
                        "2023-05-11 17:07:00.000", "2023-06-12 17:07:00.000"));

        assertEquals(HistoryErrorCode.INVALID_TIME_RANGE, outcome.errorCode());
    }

    @Test
    void rejectsResultCountAboveConfiguredCap() {
        MemoryStore store = catalog("pcs", Set.of("pcs001"), Set.of("Ua"));
        store.points = List.of(
                point("pcs001", "2023-05-11T17:07:00", "Ua", "1"),
                point("pcs001", "2023-05-11T17:08:00", "Ua", "2"));
        HistoryQueryOutcome outcome = service(store, 1, 1_000)
                .execute("ems/v1/prj01/history", request(List.of("pcs001"), List.of("Ua"),
                        "2023-05-11 17:07:00.000", "2023-05-12 17:07:00.000"));

        assertEquals(HistoryErrorCode.RESULT_COUNT_EXCEEDED, outcome.errorCode());
    }

    @Test
    void rejectsUnknownTypeAndDeviceAndKey() {
        MemoryStore store = catalog("pcs", Set.of("pcs001"), Set.of("Ua"));
        HistoryQueryService service = service(store, 100_000, 1_000);

        assertEquals(HistoryErrorCode.INVALID_TYPE, service.execute("ems/v1/prj01/history",
                new HistoryQueryMessage("prj01", null, new HistoryQueryMessage.Query("bms", List.of("pcs001"),
                        List.of("Ua"), "2023-05-11 17:07:00.000", "2023-05-12 17:07:00.000"))).errorCode());
        assertEquals(HistoryErrorCode.DEVICE_MISMATCH, service.execute("ems/v1/prj01/history",
                request(List.of("pcs999"), List.of("Ua"),
                        "2023-05-11 17:07:00.000", "2023-05-12 17:07:00.000")).errorCode());
        assertEquals(HistoryErrorCode.INVALID_KEY, service.execute("ems/v1/prj01/history",
                request(List.of("pcs001"), List.of("Ub"),
                        "2023-05-11 17:07:00.000", "2023-05-12 17:07:00.000")).errorCode());
    }

    private static HistoryQueryService service(HistoryDataStore store, int maxValues, int pageSize) {
        CloudMqttProperties properties = new CloudMqttProperties();
        properties.setMaxHistoryValues(maxValues);
        properties.setHistoryPageSize(pageSize);
        properties.setHistoryQueryTimeoutSeconds(5);
        properties.setMaxPublishBytes(240 * 1024);
        return new HistoryQueryService(store, properties, ObjectMapper.getDefault());
    }

    private static HistoryQueryMessage request(List<String> ids, List<String> keys, String start, String end) {
        return new HistoryQueryMessage("prj01", null, new HistoryQueryMessage.Query("pcs", ids, keys, start, end));
    }

    private static MemoryStore catalog(String type, Set<String> devices, Set<String> keys) {
        MemoryStore store = new MemoryStore();
        store.type = type;
        store.devices = devices;
        store.keys = keys;
        return store;
    }

    private static HistoryDataStore.HistoryPoint point(String id, String time, String key, String value) {
        return new HistoryDataStore.HistoryPoint("pcs", id, LocalDateTime.parse(time), key, new BigDecimal(value));
    }

    private static final class MemoryStore implements HistoryDataStore {
        private String type;
        private Set<String> devices = Set.of();
        private Set<String> keys = Set.of();
        private List<HistoryPoint> points = List.of();

        @Override
        public boolean hasType(String projectId, String deviceType) {
            return type.equals(deviceType);
        }

        @Override
        public Set<String> deviceIds(String projectId, String deviceType) {
            return devices;
        }

        @Override
        public Set<String> keys(String projectId, String deviceType) {
            return keys;
        }

        @Override
        public List<HistoryPoint> query(String projectId, String deviceType, Collection<String> deviceIds,
                                        Collection<String> queryKeys, LocalDateTime start, LocalDateTime end, int limit) {
            List<HistoryPoint> matched = new ArrayList<>();
            for (HistoryPoint point : points) {
                if (matched.size() == limit) {
                    break;
                }
                matched.add(point);
            }
            return matched;
        }
    }
}
