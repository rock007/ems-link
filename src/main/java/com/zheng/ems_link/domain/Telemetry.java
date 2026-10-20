package com.zheng.ems_link.domain;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

@MappedEntity("telemetry")
public class Telemetry {

    @Id
    private Long id;
    private String deviceId;
    private String eventType;
    private String payload;
    private Instant receivedAt;
    
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getDeviceId() {
		return deviceId;
	}
	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}
	public String getEventType() {
		return eventType;
	}
	public void setEventType(String eventType) {
		this.eventType = eventType;
	}
	public String getPayload() {
		return payload;
	}
	public void setPayload(String payload) {
		this.payload = payload;
	}
	public Instant getReceivedAt() {
		return receivedAt;
	}
	public void setReceivedAt(Instant receivedAt) {
		this.receivedAt = receivedAt;
	}

    // 无参构造、getter、setter
    
}