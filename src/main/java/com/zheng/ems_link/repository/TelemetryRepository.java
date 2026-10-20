package com.zheng.ems_link.repository;


import com.zheng.ems_link.domain.Telemetry;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface TelemetryRepository
        extends CrudRepository<Telemetry, Long> {
}