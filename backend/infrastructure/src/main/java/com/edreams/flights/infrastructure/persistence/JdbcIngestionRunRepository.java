package com.edreams.flights.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.edreams.flights.application.ingestion.IngestionRun;
import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.port.out.IngestionRunRepository;
import com.edreams.flights.infrastructure.json.JsonCodec;
import com.edreams.flights.infrastructure.json.RunJson;

@Repository
public class JdbcIngestionRunRepository implements IngestionRunRepository {

    private static final String SELECT = "SELECT id, started_at, finished_at, status, provider_results, itineraries, error FROM ingestion_run";

    private final JdbcClient jdbc;
    private final JsonCodec json;

    public JdbcIngestionRunRepository(DataSource dataSource, JsonCodec json) {
        this.jdbc = JdbcClient.create(dataSource);
        this.json = json;
    }

    @Override
    public void save(IngestionRun run) {
        jdbc.sql("""
                INSERT INTO ingestion_run (id, started_at, finished_at, status, provider_results, itineraries, error)
                VALUES (:id, :startedAt, :finishedAt, :status, :results::jsonb, :itineraries, :error)
                ON CONFLICT (id) DO UPDATE SET finished_at = EXCLUDED.finished_at, status = EXCLUDED.status,
                    provider_results = EXCLUDED.provider_results, itineraries = EXCLUDED.itineraries, error = EXCLUDED.error
                """)
                .param("id", run.id().value())
                .param("startedAt", Timestamp.from(run.startedAt()))
                .param("finishedAt", run.finishedAt() == null ? null : Timestamp.from(run.finishedAt()))
                .param("status", run.status().name())
                .param("results", json.writeProviderResults(RunJson.providersFrom(run.providers())))
                .param("itineraries", run.itineraries())
                .param("error", run.error())
                .update();
    }

    @Override
    public Optional<IngestionRun> find(IngestionRunId id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id.value()).query(this::map).optional();
    }

    @Override
    public Optional<IngestionRun> latest() {
        return latest(1).stream().findFirst();
    }

    @Override
    public List<IngestionRun> latest(int limit) {
        return jdbc.sql(SELECT + " ORDER BY started_at DESC LIMIT :limit").param("limit", limit).query(this::map).list();
    }

    private IngestionRun map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp finished = rs.getTimestamp("finished_at");
        Integer itineraries = rs.getObject("itineraries", Integer.class);
        return new IngestionRun(
                new IngestionRunId(rs.getObject("id", UUID.class)),
                rs.getTimestamp("started_at").toInstant(),
                finished == null ? null : finished.toInstant(),
                IngestionRun.Status.valueOf(rs.getString("status")),
                RunJson.providersTo(json.readProviderResults(rs.getString("provider_results"))),
                itineraries,
                rs.getString("error"));
    }
}
