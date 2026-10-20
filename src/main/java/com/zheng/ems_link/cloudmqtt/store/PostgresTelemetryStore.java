package com.zheng.ems_link.cloudmqtt.store;

import com.zheng.ems_link.cloudmqtt.record.TelemetryMessage;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Singleton
class PostgresTelemetryStore implements TelemetryStore, HistoryDataStore {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String INSERT_MESSAGE = """
            INSERT INTO mqtt_telemetry_message (project_id, file_type, payload)
            VALUES (?, ?, CAST(? AS jsonb)) RETURNING id
            """;
    private static final String UPSERT_POINT = """
            INSERT INTO telemetry_point (message_id, project_id, device_type, device_id, sampled_at, point_key, point_value)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (project_id, device_type, device_id, sampled_at, point_key)
            DO UPDATE SET message_id = EXCLUDED.message_id, point_value = EXCLUDED.point_value, received_at = now()
            """;
    private final DataSource dataSource;

    PostgresTelemetryStore(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public void save(TelemetryMessage message, byte[] rawPayload) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long messageId = insertMessage(connection, message, rawPayload);
                insertPoints(connection, message, messageId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to persist telemetry data", exception);
        }
    }

    private long insertMessage(Connection connection, TelemetryMessage message, byte[] rawPayload) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MESSAGE)) {
            statement.setString(1, message.projectId());
            statement.setString(2, message.fileType());
            statement.setString(3, new String(rawPayload, StandardCharsets.UTF_8));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Telemetry message insert did not return an id");
                return result.getLong(1);
            }
        }
    }

    private void insertPoints(Connection connection, TelemetryMessage message, long messageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_POINT)) {
            for (TelemetryMessage.DeviceSample sample : message.data()) {
                Timestamp sampledAt = Timestamp.valueOf(LocalDateTime.parse(sample.time(), TIME_FORMAT));
                for (TelemetryMessage.Measurement measurement : sample.keys()) {
                    statement.setLong(1, messageId);
                    statement.setString(2, message.projectId());
                    statement.setString(3, sample.type());
                    statement.setString(4, sample.id());
                    statement.setTimestamp(5, sampledAt);
                    statement.setString(6, measurement.key());
                    statement.setBigDecimal(7, measurement.value());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    @Override
    public boolean hasType(String projectId, String type) {
        return exists("""
                SELECT 1 FROM telemetry_point WHERE project_id = ? AND device_type = ? LIMIT 1
                """, projectId, type);
    }

    @Override
    public Set<String> deviceIds(String projectId, String type) {
        return distinct("""
                SELECT DISTINCT device_id FROM telemetry_point WHERE project_id = ? AND device_type = ?
                """, projectId, type);
    }

    @Override
    public Set<String> keys(String projectId, String type) {
        return distinct("""
                SELECT DISTINCT point_key FROM telemetry_point WHERE project_id = ? AND device_type = ?
                """, projectId, type);
    }

    @Override
    public List<HistoryPoint> query(String projectId, String type, Collection<String> deviceIds,
                                    Collection<String> keys, LocalDateTime start, LocalDateTime end, int limit) {
        String sql = """
                SELECT device_type, device_id, sampled_at, point_key, point_value
                FROM telemetry_point
                WHERE project_id = ? AND device_type = ?
                  AND point_key = ANY(?)
                  AND sampled_at >= ? AND sampled_at <= ?
                """ + (deviceIds == null || deviceIds.isEmpty() ? "" : " AND device_id = ANY(?)") + """
                
                ORDER BY sampled_at ASC, device_id ASC, point_key ASC
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, projectId);
            statement.setString(index++, type);
            Array keyArray = connection.createArrayOf("varchar", keys.toArray());
            statement.setArray(index++, keyArray);
            statement.setTimestamp(index++, Timestamp.valueOf(start));
            statement.setTimestamp(index++, Timestamp.valueOf(end));
            if (deviceIds != null && !deviceIds.isEmpty()) {
                statement.setArray(index++, connection.createArrayOf("varchar", deviceIds.toArray()));
            }
            statement.setInt(index, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<HistoryPoint> points = new ArrayList<>();
                while (result.next()) {
                    points.add(new HistoryPoint(
                            result.getString("device_type"),
                            result.getString("device_id"),
                            result.getTimestamp("sampled_at").toLocalDateTime(),
                            result.getString("point_key"),
                            result.getBigDecimal("point_value")));
                }
                return points;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query history data", exception);
        }
    }

    private boolean exists(String sql, String projectId, String type) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectId);
            statement.setString(2, type);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query history catalog", exception);
        }
    }

    private Set<String> distinct(String sql, String projectId, String type) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectId);
            statement.setString(2, type);
            try (ResultSet result = statement.executeQuery()) {
                Set<String> values = new LinkedHashSet<>();
                while (result.next()) {
                    values.add(result.getString(1));
                }
                return values;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query history catalog", exception);
        }
    }
}
