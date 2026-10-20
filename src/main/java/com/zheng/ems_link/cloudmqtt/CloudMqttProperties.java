package com.zheng.ems_link.cloudmqtt;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("cloud-mqtt")
public class CloudMqttProperties {
    private boolean enabled;
    private String host = "";
    private int port = 8883;
    private boolean tls = true;
    private String clientId = "";
    private String telemetryTopicFilter = "ems/v1/+/telemetry/up";
    private String historyClientId = "";
    private String historyTopicFilter = "ems/v1/+/history";
    private String username = "";
    private String password = "";
    private int maxHistoryValues = 100_000;
    private int historyPageSize = 1_000;
    private int historyQueryTimeoutSeconds = 30;
    private int maxPublishBytes = 240 * 1024;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isTls() { return tls; }
    public void setTls(boolean tls) { this.tls = tls; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTelemetryTopicFilter() { return telemetryTopicFilter; }
    public void setTelemetryTopicFilter(String telemetryTopicFilter) { this.telemetryTopicFilter = telemetryTopicFilter; }
    public String getHistoryClientId() { return historyClientId; }
    public void setHistoryClientId(String historyClientId) { this.historyClientId = historyClientId; }
    public String getHistoryTopicFilter() { return historyTopicFilter; }
    public void setHistoryTopicFilter(String historyTopicFilter) { this.historyTopicFilter = historyTopicFilter; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getMaxHistoryValues() { return maxHistoryValues; }
    public void setMaxHistoryValues(int maxHistoryValues) { this.maxHistoryValues = maxHistoryValues; }
    public int getHistoryPageSize() { return historyPageSize; }
    public void setHistoryPageSize(int historyPageSize) { this.historyPageSize = historyPageSize; }
    public int getHistoryQueryTimeoutSeconds() { return historyQueryTimeoutSeconds; }
    public void setHistoryQueryTimeoutSeconds(int historyQueryTimeoutSeconds) { this.historyQueryTimeoutSeconds = historyQueryTimeoutSeconds; }
    public int getMaxPublishBytes() { return maxPublishBytes; }
    public void setMaxPublishBytes(int maxPublishBytes) { this.maxPublishBytes = maxPublishBytes; }
}
