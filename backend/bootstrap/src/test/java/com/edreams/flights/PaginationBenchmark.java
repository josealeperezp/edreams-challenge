package com.edreams.flights;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Offset versus keyset over the real inventory. Not a unit test: excluded by default, run on demand
 * against the compose database after an ingestion, ideally at MOCK_INVENTORY_SIZE=1000000:
 * <pre>mvn -pl bootstrap -am test -Dsurefire.excludedGroups= -Dgroups=benchmark -Dtest=PaginationBenchmark -Dsurefire.failIfNoSpecifiedTests=false</pre>
 * Prints a Markdown table meant for SOLUTION.md.
 */
@Tag("benchmark")
class PaginationBenchmark {

    private static final String URL = System.getProperty("bench.url", "jdbc:postgresql://localhost:15432/flights");
    private static final String USER = System.getProperty("bench.user", "flights");
    private static final String PASSWORD = System.getProperty("bench.password", "flights");
    private static final int PAGE = 20;
    private static final int ITERATIONS = 30;

    @Test
    void offsetVersusKeyset() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            long total = count(c, "SELECT count(*) FROM itinerary");
            System.out.printf(Locale.ROOT, "%n### Pagination benchmark, %,d itineraries, page size %d, %d iterations%n%n", total, PAGE, ITERATIONS);
            System.out.println("| Filter | Page | Offset p50 ms | Offset p99 ms | Keyset p50 ms | Keyset p99 ms |");
            System.out.println("|---|---|---|---|---|---|");
            for (int page : new int[] {1, 100, 1_000, 10_000}) {
                report(c, "none", "", page);
            }
            for (int page : new int[] {1, 10, 50}) {
                report(c, "MAD-BCN", " AND origin = 'MAD' AND destination = 'BCN'", page);
            }
        }
    }

    private static void report(Connection c, String label, String where, int page) throws SQLException {
        int offset = (page - 1) * PAGE;
        String base = "SELECT id, price FROM itinerary WHERE 1 = 1" + where;
        String offsetSql = base + " ORDER BY price, id LIMIT " + PAGE + " OFFSET " + offset;
        Cursor cursor = cursorAt(c, base, offset);
        if (cursor == null) {
            System.out.printf(Locale.ROOT, "| %s | %d | n/a (fewer rows) | | | |%n", label, page);
            return;
        }
        String keysetSql = base + " AND (price, id) > (?, ?) ORDER BY price, id LIMIT " + PAGE;
        List<Double> offsetMs = time(c, offsetSql, null);
        List<Double> keysetMs = time(c, keysetSql, cursor);
        System.out.printf(Locale.ROOT, "| %s | %d | %.2f | %.2f | %.2f | %.2f |%n", label, page,
                percentile(offsetMs, 50), percentile(offsetMs, 99), percentile(keysetMs, 50), percentile(keysetMs, 99));
    }

    private record Cursor(BigDecimal price, long id) {
    }

    /** The keyset position before the requested page, found once with OFFSET and not timed. */
    private static Cursor cursorAt(Connection c, String base, int offset) throws SQLException {
        if (offset == 0) {
            return new Cursor(BigDecimal.ZERO, 0L);
        }
        try (PreparedStatement ps = c.prepareStatement(base + " ORDER BY price, id LIMIT 1 OFFSET " + (offset - 1));
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? new Cursor(rs.getBigDecimal("price"), rs.getLong("id")) : null;
        }
    }

    private static List<Double> time(Connection c, String sql, Cursor cursor) throws SQLException {
        List<Double> samples = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < ITERATIONS + 3; i++) {
                if (cursor != null) {
                    ps.setBigDecimal(1, cursor.price());
                    ps.setLong(2, cursor.id());
                }
                long start = System.nanoTime();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rs.getLong(1);
                    }
                }
                if (i >= 3) {
                    samples.add((System.nanoTime() - start) / 1_000_000.0);
                }
            }
        }
        return samples;
    }

    private static double percentile(List<Double> samples, int p) {
        List<Double> sorted = new ArrayList<>(samples);
        sorted.sort(null);
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
