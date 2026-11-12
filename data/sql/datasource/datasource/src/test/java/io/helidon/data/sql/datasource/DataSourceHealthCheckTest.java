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
package io.helidon.data.sql.datasource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.sql.DataSource;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class DataSourceHealthCheckTest {
    /**
     * Keeps policy defaults, configuration, owner builders, and copies consistent without resolving a data source.
     */
    @Test
    void healthPolicyDefaultsAndOwnerBuilderParity() {
        DataSourceHealthConfig defaults = DataSourceHealthConfig.create();
        assertThat(defaults.enabled(), is(false));
        assertThat(defaults.query(), is("SELECT 1"));
        assertThat(defaults.queryTimeout(), is(Duration.ofSeconds(2)));
        assertThat(defaults.type(), is(HealthCheckType.READINESS));
        assertThat(defaults.name().isEmpty(), is(true));

        String query = "SELECT 'POLICY_SQL_CANARY'";
        Config config = Config.just(ConfigSources.create(Map.of("name", "orders",
                "health.enabled", "true", "health.query", query, "health.query-timeout", "PT3S",
                "health.name", "source-db", "health.type", "liveness")));
        ProviderConfig provider = mock(ProviderConfig.class);
        var configured = DataSourceConfig.builder().config(config).provider(provider);
        var policy = DataSourceHealthConfig.builder().enabled(true).query(query).queryTimeout(Duration.ofSeconds(3))
                .name("source-db").type(HealthCheckType.LIVENESS);
        var typed = DataSourceConfig.builder().name("orders").provider(provider).health(policy.build());
        var nested = DataSourceConfig.builder().name("orders").provider(provider)
                .health(health -> health.enabled(true).query(query).queryTimeout(Duration.ofSeconds(3))
                        .name("source-db").type(HealthCheckType.LIVENESS));
        assertThat(DataSourceConfig.builder().provider(provider).build().health(), is(defaults));
        for (var builder : List.of(configured, typed, nested)) {
            DataSourceConfig owner = builder.build();
            assertThat(owner.health(), is(policy.build()));
            assertThat(DataSourceConfig.builder(owner).build(), is(owner));
            assertThat(DataSourceHealthConfig.builder(owner.health()).build(), is(owner.health()));
            assertThat(builder.toString(), not(containsString("POLICY_SQL_CANARY")));
            assertThat(owner.toString(), not(containsString("POLICY_SQL_CANARY")));
            assertThat(owner.health().query(), is(query));
        }
        assertThat(policy.toString(), containsString("query=****"));
        assertThat(policy.build().toString(), not(containsString("POLICY_SQL_CANARY")));
        assertThrows(NullPointerException.class, () -> DataSourceHealthConfig.create((Config) null));
        assertThrows(NullPointerException.class, () -> DataSourceHealthConfig.builder(null));
        assertThrows(NullPointerException.class, () -> policy.from((DataSourceHealthConfig) null));
        assertThrows(NullPointerException.class,
                     () -> policy.from((DataSourceHealthConfig.BuilderBase<?, ?>) null));
        assertThrows(NullPointerException.class, () -> policy.query(null));
        assertThrows(NullPointerException.class, () -> policy.queryTimeout(null));
        assertThrows(NullPointerException.class, () -> policy.type(null));
        assertThrows(NullPointerException.class, () -> policy.name((String) null));
        assertThrows(NullPointerException.class, () -> policy.config(null));
        assertThrows(NullPointerException.class, () -> typed.health((DataSourceHealthConfig) null));
        assertThrows(NullPointerException.class,
                     () -> typed.health((Consumer<DataSourceHealthConfig.Builder>) null));
    }

    /**
     * Rejects null configuration arguments without changing builder values or acquiring connections.
     */
    @Test
    void generatedConfigurationRejectsNullAndPreservesCopies() {
        DataSource source = mock(DataSource.class);
        var builder = DataSourceHealthCheck.builder().dataSource(source).query("SELECT 2").name("database");
        DataSourceHealthCheckConfig prototype = builder.buildPrototype();

        assertThrows(NullPointerException.class, () -> DataSourceHealthCheckConfig.builder(null));
        assertThrows(NullPointerException.class, () -> DataSourceHealthCheckConfig.create((Config) null));
        assertThrows(NullPointerException.class, () -> builder.from((DataSourceHealthCheckConfig) null));
        assertThrows(NullPointerException.class, () -> builder.from((DataSourceHealthCheckConfig.BuilderBase<?, ?>) null));
        assertThrows(NullPointerException.class, () -> builder.config(null));
        assertThrows(NullPointerException.class, () -> builder.dataSource(null));
        assertThrows(NullPointerException.class, () -> builder.query(null));
        assertThrows(NullPointerException.class, () -> builder.queryTimeout(null));
        assertThrows(NullPointerException.class, () -> builder.type(null));
        assertThrows(NullPointerException.class, () -> builder.name((String) null));

        assertThat(builder.buildPrototype(), is(prototype));
        assertThat(DataSourceHealthCheckConfig.builder(prototype).buildPrototype(), is(prototype));
        assertThat(DataSourceHealthCheckConfig.builder().from(builder).buildPrototype(), is(prototype));
        assertThat(prototype.dataSource(), sameInstance(source));
        verifyZeroInteractions(source);
    }

    /**
     * Validates SQL and timeout settings before accessing the data source.
     */
    @Test
    void validatesBeforeAcquisition() {
        DataSource source = mock(DataSource.class);
        for (String sql : List.of("", "WITH x AS (SELECT 1) SELECT * FROM x", "CALL x()", "EXPLAIN SELECT 1",
                                  "SELECTED 1", "SELECTé 1", "'SELECT'", "SELECT ?", "SELECT :value", "SELECT 1;;",
                                  "SELECT 1; SELECT 2", "SELECT 1; 'x'", "SELECT /* unfinished")) {
            assertThrows(IllegalArgumentException.class, () -> probe(source, sql, Duration.ofSeconds(2)));
        }
        for (Duration timeout : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(1500),
                                        Duration.ofSeconds((long) Integer.MAX_VALUE + 1))) {
            assertThrows(IllegalArgumentException.class, () -> probe(source, "SELECT 1", timeout));
        }
        for (String sql : List.of("SELECT 1", " /* comment */ select 1; -- end\n", "SELECT '?;:x'", "SELECT $$?;:x$$",
                                  "SELECT q'[?;:x]'", "SELECT nq'[?;:x]'", "SELECT `?`, \":x\"", "SELECT 1 /* ? */")) {
            try {
                probe(source, sql, Duration.ofSeconds(Integer.MAX_VALUE));
            } catch (IllegalArgumentException failure) {
                throw new AssertionError(sql, failure);
            }
        }
        assertThrows(NullPointerException.class, () -> probe(source, "", null));
        assertThrows(NullPointerException.class, () -> probe(null, "", Duration.ZERO));
        verifyZeroInteractions(source);
    }

    /**
     * Retains the supplied source and drains each fresh result without reading column values.
     */
    @Test
    void retainsActualResolvedSourceAndExecutesFreshEmptyAndNonemptyProbes() throws Exception {
        Fixture fixture = fixture();
        when(fixture.rows().next()).thenReturn(true, true, false);
        BooleanSupplier probe = probe(fixture.source(),
                                                             "SELECT 1;", Duration.ofSeconds(3));
        verifyZeroInteractions(fixture.source());
        assertThat(probe.getAsBoolean(), is(true));
        assertThat(probe.getAsBoolean(), is(true));
        verify(fixture.source(), times(2)).getConnection();
        verify(fixture.connection(), times(2)).prepareStatement("SELECT 1");
        InOrder execution = inOrder(fixture.statement());
        for (int invocation = 0; invocation < 2; invocation++) {
            execution.verify(fixture.statement()).setQueryTimeout(3);
            execution.verify(fixture.statement()).execute();
        }
        verify(fixture.rows(), times(4)).next();
        verify(fixture.rows(), times(2)).close();
        verifyNoMoreInteractions(fixture.rows());
        verify(fixture.statement(), times(2)).close();
        verify(fixture.connection(), times(2)).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Returns DOWN for driver failures and releases every resource already acquired by the probe.
     */
    @Test
    void containsEveryInfrastructureFailureAndAttemptsCleanup() throws Exception {
        for (String fault : List.of("acquire", "mode", "prepare", "timeout", "execute", "rows", "next", "rowClose",
                                    "more", "count", "statementClose", "connectionClose", "runtime")) {
            Fixture fixture = fixture();
            SQLException failure = new SQLException("Secret SQL and credentials", "08001", 123);
            switch (fault) {
                case "acquire" -> when(fixture.source().getConnection()).thenThrow(failure);
                case "mode" -> when(fixture.connection().getAutoCommit()).thenThrow(failure);
                case "prepare" -> when(fixture.connection().prepareStatement(anyString())).thenThrow(failure);
                case "timeout" -> doThrow(failure).when(fixture.statement()).setQueryTimeout(anyInt());
                case "execute" -> when(fixture.statement().execute()).thenThrow(failure);
                case "rows" -> when(fixture.statement().getResultSet()).thenThrow(failure);
                case "next" -> when(fixture.rows().next()).thenThrow(failure);
                case "rowClose" -> doThrow(failure).when(fixture.rows()).close();
                case "more" -> when(fixture.statement().getMoreResults(anyInt())).thenThrow(failure);
                case "count" -> when(fixture.statement().getUpdateCount()).thenThrow(failure);
                case "statementClose" -> doThrow(failure).when(fixture.statement()).close();
                case "connectionClose" -> doThrow(failure).when(fixture.connection()).close();
                case "runtime" -> when(fixture.rows().next()).thenThrow(new IllegalStateException("Secret driver state."));
                default -> throw new AssertionError(fault);
            }
            assertThat(fault, probe(fixture).getAsBoolean(), is(false));
            if (fault.equals("timeout")) {
                verify(fixture.statement(), never()).execute();
            }
            noTransactionControl(fixture.connection());
            if (List.of("mode", "rowClose", "statementClose", "connectionClose").contains(fault)) {
                verify(fixture.connection()).abort(any());
            }
            if (List.of("timeout", "execute", "rows", "next", "rowClose", "more", "count", "statementClose", "connectionClose")
                    .contains(fault)) {
                verify(fixture.statement()).close();
            }
        }
    }

    /**
     * Rejects unsafe connection modes and drains unexpected channels without changing transaction mode.
     */
    @Test
    void rejectsUnsafeModesAndWrongChannels() throws Exception {
        for (String fault : List.of("initialMode", "finalMode", "unreadableFinalMode", "noRows",
                                    "missingRows", "extraRows", "extraCount")) {
            Fixture fixture = fixture();
            switch (fault) {
                case "initialMode" -> when(fixture.connection().getAutoCommit()).thenReturn(false);
                case "finalMode" -> when(fixture.connection().getAutoCommit()).thenReturn(true, false);
                case "unreadableFinalMode" -> when(fixture.connection().getAutoCommit()).thenReturn(true)
                        .thenThrow(new SQLException("Secret mode diagnostic."));
                case "noRows" -> when(fixture.statement().execute()).thenReturn(false);
                case "missingRows" -> when(fixture.statement().getResultSet()).thenReturn(null);
                case "extraRows" -> when(fixture.statement().getMoreResults(anyInt())).thenReturn(true, false);
                case "extraCount" -> when(fixture.statement().getUpdateCount()).thenReturn(0, -1);
                default -> throw new AssertionError(fault);
            }
            assertThat(fault, probe(fixture).getAsBoolean(), is(false));
            if (fault.equals("initialMode") || fault.equals("finalMode") || fault.equals("unreadableFinalMode")) {
                verify(fixture.connection()).abort(any());
            }
            noTransactionControl(fixture.connection());
        }
    }

    /**
     * Preserves a fatal cleanup error over an earlier SQL failure and still releases the connection.
     */
    @Test
    void cleanupFatalErrorTakesPrecedenceOverSqlFailure() throws Exception {
        Fixture fixture = fixture();
        when(fixture.rows().next()).thenThrow(new SQLException("Secret"));
        Error fatal = new OutOfMemoryError("fatal");
        doThrow(fatal).when(fixture.statement()).close();
        assertThat(assertThrows(OutOfMemoryError.class, () -> probe(fixture).getAsBoolean()), sameInstance(fatal));
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Preserves a fatal abort error while attempting the close fallback without transaction recovery.
     */
    @Test
    void invalidationPreservesFatalErrorsAndStillAttemptsClose() throws Exception {
        Fixture fixture = fixture();
        when(fixture.connection().getAutoCommit()).thenReturn(false);
        Error fatal = new OutOfMemoryError("fatal");
        doThrow(fatal).when(fixture.connection()).abort(any());
        doThrow(new SQLException("Secret close diagnostic.")).when(fixture.connection()).close();
        assertThat(assertThrows(OutOfMemoryError.class, () -> probe(fixture).getAsBoolean()), sameInstance(fatal));
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Holds both invocations open together to prove that their connections and statements remain independent.
     */
    @Test
    void concurrentInvocationsOwnDistinctResources() throws Exception {
        Fixture first = fixture();
        Fixture second = fixture();
        when(first.source().getConnection()).thenReturn(first.connection(), second.connection());
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        for (Fixture fixture : List.of(first, second)) {
            when(fixture.statement().execute()).thenAnswer(_ -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new SQLException("The coordination wait expired.");
                }
                return true;
            });
        }
        var executor = Executors.newFixedThreadPool(2);
        var probe = probe(first);
        List<Future<Boolean>> workers = new ArrayList<>();
        Throwable primary = null;
        try {
            workers.add(executor.submit(probe::getAsBoolean));
            workers.add(executor.submit(probe::getAsBoolean));
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            release.countDown();
            for (Future<Boolean> worker : workers) {
                assertThat(worker.get(5, TimeUnit.SECONDS), is(true));
            }
            for (Fixture fixture : List.of(first, second)) {
                verify(fixture.connection()).close();
                noTransactionControl(fixture.connection());
            }
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            finishWorkers(release, workers, executor, primary);
        }
    }

    /**
     * Reports acquisition failure before waiting for the peer and releases the successful probe independently.
     */
    @Test
    void failedConcurrentProbeDoesNotStrandItsPeer() throws Exception {
        Fixture fixture = fixture();
        when(fixture.source().getConnection()).thenThrow(new SQLException("Secret acquisition failure."))
                .thenReturn(fixture.connection());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(fixture.statement().execute()).thenAnswer(_ -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new SQLException("The coordination wait expired.");
            }
            return true;
        });
        var executor = Executors.newFixedThreadPool(2);
        var completed = new ExecutorCompletionService<Boolean>(executor);
        BooleanSupplier probe = probe(fixture);
        List<Future<Boolean>> workers = new ArrayList<>();
        Throwable primary = null;
        try {
            workers.add(completed.submit(probe::getAsBoolean));
            workers.add(completed.submit(probe::getAsBoolean));
            Future<Boolean> failure = completed.poll(5, TimeUnit.SECONDS);
            assertThat(failure, notNullValue());
            assertThat(failure.get(5, TimeUnit.SECONDS), is(false));
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            release.countDown();
            Future<Boolean> success = completed.poll(5, TimeUnit.SECONDS);
            assertThat(success, notNullValue());
            assertThat(success.get(5, TimeUnit.SECONDS), is(true));
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            finishWorkers(release, workers, executor, primary);
        }
        verify(fixture.rows()).close();
        verify(fixture.statement()).close();
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
        assertThat(probe.getAsBoolean(), is(true));
    }

    /**
     * Verifies builder and configuration parity, redaction, and construction without acquisition.
     */
    @Test
    void defaultsConfigurationAndConfidentialRendering() {
        String query = "SELECT 'SQL_LITERAL_CANARY'";
        DataSource source = mock(DataSource.class);
        when(source.toString()).thenReturn("SOURCE_CREDENTIAL_CANARY");
        DataSourceHealthCheck defaults = DataSourceHealthCheck.builder().dataSource(source).build();
        assertThat(defaults.name(), is("DataSourceHealthCheck"));
        assertThat(defaults.path(), is(defaults.name()));
        assertThat(defaults.type(), is(HealthCheckType.READINESS));
        assertThat(defaults.prototype().query(), is("SELECT 1"));
        assertThat(defaults.prototype().queryTimeout(), is(Duration.ofSeconds(2)));
        Config config = Config.just(ConfigSources.create(Map.of("query", query, "name", "source-db",
                                                               "query-timeout", "PT3S", "type", "liveness")));
        var configured = DataSourceHealthCheck.builder().dataSource(source).config(config);
        var programmatic = DataSourceHealthCheck.builder().dataSource(source).query(query).name("source-db")
                .queryTimeout(Duration.ofSeconds(3)).type(HealthCheckType.LIVENESS);
        assertThat(configured.buildPrototype(), is(programmatic.buildPrototype()));
        for (var builder : List.of(configured, programmatic)) {
            assertThat(builder.toString(), containsString("query=****"));
            assertThat(builder.toString(), containsString("dataSource=****"));
            assertThat(builder.toString(), not(containsString("SQL_LITERAL_CANARY")));
            assertThat(builder.toString(), not(containsString("SOURCE_CREDENTIAL_CANARY")));
            var check = builder.build();
            assertThat(check.prototype().query(), is(query));
            assertThat(check.prototype().dataSource(), sameInstance(source));
            assertThat(check.prototype().toString(), not(containsString("SQL_LITERAL_CANARY")));
            assertThat(check.prototype().toString(), not(containsString("SOURCE_CREDENTIAL_CANARY")));
        }
        assertThrows(IllegalArgumentException.class, () -> DataSourceHealthCheck.builder().dataSource(source).name(" ").build());
        assertThrows(NullPointerException.class, () -> DataSourceHealthCheck.create((DataSourceHealthCheckConfig) null));
        assertThrows(NullPointerException.class,
                     () -> DataSourceHealthCheck.create((Consumer<DataSourceHealthCheckConfig.Builder>) null));
        assertThrows(NullPointerException.class, () -> DataSourceHealthCheck.builder().dataSource(null));
        assertThrows(Errors.ErrorMessagesException.class, () -> DataSourceHealthCheck.builder().build());
        verifyZeroInteractions(source);
    }

    /**
     * Preserves fatal error identity without attaching earlier confidential infrastructure diagnostics.
     */
    @Test
    void fatalInvalidationSanitizesEarlierFailure() throws Exception {
        Fixture fixture = fixture();
        when(fixture.connection().getAutoCommit()).thenThrow(new SQLException("SQL_CREDENTIAL_CANARY", "SECRET", 123));
        Error fatal = new OutOfMemoryError("fatal");
        doThrow(fatal).when(fixture.connection()).abort(any());
        assertThat(assertThrows(OutOfMemoryError.class, () -> probe(fixture).getAsBoolean()), sameInstance(fatal));
        assertThat(fatal.getSuppressed().length, is(1));
        assertThat(fatal.getSuppressed()[0].toString(), not(containsString("SQL_CREDENTIAL_CANARY")));
        assertThat(fatal.getSuppressed()[0].getCause(), is((Throwable) null));
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Avoids resolving data source services when discovery is disabled or absent.
     */
    @Test
    void disabledDiscoveryDoesNotResolveSources() {
        for (Map<String, String> values : List.of(Map.<String, String>of(),
                Map.of("data.sources.sql.0.health.enabled", "false"),
                Map.of("data.sources.sql.0.health-check.enabled", "true"),
                Map.of("data.sources.sql.provider.hikari.url", "unused", "data.sources.sql.health.enabled", "false"))) {
            var factory = new DataSourceHealthCheckFactory(() -> configurations(Config.just(ConfigSources.create(values))),
                    () -> { throw new AssertionError("Disabled discovery must not resolve sources."); });
            assertThat(factory.services().isEmpty(), is(true));
        }
        Config invalid = mock(Config.class);
        when(invalid.get("data.sources.sql")).thenThrow(new ConfigException("CONFIG_CANARY"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new DataSourceHealthCheckFactory(() -> configurations(invalid), () -> {
                    throw new AssertionError("Invalid discovery must not resolve its target.");
                }).services());
        assertThat(failure.getMessage(), not(containsString("CONFIG_CANARY")));
        assertThat(failure.getCause(), is((Throwable) null));
    }

    /**
     * Uses the selected source with an independent health policy in both data source configuration forms.
     */
    @Test
    void listAndSingleSourceDefinitionsBindEffectiveServices() {
        DataSource source = mock(DataSource.class);
        @SuppressWarnings("unchecked")
        ServiceInstance<DataSource> instance = mock(ServiceInstance.class);
        when(instance.qualifiers()).thenReturn(Set.of(Qualifier.createNamed("orders")));
        when(instance.get()).thenReturn(source);
        for (boolean single : List.of(false, true)) {
            String prefix = single ? "data.sources.sql" : "data.sources.sql.0";
            Map<String, String> values = new HashMap<>();
            values.put(prefix + ".name", "orders");
            values.put(prefix + ".health.enabled", "true");
            values.put(prefix + ".health.query", "SELECT 2");
            values.put(prefix + ".health.name", "source-db");
            values.put(prefix + ".health.type", "liveness");
            values.put(prefix + ".provider.hikari.url", "unused");
            values.put(prefix + ".provider.hikari.connection-test-query", "SELECT 3");
            Config config = Config.just(ConfigSources.create(values));
            assertThrows(IllegalStateException.class,
                         () -> new DataSourceHealthCheckFactory(() -> configurations(config), List::of).services());
            var services = new DataSourceHealthCheckFactory(() -> configurations(config), () -> List.of(instance)).services();
            assertThat(services.size(), is(1));
            assertThat(services.getFirst().qualifiers().isEmpty(), is(true));
            var check = (DataSourceHealthCheck) services.getFirst().get();
            assertThat(check.name(), is("source-db"));
            assertThat(check.type(), is(HealthCheckType.LIVENESS));
            assertThat(check.prototype().query(), is("SELECT 2"));
            assertThat(check.prototype().dataSource(), sameInstance(source));
        }
        verifyZeroInteractions(source);
        Config invalid = Config.just(ConfigSources.create(Map.of("data.sources.sql.0.name", "orders",
                "data.sources.sql.0.health.enabled", "true",
                "data.sources.sql.0.health.query-timeout", "CONFIG_CANARY")));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new DataSourceHealthCheckFactory(() -> configurations(invalid), () -> List.of(instance)).services());
        assertThat(failure.getMessage(), not(containsString("CONFIG_CANARY")));
        assertThat(failure.getCause(), is((Throwable) null));
        verifyZeroInteractions(source);
    }

    /**
     * Uses the preferred definition policy while retaining duplicate health paths and the effective source.
     */
    @Test
    void preferredDefinitionsRetainNamesAndPermitCollidingPaths() {
        ProviderConfig provider = mock(ProviderConfig.class);
        DataSourceConfig preferred = DataSourceConfig.builder().name("orders").provider(provider)
                .health(health -> health.enabled(true).query("SELECT 2")).build();
        DataSourceConfig shadowed = DataSourceConfig.builder().name("orders").provider(provider)
                .health(health -> health.enabled(true).query("UPDATE invalid")).build();
        DataSourceConfig other = DataSourceConfig.builder().name("other").provider(provider)
                .health(health -> health.enabled(true).name("orders")).build();
        DataSource source = mock(DataSource.class);
        @SuppressWarnings("unchecked")
        ServiceInstance<DataSource> instance = mock(ServiceInstance.class);
        when(instance.qualifiers()).thenReturn(Set.of(Qualifier.createNamed("orders"), Qualifier.createNamed("other")));
        when(instance.get()).thenReturn(source);
        var checks = new DataSourceHealthCheckFactory(() -> List.of(preferred, shadowed, other), () -> List.of(instance))
                .services();
        assertThat(checks.size(), is(2));
        assertThat(checks.get(0).get().name(), is("orders"));
        assertThat(checks.get(1).get().name(), is("orders"));
        assertThat(((DataSourceHealthCheck) checks.get(0).get()).prototype().query(), is("SELECT 2"));
        assertThat(((DataSourceHealthCheck) checks.get(1).get()).prototype().query(), is("SELECT 1"));
        DataSourceConfig disabled = DataSourceConfig.builder(preferred).health(DataSourceHealthConfig.create()).build();
        assertThat(new DataSourceHealthCheckFactory(() -> List.of(disabled, shadowed), () -> {
            throw new AssertionError("The disabled preferred definition must not resolve a source.");
        }).services().isEmpty(), is(true));
        for (DataSourceHealthConfig invalid : List.of(
                DataSourceHealthConfig.builder().enabled(true).query("UPDATE invalid").build(),
                DataSourceHealthConfig.builder().enabled(true).name(" ").build(),
                DataSourceHealthConfig.builder().enabled(true).queryTimeout(Duration.ZERO).build())) {
            DataSourceConfig definition = DataSourceConfig.builder(preferred).health(invalid).build();
            assertThrows(IllegalArgumentException.class,
                         () -> new DataSourceHealthCheckFactory(() -> List.of(definition), () -> List.of(instance)).services());
        }
        verifyZeroInteractions(source);
    }

    /**
     * Keeps identical source names isolated across live registries without a client runtime.
     */
    @Test
    void registryDiscoveryRetainsItsOwningSource() throws Exception {
        Fixture healthy = fixture();
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("SECRET_SQL_CREDENTIAL"));
        Config config = Config.just(ConfigSources.create(Map.of("data.sources.sql.0.name", "orders",
                "data.sources.sql.0.health.enabled", "true", "data.sources.sql.0.health.name", "source-db")));
        var first = registry(config, healthy.source());
        var second = registry(config, broken);
        try {
            HealthCheck a = first.registry().all(HealthCheck.class).getFirst();
            HealthCheck b = second.registry().all(HealthCheck.class).getFirst();
            verifyZeroInteractions(healthy.source(), broken);
            assertThat(a.call().status(), is(HealthCheckResponse.Status.UP));
            assertThat(b.call().status(), is(HealthCheckResponse.Status.DOWN));
            GlobalServiceRegistry.registry(first.registry());
            Services.setNamed(DataSource.class, broken, "orders");
            assertThat(a.call().status(), is(HealthCheckResponse.Status.UP));
            noTransactionControl(healthy.connection());
        } finally {
            try {
                first.shutdown();
            } finally {
                second.shutdown();
            }
        }
    }

    private static void finishWorkers(CountDownLatch release,
                                      List<Future<Boolean>> workers,
                                      ExecutorService executor,
                                      Throwable primary) throws InterruptedException {
        try {
            release.countDown();
            workers.forEach(worker -> worker.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException | RuntimeException | Error failure) {
            if (primary == null) {
                throw failure;
            }
            if (primary != failure) {
                primary.addSuppressed(failure);
            }
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<DataSourceConfig> configurations(Config config) {
        Config sources = config.get("data.sources.sql");
        List<Config> definitions = sources.get("provider").exists() ? List.of(sources) : sources.asNodeList().orElse(List.of());
        return definitions.stream()
                .map(definition -> DataSourceConfig.builder().config(definition).provider(mock(ProviderConfig.class)).build())
                .toList();
    }

    private static ServiceRegistryManager registry(Config config, DataSource source) {
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(DataSourceHealthCheckFactory__ServiceDescriptor.INSTANCE)
                .build());
        GlobalServiceRegistry.registry(manager.registry());
        Services.set(DataSourceConfig.class, configurations(config).getFirst());
        Services.setNamed(DataSource.class, source, "orders");
        return manager;
    }

    private static BooleanSupplier probe(DataSource source, String query, Duration timeout) {
        DataSourceHealthCheck check = DataSourceHealthCheck.builder()
                .dataSource(source).query(query).queryTimeout(timeout).build();
        return () -> check.call().status() == HealthCheckResponse.Status.UP;
    }

    private static BooleanSupplier probe(Fixture fixture) {
        return probe(fixture.source(), "SELECT 1", Duration.ofSeconds(2));
    }

    private static void noTransactionControl(Connection connection) throws Exception {
        verify(connection, never()).setAutoCommit(anyBoolean());
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    private static Fixture fixture() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(statement.getUpdateCount()).thenReturn(-1);
        return new Fixture(source, connection, statement, rows);
    }

    private record Fixture(DataSource source, Connection connection, PreparedStatement statement, ResultSet rows) {
    }
}
