/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tests.integration.data.jdbc.contract;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.tests.integration.data.jdbc.declarative.StreamingRepository;
import io.helidon.tests.integration.data.jdbc.support.TestConfigFactory;
import io.helidon.tests.integration.data.jdbc.support.TestDataSourceFactory;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxSupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies query streaming with real drivers and a constrained application owned pool.
 */
public abstract class AbstractJdbcStreamingContract {
    private static final String QUERY = "SELECT DATA_VALUE FROM ST_VALUE ORDER BY ID";
    private static final int ROWS = 64;

    private ServiceRegistryManager manager;
    private HikariDataSource pool;
    private JdbcClient client;
    private TxSupport transactions;

    protected abstract Config databaseConfig();

    protected abstract boolean declarative();

    protected abstract void verifyDriverFetching(Connection connection) throws Exception;

    @BeforeEach
    protected final void startStreamingContract() throws Exception {
        Config config = databaseConfig().get("data.clients.jdbc.0.connection");
        HikariConfig settings = new HikariConfig();
        String url = config.get("url").asString().get();
        settings.setJdbcUrl(url);
        settings.setUsername(config.get("username").asString().get());
        settings.setPassword(config.get("password").asString().get());
        settings.setMaximumPoolSize(2);
        settings.setConnectionTimeout(1500);
        if (url.startsWith("jdbc:postgresql:")) {
            settings.addDataSourceProperty("defaultRowFetchSize", "16");
        } else if (url.startsWith("jdbc:mysql:")) {
            settings.addDataSourceProperty("useCursorFetch", "true");
            settings.addDataSourceProperty("defaultFetchSize", "16");
        } else {
            settings.addDataSourceProperty("defaultRowPrefetch", "16");
        }
        pool = new HikariDataSource(settings);
        TestDataSourceFactory.dataSource("stream-pool", pool);
        TestConfigFactory.config(Config.just(ConfigSources.create(Map.of(
                "data.clients.jdbc.0.name", "@default", "data.clients.jdbc.0.data-source", "stream-pool"))));
        manager = ServiceRegistryManager.start();
        client = manager.registry().get(JdbcClient.class, Qualifier.createNamed("@default"),
                Qualifier.builder().typeName(Data.ProviderType.TYPE).value("jdbc").build());
        transactions = manager.registry().get(TxSupport.class);
        client.create("CREATE TABLE ST_VALUE (ID INTEGER PRIMARY KEY, DATA_VALUE VARCHAR(512))").execute();
        for (int index = 0; index < ROWS; index++) {
            client.create("INSERT INTO ST_VALUE (ID, DATA_VALUE) VALUES (?, ?)")
                    .bind(1, index).bind(2, "value" + index).execute();
        }
        try (Connection connection = pool.getConnection()) {
            System.out.println("Streaming driver: " + connection.getMetaData().getDriverName() + " "
                    + connection.getMetaData().getDriverVersion() + ". Database: "
                    + connection.getMetaData().getDatabaseProductVersion() + ". Fetch size: 16. Rows: " + ROWS + ".");
        }
    }

    @AfterEach
    protected final void stopStreamingContract() {
        try {
            if (client != null) {
                client.create("DROP TABLE ST_VALUE").execute();
            }
        } finally {
            try {
                if (manager != null) {
                    manager.shutdown();
                }
            } finally {
                if (pool != null) {
                    pool.close();
                }
                TestDataSourceFactory.reset();
                TestConfigFactory.reset();
            }
        }
    }

    /**
     * Full, partial, absent and explicit closure return both pool slots for later work.
     */
    @Test
    protected final void consumesAllTraversalModesAndRecoversPool() {
        for (int mode = 0; mode < 4; mode++) {
            int selected = mode;
            consume(stream -> {
                switch (selected) {
                case 0 -> assertThat(stream.toList().size(), is(ROWS));
                case 1 -> assertThat(stream.limit(1).toList(), is(List.of("value0")));
                case 2 -> { }
                case 3 -> stream.close();
                default -> throw new AssertionError();
                }
            });
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThat(client.create("SELECT COUNT(*) FROM ST_VALUE").map(Long.class).one(), is((long) ROWS));
        }
    }

    /**
     * The provider maps one row for a short circuit even when the driver fetches a block.
     */
    @Test
    protected final void mapsLazilyAndExpiresRetainedViews() {
        AtomicInteger mapped = new AtomicInteger();
        client.create(QUERY).map(row -> {
            mapped.incrementAndGet();
            return row.get(1, String.class);
        }).useStream(stream -> assertThat(stream.findFirst().orElseThrow(), is("value0")));
        assertThat(mapped.get(), is(1));
        AtomicReference<Iterator<String>> iterator = new AtomicReference<>();
        consume(stream -> iterator.set(stream.iterator()));
        assertThrows(IllegalStateException.class, () -> iterator.get().hasNext());
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    /**
     * Off-thread access fails before JDBC while the original callback remains usable.
     */
    @Test
    protected final void rejectsOffThreadReads() {
        consume(stream -> {
            var source = stream.spliterator();
            CompletableFuture<Void> offThread = CompletableFuture.runAsync(() ->
                    assertThrows(IllegalStateException.class, () -> source.tryAdvance(_ -> { })));
            try {
                offThread.get(5, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            } finally {
                offThread.cancel(true);
            }
            assertThat(source.tryAdvance(_ -> { }), is(true));
        });
    }

    /**
     * Caught mapper failures remain failures and leave a reusable pool connection.
     */
    @Test
    protected final void retainsCaughtFailuresAndCloseHandlerFailures() {
        IllegalArgumentException mapping = new IllegalArgumentException("Mapping failed.");
        assertThat(assertThrows(IllegalArgumentException.class, () -> client.create(QUERY).map(_ -> {
            throw mapping;
        }).useStream(stream -> assertThrows(IllegalArgumentException.class, stream::count))), sameInstance(mapping));
        IllegalStateException closing = new IllegalStateException("Closing failed.");
        assertThat(assertThrows(IllegalStateException.class, () -> consume(stream -> {
            stream.onClose(() -> { throw closing; });
            assertThrows(IllegalStateException.class, stream::close);
        })), sameInstance(closing));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        assertThat(client.create(QUERY).map(String.class).list().size(), is(ROWS));
    }

    /**
     * Exhausted and closed streams retain reservations until the terminal returns.
     */
    @Test
    protected final void preservesTransactionReservationAndRollbackSignal() {
        transactions.transaction(Tx.Type.REQUIRED, () -> {
            for (int mode = 0; mode < 3; mode++) {
                int selected = mode;
                consume(stream -> {
                    switch (selected) {
                    case 0 -> { }
                    case 1 -> assertThat(stream.count(), is((long) ROWS));
                    case 2 -> stream.close();
                    default -> throw new AssertionError();
                    }
                    assertThrows(IllegalStateException.class, () -> client.create(QUERY).map(String.class).list());
                    assertThrows(IllegalStateException.class, () -> transactions.transaction(Tx.Type.NEW, () -> null));
                    assertThrows(IllegalStateException.class, () -> transactions.transaction(Tx.Type.UNSUPPORTED, () -> null));
                    JdbcClient independent = JdbcClient.builder().dataSource(pool).build();
                    assertThat(independent.create("SELECT COUNT(*) FROM ST_VALUE").map(Long.class).one(), is((long) ROWS));
                });
            }
            assertThat(client.create(QUERY).map(String.class).list().size(), is(ROWS));
            return null;
        });
        assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
            client.create("INSERT INTO ST_VALUE (ID, DATA_VALUE) VALUES (1000, 'rollback')").execute();
            assertThrows(DataException.class, () -> client.create(QUERY).map(_ -> null)
                    .useStream(stream -> assertThrows(DataException.class, stream::count)));
            return null;
        }));
        // This operation acquires independently after transaction completion and observes durable state.
        assertThat(client.create("SELECT COUNT(*) FROM ST_VALUE WHERE ID = 1000").map(Long.class).one(), is(0L));
        IllegalArgumentException closing = new IllegalArgumentException("Closing failed within the transaction.");
        assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
            client.create("INSERT INTO ST_VALUE (ID, DATA_VALUE) VALUES (1001, 'rollback')").execute();
            assertThat(assertThrows(IllegalArgumentException.class, () -> consume(stream -> {
                stream.onClose(() -> { throw closing; });
                assertThrows(IllegalArgumentException.class, stream::close);
            })), sameInstance(closing));
            return null;
        }));
        assertThat(client.create("SELECT COUNT(*) FROM ST_VALUE WHERE ID = 1001").map(Long.class).one(), is(0L));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    /**
     * The configured driver retains its fetching controls without hidden transaction changes.
     */
    @Test
    protected final void verifiesConfiguredFetchingAndRecordMapping() throws Exception {
        int total = ROWS;
        for (int round = 0; round < 6; round++) {
            client.create("INSERT INTO ST_VALUE (ID, DATA_VALUE) SELECT ID + ?, DATA_VALUE FROM ST_VALUE")
                    .bind(1, total).execute();
            total *= 2;
        }
        client.create("UPDATE ST_VALUE SET DATA_VALUE = ?").bind(1, "x".repeat(256)).execute();
        try (Connection connection = pool.getConnection()) {
            verifyDriverFetching(connection);
            assertThat(connection.getAutoCommit(), is(true));
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(QUERY)) {
                assertThat(statement.getFetchSize(), is(16));
                assertThat(statement.getResultSetType(), is(ResultSet.TYPE_FORWARD_ONLY));
                try (var result = statement.executeQuery()) {
                    int count = 0;
                    while (result.next()) {
                        count++;
                    }
                    assertThat(count, is(total));
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
        // These bounded measurements describe this workload and impose no portable performance threshold.
        long materializedNanos = 0;
        long streamingNanos = 0;
        for (int iteration = 0; iteration < 8; iteration++) {
            long started = System.nanoTime();
            int count = transactions.transaction(Tx.Type.REQUIRED,
                    () -> client.create(QUERY).map(String.class).list().size());
            long materializedElapsed = System.nanoTime() - started;
            assertThat(count, is(total));
            started = System.nanoTime();
            AtomicInteger streamed = new AtomicInteger();
            transactions.transaction(Tx.Type.REQUIRED, () -> {
                client.create(QUERY).map(String.class).useStream(stream -> streamed.set(Math.toIntExact(stream.count())));
                return null;
            });
            long streamingElapsed = System.nanoTime() - started;
            assertThat(streamed.get(), is(total));
            if (iteration >= 3) {
                materializedNanos += materializedElapsed;
                streamingNanos += streamingElapsed;
            }
        }
        System.out.println("Streaming measurement: 4096 rows, 256 characters, fetch size 16, local transaction, "
                + "three warmups and five samples. Mean materialized nanoseconds: " + materializedNanos / 5
                + ". Mean streaming nanoseconds: " + streamingNanos / 5 + ".");
        if (declarative()) {
            manager.registry().get(StreamingRepository.class).records(stream -> {
                var value = stream.findFirst().orElseThrow();
                assertThat(value.id(), is(1));
                assertThat(value.value(), is("x".repeat(256)));
            }, 1);
            manager.registry().get(StreamingRepository.class).mapped(stream -> {
                assertThat(stream.findFirst().orElseThrow().id(), is(0));
                assertThrows(IllegalStateException.class, () -> client.create(QUERY).map(String.class).list());
            });
        }
    }

    /**
     * Independent acquisitions preserve pool timeouts and recover after the streaming terminal returns.
     */
    @Test
    protected final void recoversAfterNestedIndependentPoolExhaustion() throws Exception {
        try (Connection held = pool.getConnection()) {
            assertThat(held.getAutoCommit(), is(true));
            consume(stream -> {
                assertThrows(DataException.class, () -> client.create(QUERY).map(String.class).list());
                assertThat(stream.findFirst().orElseThrow(), is("value0"));
            });
        }
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        assertThat(client.create(QUERY).map(String.class).list().size(), is(ROWS));
    }

    /**
     * Buffered iterator failures cannot be hidden by callback catches or a transaction boundary.
     */
    @Test
    protected final void retainsIteratorProcessingFailuresAndRollsBack() {
        for (int kind = 0; kind < 4; kind++) {
            int selected = kind;
            NoSuchElementException failure = new NoSuchElementException("Buffered processing failed.");
            Runnable operation = () -> consume(stream -> {
                Iterator<?> iterator = switch (selected) {
                case 0 -> stream.sorted((_, _) -> { throw failure; }).iterator();
                case 1 -> stream.mapToInt(String::length).sorted().map(_ -> { throw failure; }).iterator();
                case 2 -> stream.mapToLong(String::length).sorted().map(_ -> { throw failure; }).iterator();
                case 3 -> stream.mapToDouble(String::length).sorted().map(_ -> { throw failure; }).iterator();
                default -> throw new AssertionError();
                };
                assertThat(assertThrows(NoSuchElementException.class, iterator::next), sameInstance(failure));
            });
            assertThat(assertThrows(NoSuchElementException.class, operation::run), sameInstance(failure));
            assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                client.create("INSERT INTO ST_VALUE (ID, DATA_VALUE) VALUES (2000, 'rollback')").execute();
                assertThat(assertThrows(NoSuchElementException.class, operation::run), sameInstance(failure));
                return null;
            }));
            assertThat(client.create("SELECT COUNT(*) FROM ST_VALUE WHERE ID = 2000").map(Long.class).one(), is(0L));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThat(client.create(QUERY).map(String.class).list().size(), is(ROWS));
        }
    }

    /**
     * A caught column lookup failure still rolls back real writes and releases the pooled connection.
     */
    @Test
    protected final void retainsCaughtColumnLookupFailuresAndRollsBack() {
        for (boolean optional : new boolean[] {false, true}) {
            AtomicReference<DataException> observed = new AtomicReference<>();
            Runnable operation = () -> client.create(QUERY).map(row -> {
                observed.set(assertThrows(DataException.class, () -> {
                    if (optional) {
                        row.optional("MISSING_COLUMN", String.class);
                    } else {
                        row.get("MISSING_COLUMN", String.class);
                    }
                }));
                assertThrows(IllegalStateException.class, () -> row.get(1, String.class));
                assertThrows(IllegalStateException.class, () -> row.optional("DATA_VALUE", String.class));
                return "fallback";
            }).useStream(stream -> {
                Iterator<String> iterator = stream.iterator();
                assertThrows(IllegalStateException.class, iterator::next);
                assertThrows(IllegalStateException.class, iterator::hasNext);
            });
            assertThat(assertThrows(DataException.class, operation::run), sameInstance(observed.get()));
            assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                client.create("INSERT INTO ST_VALUE (ID, DATA_VALUE) VALUES (3000, 'rollback')").execute();
                assertThat(assertThrows(DataException.class, operation::run), sameInstance(observed.get()));
                return null;
            }));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            // This independent operation observes the committed state after transaction completion.
            assertThat(client.create("SELECT COUNT(*) FROM ST_VALUE WHERE ID = 3000").map(Long.class).one(), is(0L));
            assertThat(client.create(QUERY).map(String.class).list().size(), is(ROWS));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    /**
     * Comparator metadata supports JDK reconstruction while genuine exhaustion remains recoverable.
     */
    @Test
    protected final void preservesSortedSpliteratorMetadataAndIteratorExhaustion() {
        for (int kind = 0; kind < 4; kind++) {
            int selected = kind;
            consume(stream -> {
                Spliterator<?> source = switch (selected) {
                case 0 -> stream.sorted().spliterator();
                case 1 -> stream.mapToInt(String::length).sorted().spliterator();
                case 2 -> stream.mapToLong(String::length).sorted().spliterator();
                case 3 -> stream.mapToDouble(String::length).sorted().spliterator();
                default -> throw new AssertionError();
                };
                assertThat(source.hasCharacteristics(Spliterator.SORTED), is(true));
                assertThat(source.getComparator(), nullValue());
                assertThat(source.trySplit(), nullValue());
                assertThat(StreamSupport.stream(source, false).count(), is((long) ROWS));
            });
            transactions.transaction(Tx.Type.REQUIRED, () -> {
                consume(stream -> {
                    Iterator<?> iterator = switch (selected) {
                    case 0 -> stream.iterator();
                    case 1 -> stream.mapToInt(String::length).iterator();
                    case 2 -> stream.mapToLong(String::length).iterator();
                    case 3 -> stream.mapToDouble(String::length).iterator();
                    default -> throw new AssertionError();
                    };
                    for (int index = 0; index < ROWS; index++) {
                        iterator.next();
                    }
                    assertThrows(NoSuchElementException.class, iterator::next);
                    assertThrows(NoSuchElementException.class, iterator::next);
                });
                return null;
            });
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    private void consume(Consumer<Stream<String>> action) {
        if (declarative()) {
            manager.registry().get(StreamingRepository.class).values(null, action);
        } else {
            client.create(QUERY).map(String.class).useStream(action);
        }
    }
}
