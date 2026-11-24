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
package io.helidon.tests.integration.data.jdbc.imperative.mysql;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.sql.DriverManager;
import java.sql.JDBCType;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcBatchContract;
import io.helidon.tests.integration.data.jdbc.support.TestConfigFactory;
import io.helidon.transaction.Tx;

import com.sun.management.ThreadMXBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Executes the imperative batch contract against MySQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlImperativeBatchTest extends AbstractJdbcBatchContract {
    @Container
    static final MySQLContainer<?> MYSQL = MySqlImperativeTestSupport.MYSQL;

    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(MySqlImperativeTestSupport.config());
    }
    @Override
    protected List<String> committedAfterPartialFailure() {
        return List.of("batch-before", "batch-after");
    }

    /**
     * A pool with one connection remains available after prepared capture and real driver failures.
     */
    @Test
    void preparedBatchesReturnTheOnlyPoolConnectionAfterSuccessAndFailure() {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(MYSQL.getJdbcUrl());
        configuration.setUsername(MYSQL.getUsername());
        configuration.setPassword(MYSQL.getPassword());
        configuration.setMaximumPoolSize(1);
        configuration.setConnectionTimeout(1_000);
        Config sql = Config.just(ConfigSources.classpath("configured-batches/reordered/application.yaml"));
        try (HikariDataSource pool = new HikariDataSource(configuration)) {
            JdbcClient client = JdbcClient.builder().dataSource(pool).build();
            var definition = client.prepare(sql.get("application.batches.insert").asString().get(), List.of("name", "email"));
            assertThat(definition.get().executeBatch(List.of("pooled-success"),
                    (bindings, name) -> bindings.bind("name", name).bindNull("email", JDBCType.VARCHAR)).entries().size(), is(1));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));

            assertThat(definition.get().executeBatch(List.of("pooled-sized-one", "pooled-sized-two", "pooled-sized-three"), 2,
                    (bindings, name) -> bindings.bind("name", name).bindNull("email", JDBCType.VARCHAR)).entries().size(), is(3));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThrows(IllegalArgumentException.class, () -> definition.get().executeBatch(List.of(0, 1, 2), 2,
                    (bindings, index) -> {
                        bindings.bind("name", "pooled-sized-capture").bindNull("email", JDBCType.VARCHAR);
                        if (index == 2) {
                            bindings.bind("name", "duplicate");
                        }
                    }));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            JdbcBatchException sizedFailure = assertThrows(JdbcBatchException.class,
                    () -> definition.get().executeBatch(List.of(0, 1, 2, 3, 4), 2, (bindings, index) -> {
                        if (index == 2) {
                            bindings.bindNull("name", JDBCType.VARCHAR);
                        } else {
                            bindings.bind("name", "pooled-sized-" + index);
                        }
                        bindings.bindNull("email", JDBCType.VARCHAR);
                    }));
            assertThat(sizedFailure.result().entries().get(4).status(), is(JdbcClient.BatchResult.Status.NOT_REPORTED));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            definition.get().executeBatch(List.of("pooled-sized-reused"), 2,
                    (bindings, name) -> bindings.bind("name", name).bindNull("email", JDBCType.VARCHAR));
            assertThat(client.create("SELECT COUNT(*) FROM CONTACT WHERE NAME = 'pooled-sized-reused'")
                               .map(Long.class).one(), is(1L));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThrows(IllegalArgumentException.class, () -> definition.get().executeBatch(List.of("capture"),
                    (bindings, name) -> bindings.bind("name", name).bind("name", name)));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> definition.get().executeBatch(List.of(0, 1), (bindings, index) -> {
                        if (index == 0) {
                            bindings.bind("name", "pooled-before-failure");
                        } else {
                            bindings.bindNull("name", JDBCType.VARCHAR);
                        }
                        bindings.bindNull("email", JDBCType.VARCHAR);
                    }));
            assertThat(failure.result().entries().size(), is(2));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            definition.get().executeBatch(List.of("pooled-reused"),
                    (bindings, name) -> bindings.bind("name", name).bindNull("email", JDBCType.VARCHAR));
            assertThat(client.create("SELECT COUNT(*) FROM CONTACT WHERE NAME = 'pooled-reused'").map(Long.class).one(), is(1L));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThat(pool.isClosed(), is(false));
        }
    }

    @Test
    void measuresSubmissionSizesWithFixedPayloadAndTransactionMode() throws Exception {
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertThat(threads.isThreadAllocatedMemorySupported(), is(true));
        threads.setThreadAllocatedMemoryEnabled(true);
        try (var metadataConnection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            var metadata = metadataConnection.getMetaData();
            System.out.printf("Batch measurement environment: Java=%s, database=%s, driver=%s, URL=%s, maxHeapBytes=%d, GC=%s.%n",
                    System.getProperty("java.version"), metadata.getDatabaseProductVersion(), metadata.getDriverVersion(),
                    MYSQL.getJdbcUrl(), Runtime.getRuntime().maxMemory(),
                    ManagementFactory.getGarbageCollectorMXBeans().stream().map(bean -> bean.getName()).toList());
        }
        List<Integer> items = IntStream.range(0, 512).boxed().toList();
        List<String> values = items.stream().map(index -> "%048d".formatted(index)).toList();
        var definition = client().prepare("UPDATE CONTACT SET EMAIL = :email WHERE NAME = :name");
        JdbcClient.BatchBinder<Integer> binder = (bindings, index) -> {
            Objects.requireNonNull(bindings, "The batch bindings must not be null.");
            Objects.requireNonNull(index, "The batch index must not be null.");
            bindings.bind("email", values.get(index)).bind("name", "alpha");
        };
        var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP).toList();
        for (int size : new int[] {0, 1, 32, 128}) {
            for (int iteration = 0; iteration < 5; iteration++) {
                pools.forEach(pool -> pool.resetPeakUsage());
                long allocated = threads.getThreadAllocatedBytes(Thread.currentThread().threadId());
                long started = System.nanoTime();
                JdbcClient.BatchResult result = transactions().transaction(Tx.Type.REQUIRED, () -> size == 0
                        ? definition.get().executeBatch(items, binder) : definition.get().executeBatch(items, size, binder));
                long elapsed = System.nanoTime() - started;
                long bytes = threads.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
                assertThat(result.entries().size(), is(items.size()));
                if (iteration >= 2) {
                    long peakHeap = pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
                    System.out.printf("Batch measurement: size=%s, sample=%d, items=512, markers=2, emailBytes=48, nameBytes=5, "
                                    + "transaction=REQUIRED, elapsedNs=%d, allocatedBytes=%d, heapPoolPeakSumBytes=%d.%n",
                            size == 0 ? "unsized" : Integer.toString(size), iteration - 1, elapsed, bytes, peakHeap);
                }
            }
        }
    }

}
