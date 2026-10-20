package com.zheng.ems_link.api;

import com.zheng.ems_link.repository.TelemetryRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/telemetry")
public class TelemetryController {

    private final TelemetryRepository repository;

    public TelemetryController(TelemetryRepository repository) {
        this.repository = repository;
    }

    @Get
    public Iterable<?> list() {
        return repository.findAll();
    }
}