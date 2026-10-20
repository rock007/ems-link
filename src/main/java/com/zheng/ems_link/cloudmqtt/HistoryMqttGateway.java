package com.zheng.ems_link.cloudmqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperty;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.zheng.ems_link.cloudmqtt.record.HistoryQueryMessage;
import com.zheng.ems_link.cloudmqtt.record.HistoryQueryOutcome;
import com.zheng.ems_link.cloudmqtt.service.HistoryQueryService;
import com.zheng.ems_link.cloudmqtt.record.HistoryResponseMessage;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Data-provider MQTT 5 client for history query request/response on the shared history topic. */
@Context
@Requires(property = "cloud-mqtt.enabled", value = "true")
@Requires(property = "cloud-mqtt.history-client-id", pattern = ".+")
public class HistoryMqttGateway {
    private static final System.Logger LOG = System.getLogger(HistoryMqttGateway.class.getName());

    private final CloudMqttProperties properties;
    private final JsonMapper jsonMapper;
    private final HistoryQueryService queryService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(thread -> {
        Thread worker = new Thread(thread, "mqtt-history");
        worker.setDaemon(true);
        return worker;
    });
    private Mqtt5AsyncClient client;

    public HistoryMqttGateway(CloudMqttProperties properties, JsonMapper jsonMapper,
                              HistoryQueryService queryService) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.queryService = queryService;
    }

    @PostConstruct
    void connect() {
        if (properties.getHost().isBlank() || properties.getHistoryClientId().isBlank()) {
            throw new IllegalStateException("cloud-mqtt.host and cloud-mqtt.history-client-id must be configured");
        }
        if (properties.getHistoryClientId().equals(properties.getClientId())) {
            throw new IllegalStateException("historyClientId must be different from the telemetry upload clientId");
        }
        var builder = Mqtt5Client.builder().identifier(properties.getHistoryClientId())
                .serverHost(properties.getHost()).serverPort(properties.getPort())
                .automaticReconnectWithDefaultConfig();
        if (properties.isTls()) {
            builder.sslWithDefaultConfig();
        }
        if (!properties.getUsername().isBlank()) {
            builder.simpleAuth().username(properties.getUsername())
                    .password(properties.getPassword().getBytes(StandardCharsets.UTF_8)).applySimpleAuth();
        }
        client = builder.buildAsync();
        client.connectWith().cleanStart(false).sessionExpiryInterval(86_400).keepAlive(30).send()
                .thenCompose(ignored -> client.subscribeWith().topicFilter(properties.getHistoryTopicFilter())
                        .qos(MqttQos.AT_MOST_ONCE).callback(this::onHistory).send())
                .join();
    }

    private void onHistory(Mqtt5Publish publish) {
        executor.execute(() -> handle(publish));
    }

    private void handle(Mqtt5Publish publish) {
        if (hasStatus(publish) || publish.getCorrelationData().isEmpty()) {
            return;
        }
        byte[] correlation = bytes(publish.getCorrelationData().orElseThrow());
        String topic = publish.getTopic().toString();
        String responseTopic = publish.getResponseTopic().map(MqttUtf8String::toString).orElse(topic);
        try {
            HistoryQueryMessage message = jsonMapper.readValue(publish.getPayloadAsBytes(), HistoryQueryMessage.class);
            if (message != null && "history".equals(message.fileType())) {
                return;
            }
            HistoryQueryOutcome outcome = queryService.execute(topic, message);
            publishOutcome(responseTopic, correlation, outcome);
        } catch (Exception exception) {
            LOG.log(System.Logger.Level.ERROR, "Failed to handle history query", exception);
            String projectId = HistoryQueryService.projectIdFrom(topic, null);
            publishOutcome(responseTopic, correlation,
                    HistoryQueryOutcome.error(HistoryErrorCode.INVALID_REQUEST, projectId));
        }
    }

    private void publishOutcome(String responseTopic, byte[] correlation, HistoryQueryOutcome outcome) {
        try {
            for (HistoryResponseMessage page : outcome.pages()) {
                var publish = client.publishWith()
                        .topic(responseTopic)
                        .qos(MqttQos.AT_MOST_ONCE)
                        .payload(jsonMapper.writeValueAsBytes(page))
                        .correlationData(correlation)
                        .userProperties()
                        .add("status", outcome.success() ? "SUCCESS" : "ERROR");
                if (!outcome.success()) {
                    publish.add("code", outcome.errorCode().name());
                }
                publish.applyUserProperties().send().join();
            }
        } catch (Exception exception) {
            LOG.log(System.Logger.Level.ERROR, "Failed to publish history query response", exception);
        }
    }

    private static boolean hasStatus(Mqtt5Publish publish) {
        for (Mqtt5UserProperty property : publish.getUserProperties().asList()) {
            if ("status".equals(property.getName().toString())) {
                return true;
            }
        }
        return false;
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer view = buffer.duplicate();
        byte[] value = new byte[view.remaining()];
        view.get(value);
        return value;
    }

    @PreDestroy
    void disconnect() {
        executor.shutdownNow();
        if (client != null) {
            client.disconnect();
        }
    }
}
