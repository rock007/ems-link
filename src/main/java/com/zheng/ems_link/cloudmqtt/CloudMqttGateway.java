package com.zheng.ems_link.cloudmqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.zheng.ems_link.cloudmqtt.record.TelemetryMessage;
import com.zheng.ems_link.cloudmqtt.service.TelemetryIngestionService;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

/** Cloud-platform MQTT 5 consumer for station telemetry. */
@Context
@Requires(property = "cloud-mqtt.enabled", value = "true")
public class CloudMqttGateway {
    private final CloudMqttProperties properties;
    private final JsonMapper jsonMapper;
    private final TelemetryIngestionService ingestionService;
    private Mqtt5AsyncClient client;

    public CloudMqttGateway(CloudMqttProperties properties, JsonMapper jsonMapper,
                            TelemetryIngestionService ingestionService) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.ingestionService = ingestionService;
    }

    @PostConstruct
    void connect() {
        if (properties.getHost().isBlank() || properties.getClientId().isBlank()) {
            throw new IllegalStateException("cloud-mqtt.host and cloud-mqtt.client-id must be configured");
        }
        var builder = Mqtt5Client.builder().identifier(properties.getClientId()).serverHost(properties.getHost())
                .serverPort(properties.getPort()).automaticReconnectWithDefaultConfig();
        if (properties.isTls()) builder.sslWithDefaultConfig();
        if (!properties.getUsername().isBlank()) {
            builder.simpleAuth().username(properties.getUsername())
                    .password(properties.getPassword().getBytes(StandardCharsets.UTF_8)).applySimpleAuth();
        }
        client = builder.buildAsync();
        client.connectWith().cleanStart(false).sessionExpiryInterval(86_400).keepAlive(30).send()
                .thenCompose(ignored -> client.subscribeWith().topicFilter(properties.getTelemetryTopicFilter())
                        .qos(MqttQos.AT_MOST_ONCE).callback(this::onTelemetry).send())
                .join();
    }

    private void onTelemetry(com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish publish) {
        try {
            if(publish.getCorrelationData().isPresent()){

                String correlationDataStr=  new String( publish.getCorrelationData().get().array());
                System.getLogger(CloudMqttGateway.class.getName()).log(System.Logger.Level.DEBUG,"correlationData:"+correlationDataStr);
            }

            byte[] payload = publish.getPayloadAsBytes();
            TelemetryMessage message = jsonMapper.readValue(payload, TelemetryMessage.class);
            ingestionService.ingest(publish.getTopic().toString(), message, payload);
        } catch (Exception exception) {
            // QoS 0 has no retry acknowledgement. The callback must stay alive for the next message.
            System.getLogger(CloudMqttGateway.class.getName()).log(System.Logger.Level.ERROR,
                    "Discarded invalid telemetry MQTT message", exception);
        }
    }

    @PreDestroy
    void disconnect() {
        if (client != null) client.disconnect();
    }
}
