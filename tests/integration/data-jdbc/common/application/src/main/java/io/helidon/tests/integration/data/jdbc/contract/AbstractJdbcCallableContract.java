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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.CallableStatement;
import java.sql.JDBCType;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.NoResultException;
import io.helidon.data.NonUniqueResultException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.GeneratedJdbcData;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.tests.integration.data.jdbc.declarative.repository.CallableRoutineRepository;
import io.helidon.tests.integration.data.jdbc.support.TestConfigFactory;
import io.helidon.tests.integration.data.jdbc.support.CallableFailureFixture;
import io.helidon.tests.integration.data.jdbc.support.TestDataSourceFactory;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public abstract class AbstractJdbcCallableContract {
    private ServiceRegistryManager manager;
    private JdbcClient client;
    private Config connectionConfig;

    protected abstract Config configuration();

    protected abstract String vendor();

    protected abstract boolean generated();

    @BeforeEach
    protected void start() throws Exception {
        Config config = configuration();
        Map<String, String> values = new HashMap<>(config.asMap().get());
        if (vendor().equals("pgsql")) {
            values.compute("data.clients.jdbc.0.connection.url", (_, url) -> url + "?escapeSyntaxCallMode=callIfNoReturn");
        }
        TestConfigFactory.config(Config.just(ConfigSources.create(values)));
        manager = ServiceRegistryManager.start();
        client = manager.registry().get(JdbcClient.class);
        connectionConfig = Config.just(ConfigSources.create(values)).get("data.clients.jdbc.0.connection");
        try (Connection connection = DriverManager.getConnection(connectionConfig.get("url").asString().get(),
                connectionConfig.get("username").asString().get(), connectionConfig.get("password").asString().get())) {
            DatabaseMetaData metadata = connection.getMetaData();
            System.out.println("Callable capability fixture: " + metadata.getDatabaseProductName() + " "
                    + metadata.getDatabaseProductVersion() + ", driver " + metadata.getDriverName() + " "
                    + metadata.getDriverVersion() + ", explicit transaction cursors, escapeSyntaxCallMode="
                    + (vendor().equals("pgsql") ? "callIfNoReturn" : "driver default"));
        }
        client.create("DELETE FROM TX_MATRIX WHERE DATA_VALUE <> ?").bind(1, "baseline").execute();
        switch (vendor()) {
        case "oracle" -> {
            client.create("CREATE OR REPLACE FUNCTION sp_sum(a IN INTEGER, b IN INTEGER) RETURN INTEGER AS BEGIN "
                                  + "RETURN a + b; END;").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_out(value OUT INTEGER) AS BEGIN value := 42; END;").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_write(value IN VARCHAR2) AS BEGIN "
                                  + "INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (value); END;").execute();
            client.create("CREATE OR REPLACE FUNCTION sp_mixed(p_input IN INTEGER, value IN OUT INTEGER, "
                                  + "p_rows OUT SYS_REFCURSOR) RETURN INTEGER AS BEGIN value := value + p_input; "
                                  + "IF p_input < 0 THEN p_rows := NULL; ELSE OPEN p_rows FOR SELECT 42 AS ID FROM dual "
                                  + "WHERE p_input > 0; END IF; RETURN p_input; END;").execute();
        }
        case "pgsql" -> {
            client.create("CREATE OR REPLACE FUNCTION sp_sum(a INTEGER, b INTEGER) RETURNS INTEGER "
                                  + "LANGUAGE SQL AS $$ SELECT a + b $$").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_write(value VARCHAR) LANGUAGE plpgsql AS $$ BEGIN "
                                  + "INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (value); END $$").execute();
        }
        case "mysql" -> {
            client.create("DROP FUNCTION IF EXISTS sp_sum").execute();
            client.create("CREATE FUNCTION sp_sum(a INTEGER, b INTEGER) RETURNS INTEGER DETERMINISTIC RETURN a + b").execute();
            client.create("DROP PROCEDURE IF EXISTS sp_out").execute();
            client.create("CREATE PROCEDURE sp_out(OUT value INTEGER) BEGIN SET value = 42; END").execute();
            client.create("DROP PROCEDURE IF EXISTS sp_write").execute();
            client.create("CREATE PROCEDURE sp_write(IN value VARCHAR(40)) BEGIN "
                                  + "INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (value); END").execute();
        }
        default -> throw new IllegalArgumentException("The test vendor is unknown.");
        }
        switch (vendor()) {
        case "oracle" -> {
            client.create("CREATE OR REPLACE FUNCTION sp_decimal(p_input IN INTEGER) RETURN NUMBER AS BEGIN "
                                  + "RETURN p_input + 1.25; END;").execute();
            client.create("CREATE OR REPLACE FUNCTION sp_add(p_input IN INTEGER) RETURN INTEGER AS BEGIN "
                                  + "RETURN p_input + 1; END;").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_values(p_input IN INTEGER, value IN OUT INTEGER, "
                                  + "other IN OUT VARCHAR2) AS BEGIN value := p_input + 1; other := 'done'; END;").execute();
            client.create("CREATE OR REPLACE FUNCTION sp_cursor(p_input IN INTEGER) RETURN SYS_REFCURSOR AS "
                                  + "c SYS_REFCURSOR; BEGIN IF p_input < 0 THEN RETURN NULL; END IF; "
                                  + "OPEN c FOR SELECT 42 AS ID FROM dual WHERE p_input > 0 UNION ALL SELECT 43 FROM dual WHERE p_input > 1 "
                                  + "ORDER BY 1; RETURN c; END;").execute();
        }
        case "pgsql" -> {
            client.create("CREATE OR REPLACE FUNCTION sp_decimal(p_input INTEGER) RETURNS NUMERIC "
                                  + "LANGUAGE SQL AS $$ SELECT p_input + 1.25 $$").execute();
            client.create("CREATE OR REPLACE FUNCTION sp_add(p_input INTEGER) RETURNS INTEGER "
                                  + "LANGUAGE SQL AS $$ SELECT p_input + 1 $$").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_values(IN p_input INTEGER, INOUT value INTEGER, "
                                  + "INOUT other VARCHAR) LANGUAGE plpgsql AS $$ BEGIN value := p_input + 1; "
                                  + "other := 'done'; END $$").execute();
            client.create("CREATE OR REPLACE FUNCTION sp_cursor(p_input INTEGER) RETURNS refcursor "
                                  + "LANGUAGE plpgsql AS $$ DECLARE c refcursor; BEGIN IF p_input < 0 THEN RETURN NULL; "
                                  + "END IF; OPEN c FOR SELECT 42 AS ID WHERE p_input > 0 UNION ALL SELECT 43 WHERE p_input > 1 "
                                  + "ORDER BY 1; RETURN c; END $$").execute();
        }
        case "mysql" -> {
            client.create("DROP FUNCTION IF EXISTS sp_decimal").execute();
            client.create("CREATE FUNCTION sp_decimal(p_input INTEGER) RETURNS DECIMAL(12, 2) DETERMINISTIC "
                                  + "RETURN p_input + 1.25").execute();
            client.create("DROP FUNCTION IF EXISTS sp_add").execute();
            client.create("CREATE FUNCTION sp_add(p_input INTEGER) RETURNS INTEGER DETERMINISTIC RETURN p_input + 1").execute();
            client.create("DROP PROCEDURE IF EXISTS sp_values").execute();
            client.create("CREATE PROCEDURE sp_values(IN p_input INTEGER, INOUT value INTEGER, OUT other VARCHAR(20)) "
                                  + "BEGIN SET value = p_input + 1; SET other = 'done'; END").execute();
            client.create("DROP PROCEDURE IF EXISTS sp_direct").execute();
            client.create("CREATE PROCEDURE sp_direct() BEGIN SELECT 42; SELECT 43; END").execute();
        }
        default -> throw new IllegalArgumentException("The test vendor is unknown.");
        }
        switch (vendor()) {
        case "oracle" -> {
            client.create("CREATE OR REPLACE PROCEDURE sp_noop AS BEGIN NULL; END;").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_pair(first OUT SYS_REFCURSOR, second OUT SYS_REFCURSOR, "
                                  + "value OUT INTEGER) AS BEGIN value := 7; "
                                  + "OPEN first FOR SELECT 42 AS ID FROM dual UNION ALL SELECT 43 FROM dual ORDER BY 1; "
                                  + "OPEN second FOR SELECT 44 AS ID FROM dual; END;").execute();
            client.create("CREATE OR REPLACE FUNCTION sp_stamp RETURN TIMESTAMP AS BEGIN "
                                  + "RETURN TIMESTAMP '2026-10-28 12:13:14.123456'; END;").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_bundle(p_input IN INTEGER, value OUT INTEGER, "
                                  + "p_rows OUT SYS_REFCURSOR) AS BEGIN value := p_input + 1; "
                                  + "IF p_input < 0 THEN p_rows := NULL; ELSE OPEN p_rows FOR SELECT 42 AS ID FROM dual "
                                  + "WHERE p_input > 0; END IF; END;").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_inout(p_input IN INTEGER, p_value IN OUT INTEGER) "
                                  + "AS BEGIN p_value := p_value + p_input; END;").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_ddl AUTHID CURRENT_USER AS BEGIN EXECUTE IMMEDIATE "
                                  + "'CREATE TABLE SP_DDL_PROBE (ID INTEGER)'; END;").execute();
        }
        case "pgsql" -> {
            client.create("CREATE OR REPLACE PROCEDURE sp_noop() LANGUAGE plpgsql AS $$ BEGIN NULL; END $$").execute();
            client.create("CREATE OR REPLACE FUNCTION sp_stamp() RETURNS TIMESTAMP LANGUAGE SQL "
                                  + "AS $$ SELECT TIMESTAMP '2026-10-28 12:13:14.123456' $$").execute();
            client.create("CREATE OR REPLACE FUNCTION sp_time() RETURNS TIME LANGUAGE SQL "
                                  + "AS $$ SELECT TIME '12:13:14.123456' $$").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_inout(IN p_input INTEGER, INOUT p_value INTEGER) "
                                  + "LANGUAGE plpgsql AS $$ BEGIN p_value := p_value + p_input; END $$").execute();
            client.create("CREATE OR REPLACE PROCEDURE sp_ddl() LANGUAGE plpgsql AS $$ BEGIN COMMIT; END $$").execute();
        }
        case "mysql" -> {
            client.create("DROP FUNCTION IF EXISTS sp_stamp").execute();
            client.create("CREATE FUNCTION sp_stamp() RETURNS DATETIME(6) DETERMINISTIC "
                                  + "RETURN '2026-10-28 12:13:14.123456'").execute();
            client.create("DROP FUNCTION IF EXISTS sp_time").execute();
            client.create("CREATE FUNCTION sp_time() RETURNS TIME(6) DETERMINISTIC RETURN '12:13:14.123456'").execute();
            client.create("DROP PROCEDURE IF EXISTS sp_noop").execute();
            client.create("CREATE PROCEDURE sp_noop() BEGIN END").execute();
            client.create("DROP PROCEDURE IF EXISTS sp_inout").execute();
            client.create("CREATE PROCEDURE sp_inout(IN p_input INTEGER, INOUT p_value INTEGER) "
                                  + "BEGIN SET p_value = p_value + p_input; END").execute();
            client.create("DROP PROCEDURE IF EXISTS sp_ddl").execute();
            client.create("CREATE PROCEDURE sp_ddl() BEGIN CREATE TABLE SP_DDL_PROBE (ID INTEGER); END").execute();
        }
        default -> throw new IllegalArgumentException("The test vendor is unknown.");
        }

        switch (vendor()) {
        case "oracle" -> client.create("CREATE OR REPLACE PROCEDURE sp_values_reordered(other IN OUT VARCHAR2, "
                + "value IN OUT INTEGER, p_input IN INTEGER) AS BEGIN sp_values(p_input, value, other); END;").execute();
        case "pgsql" -> client.create("CREATE OR REPLACE PROCEDURE sp_values_reordered(INOUT other VARCHAR, "
                + "INOUT value INTEGER, IN p_input INTEGER) LANGUAGE plpgsql AS $$ BEGIN "
                + "CALL sp_values(p_input, value, other); END $$").execute();
        case "mysql" -> {
            client.create("DROP PROCEDURE IF EXISTS sp_values_reordered").execute();
            client.create("CREATE PROCEDURE sp_values_reordered(OUT other VARCHAR(20), INOUT value INTEGER, "
                    + "IN p_input INTEGER) BEGIN CALL sp_values(p_input, value, other); END").execute();
        }
        default -> throw new IllegalArgumentException("The test vendor is unknown.");
        }

    }

    @AfterEach
    protected void stop() {
        if (manager != null) {
            manager.shutdown();
        }
        TestConfigFactory.reset();
    }

    @Test
    protected void validatesRoutineKindsWithBothEscapeForms() throws Exception {
        String procedure = switch (vendor()) {
        case "oracle" -> "CREATE OR REPLACE PROCEDURE sp_form_procedure(p_input IN INTEGER) AS BEGIN "
                + "INSERT INTO TX_MATRIX(DATA_VALUE) VALUES ('probe'); END;";
        case "pgsql" -> "CREATE OR REPLACE PROCEDURE sp_form_procedure(p_input INTEGER) LANGUAGE plpgsql AS $$ BEGIN "
                + "INSERT INTO TX_MATRIX(DATA_VALUE) VALUES ('probe'); END $$";
        case "mysql" -> "CREATE PROCEDURE sp_form_procedure(IN p_input INTEGER) BEGIN "
                + "INSERT INTO TX_MATRIX(DATA_VALUE) VALUES ('probe'); END";
        default -> throw new IllegalArgumentException("The test vendor is unknown.");
        };
        if (vendor().equals("mysql")) {
            client.create("DROP PROCEDURE IF EXISTS sp_form_procedure").execute();
        }
        client.create(procedure).execute();
        List<String> profiles = vendor().equals("pgsql") ? List.of("callIfNoReturn", "select") : List.of("default");
        for (String profile : profiles) {
            for (String source : List.of("literal", "default", "configured")) {
                manager.shutdown();
                TestConfigFactory.reset();
                Map<String, String> values = new HashMap<>(configuration().asMap().get());
                if (vendor().equals("pgsql")) {
                    values.compute("data.clients.jdbc.0.connection.url", (_, url) -> url + "?escapeSyntaxCallMode=" + profile);
                }
                if (source.equals("configured")) {
                    values.put("application.calls.form-procedure", "{call sp_form_procedure(:input)}");
                    values.put("application.calls.form-procedure-return", "{? = call sp_form_procedure(:input)}");
                    values.put("application.calls.form-function", "{call sp_add(:input)}");
                    values.put("application.calls.form-function-return", "{? = call sp_add(:input)}");
                    values.put("application.calls.form-discard", "{? = call sp_add(:input)}");
                }
                Config configured = Config.just(ConfigSources.create(values));
                TestConfigFactory.config(configured);
                manager = ServiceRegistryManager.start();
                client = manager.registry().get(JdbcClient.class);
                connectionConfig = configured.get("data.clients.jdbc.0.connection");
                CallableRoutineRepository repository = generated()
                        ? manager.registry().get(CallableRoutineRepository.class) : null;
                for (boolean function : List.of(false, true)) {
                    for (boolean leading : List.of(false, true)) {
                        String routine = function ? "sp_add" : "sp_form_procedure";
                        String sql = (leading ? "{? = call " : "{call ") + routine + "(?)}";
                        boolean supported = profile.equals("select") ? function : function == leading;
                        String description = vendor() + "/" + profile + "/" + source + "/" + routine + "/" + leading;
                        String state = switch (vendor()) {
                        case "mysql" -> "42000";
                        case "oracle" -> "65000";
                        default -> "42809";
                        };
                        int code = switch (vendor()) {
                        case "mysql" -> 1305;
                        case "oracle" -> 6550;
                        default -> 0;
                        };
                        try (Connection connection = DriverManager.getConnection(connectionConfig.get("url").asString().get(),
                                connectionConfig.get("username").asString().get(),
                                connectionConfig.get("password").asString().get());
                             CallableStatement raw = connection.prepareCall(sql)) {
                            if (leading) {
                                raw.registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
                            }
                            raw.setInt(leading ? 2 : 1, 41);
                            if (supported) {
                                boolean rows = raw.execute();
                                if (function && !leading) {
                                    assertThat(description, rows, is(true));
                                    try (var result = raw.getResultSet()) {
                                        assertThat(result.next(), is(true));
                                        assertThat(result.getInt(1), is(42));
                                        assertThat(result.next(), is(false));
                                    }
                                }
                                while (rows || raw.getLargeUpdateCount() != -1) {
                                    rows = raw.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                                }
                                if (leading) {
                                    assertThat(description, raw.getInt(1), is(42));
                                }
                            } else {
                                SQLException failure = assertThrows(SQLException.class, raw::execute, description);
                                assertThat(description, failure.getSQLState(), is(state));
                                assertThat(description, failure.getErrorCode(), is(code));
                            }
                        }
                        Callable<Integer> action;
                        if (generated()) {
                            boolean literal = source.equals("literal");
                            if (function) {
                                action = leading
                                        ? () -> literal ? repository.formFunctionReturn(41)
                                                : repository.configuredFormFunctionReturn(41)
                                        : () -> literal ? repository.formFunction(41) : repository.configuredFormFunction(41);
                            } else if (leading) {
                                action = () -> literal ? repository.formProcedureReturn(41)
                                        : repository.configuredFormProcedureReturn(41);
                            } else {
                                action = () -> {
                                    if (literal) {
                                        repository.formProcedure(41);
                                    } else {
                                        repository.configuredFormProcedure(41);
                                    }
                                    return 0;
                                };
                            }
                        } else {
                            action = () -> {
                                JdbcClient.Statement stage;
                                if (source.equals("literal")) {
                                    stage = client.create(sql).bind(leading ? 2 : 1, 41);
                                } else {
                                    String key = "form-" + (function ? "function" : "procedure") + (leading ? "-return" : "");
                                    String supplied = source.equals("configured")
                                            ? configured.get("application.calls." + key).asString().get() : sql;
                                    stage = client.prepare(supplied, leading ? List.of("return", "input") : List.of("input"))
                                            .get().bind("input", 41);
                                }
                                JdbcClient.Call call = stage.call();
                                if (leading) {
                                    return call.returns(JDBCType.INTEGER).outputs().get("return", Integer.class);
                                }
                                if (function) {
                                    return call.map(result -> result.rows(row -> row.get(1, Integer.class)).one());
                                }
                                call.execute();
                                return 0;
                            };
                        }
                        if (supported) {
                            assertThat(description, action.call(), is(function ? 42 : 0));
                            if (function && leading) {
                                if (generated()) {
                                    if (source.equals("literal")) {
                                        repository.discardFormReturn(41);
                                    } else {
                                        repository.configuredDiscardFormReturn(41);
                                    }
                                } else {
                                    JdbcClient.Statement discard;
                                    if (source.equals("literal")) {
                                        discard = client.create(sql).bind(2, 41);
                                    } else {
                                        String supplied = source.equals("configured")
                                                ? configured.get("application.calls.form-discard").asString().get() : sql;
                                        discard = client.prepare(supplied, List.of("return", "input")).get().bind("input", 41);
                                    }
                                    discard.call().returns(JDBCType.INTEGER).execute();
                                }
                            } else if (!function) {
                                int count = committedCount("probe");
                                assertThrows(TxException.class, () -> Tx.transaction(() -> {
                                    action.call();
                                    throw new IllegalStateException("The callable write must roll back.");
                                }));
                                assertThat(committedCount("probe"), is(count));
                            }
                        } else {
                            DataException failure = assertThrows(DataException.class, action::call, description);
                            assertThat(description, failure.getMessage(), containsString(state));
                            assertThat(description, failure.getMessage(), containsString("vendor code " + code));
                            StringWriter diagnostics = new StringWriter();
                            failure.printStackTrace(new PrintWriter(diagnostics));
                            assertThat(diagnostics.toString(), not(containsString(routine)));
                            assertThat(diagnostics.toString(), not(containsString(sql)));
                            assertThrows(TxException.class, () -> Tx.transaction(() -> {
                                client.create("INSERT INTO TX_MATRIX(DATA_VALUE) VALUES (?)")
                                        .bind(1, "call-form-rollback").execute();
                                return action.call();
                            }));
                            assertThat(committedCount("call-form-rollback"), is(0));
                        }
                        assertThat(description, client.create("{? = call sp_add(?)}").bind(2, 41).call()
                                .out("return", 1, JDBCType.INTEGER).outputs().get("return", Integer.class), is(42));
                        System.out.println("Callable escape matrix: " + description + ", supported=" + supported + ".");
                    }
                }
            }
        }
    }

    @Test
    protected void executesAliasedInputsRepeatedMarkersAndNullableInout() {
        if (generated()) {
            CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
            assertThat(repository.aliasedRepeated(21), is(Optional.of(42)));
            assertThat(repository.aliasedRepeated(null), is(Optional.empty()));
            assertThat(repository.aliasedInout(2, 40), is(Optional.of(42)));
            assertThat(repository.aliasedInout(2, null), is(Optional.empty()));
        } else {
            assertThat(client.prepare("{? = call sp_sum(:input, :input)}").get().bind("input", 21)
                    .call().returns(JDBCType.INTEGER).outputs().get("return", Integer.class), is(42));
        }
    }

    @Test
    protected void executesConfiguredCallsFromNamedAndPermittedPositionalYaml() {
        for (String variant : List.of("named", "positional", "reordered")) {
            Config sql = restartConfiguredCalls(variant);
            CallableRoutineRepository repository = generated() ? manager.registry().get(CallableRoutineRepository.class) : null;
            if (generated()) {
                repository.configuredCommand();
                assertThat(repository.configuredValues("before", 1, 41),
                        is(new CallableRoutineRepository.ConfiguredValues("done", 42)));
                assertThat(repository.configuredInout(40, 2), is(Optional.of(42)));
                assertThat(repository.configuredInout(null, 2), is(Optional.empty()));
                assertThat(repository.configuredAliasedInout(40, 2), is(Optional.of(42)));
                assertThat(repository.configuredAliasedInout(null, 2), is(Optional.empty()));
                assertThat(repository.configuredTotal(41), is(Optional.of(42)));
                assertThat(repository.configuredTotal(null), is(Optional.empty()));
                assertThat(repository.configuredRepeated(21), is(Optional.of(42)));
            } else {
                client.prepare(sql.get("command").asString().get()).get().call().execute();
                JdbcClient.OutParameters values = client.prepare(sql.get("values").asString().get(),
                        List.of("input", "value", "other")).get().bind("other", "before").bind("value", 1).bind("input", 41)
                        .call().out("other", JDBCType.VARCHAR).out("value", JDBCType.INTEGER).outputs();
                assertThat(values.get("value", Integer.class), is(42));
                assertThat(values.get("other", String.class), is("done"));
                assertThat(client.prepare(sql.get("total").asString().get(), List.of("return", "input")).get()
                        .bind("input", 41).call().returns(JDBCType.INTEGER).outputs().get("return", Integer.class), is(42));
                JdbcClient.Statement nullable = client.prepare(sql.get("inout").asString().get(), List.of("input", "value")).get()
                        .bind("input", 2);
                GeneratedJdbcData.bindNull(nullable, "value", JDBCType.INTEGER);
                assertThat(nullable.call().out("value", JDBCType.INTEGER).outputs().optional("value", Integer.class),
                           is(Optional.empty()));
            }
            if (!vendor().equals("pgsql")) {
                if (generated()) {
                    assertThat(repository.configuredOutput(), is(42));
                } else {
                    assertThat(client.prepare(sql.get("output").asString().get(), List.of("value")).get()
                            .call().out("value", JDBCType.INTEGER).outputs().get("value", Integer.class), is(42));
                }
            }
            var definition = client.prepare(sql.get("input").asString().get(), List.of("input"));
            String committed = "configured-" + variant;
            Tx.transaction(() -> {
                if (generated()) {
                    repository.configuredInput(committed);
                } else {
                    definition.get().bind("input", committed).call().execute();
                }
                return Boolean.TRUE;
            });
            assertThat(committedCount(committed), is(1));
            String rolledBack = "rollback-" + variant;
            assertThrows(TxException.class, () -> Tx.transaction(() -> {
                definition.get().bind("input", rolledBack).call().execute();
                throw new IllegalStateException("The configured call transaction must roll back.");
            }));
            assertThat(committedCount(rolledBack), is(0));
        }
    }

    @Test
    protected void rejectsConfiguredPositionalProcedureOutputsBeforeAcquisition() throws SQLException {
        if (!generated()) {
            assertThat(client.prepare("{call sp_inout(?, ?)}").get().bind(1, 2).bind(2, 40).call()
                    .out("value", 2, JDBCType.INTEGER).map(result -> {
                        result.skipRemainingResults();
                        return result.get("value", Integer.class);
                    }), is(42));
            return;
        }
        Map<String, String> invalid = Map.of(
                "output", "{call sp_out(?)}",
                "values", "{call sp_values(?, ?, ?)}",
                "inout", "{call sp_inout(?, ?)}",
                "leading-outputs", "{call sp_values_reordered(?, ?, ?)}");
        for (Map.Entry<String, String> entry : invalid.entrySet()) {
            manager.shutdown();
            TestConfigFactory.reset();
            DataSource source = mock(DataSource.class);
            TestDataSourceFactory.dataSource("callable-policy-source", source);
            try {
                Map<String, String> settings = new HashMap<>(Config.just(ConfigSources.classpath(
                        "configured-statements/callable/named/application.yaml")).asMap().get());
                settings.put("data.clients.jdbc.0.name", "@default");
                settings.put("data.clients.jdbc.0.data-source", "callable-policy-source");
                settings.put("application.calls." + entry.getKey(), entry.getValue());
                TestConfigFactory.config(Config.just(ConfigSources.create(settings)));
                manager = ServiceRegistryManager.start();
                manager.registry().get(JdbcClient.class);
                RuntimeException failure = assertThrows(RuntimeException.class,
                        () -> manager.registry().get(CallableRoutineRepository.class));
                StringWriter diagnostic = new StringWriter();
                failure.printStackTrace(new PrintWriter(diagnostic));
                assertThat(diagnostic.toString(), containsString("named SQL markers"));
                assertThat(diagnostic.toString(), not(containsString(entry.getValue())));
                verify(source, never()).getConnection();
            } finally {
                TestDataSourceFactory.reset();
            }
        }
        restartConfiguredCalls("named");
        assertThat(manager.registry().get(CallableRoutineRepository.class).configuredTotal(41), is(Optional.of(42)));
    }

    @Test
    protected void rejectsConfiguredReturnRoleChangesAtActivation() throws SQLException {
        if (!generated()) {
            assertThat(client.prepare("{call sp_inout(:input, :return)}", List.of("return", "input")).get()
                    .bind("input", 2).bind("return", 40).call().out("return", JDBCType.INTEGER)
                    .outputs().get("return", Integer.class), is(42));
            return;
        }
        List<Map.Entry<String, String>> invalid = List.of(
                Map.entry("total", "{call sp_add(?, ?)}"),
                Map.entry("total", "{call sp_add(:return, :input)}"),
                Map.entry("stamp", "{call sp_stamp(?)}"),
                Map.entry("stamp", "{call sp_stamp(:return)}"),
                Map.entry("repeated", "{call sp_sum(:return, :input, :input)}"),
                Map.entry("inherited-total", "{call sp_add(:return, :input)}"),
                Map.entry("ordinary-return", "{? = call sp_add(:input)}"));
        for (Map.Entry<String, String> entry : invalid) {
            manager.shutdown();
            TestConfigFactory.reset();
            DataSource source = mock(DataSource.class);
            TestDataSourceFactory.dataSource("callable-return-source", source);
            try {
                Map<String, String> settings = new HashMap<>(Config.just(ConfigSources.classpath(
                        "configured-statements/callable/named/application.yaml")).asMap().get());
                settings.put("data.clients.jdbc.0.name", "@default");
                settings.put("data.clients.jdbc.0.data-source", "callable-return-source");
                settings.put("application.calls." + entry.getKey(), entry.getValue());
                TestConfigFactory.config(Config.just(ConfigSources.create(settings)));
                manager = ServiceRegistryManager.start();
                manager.registry().get(JdbcClient.class);
                RuntimeException failure = assertThrows(RuntimeException.class,
                        () -> manager.registry().get(CallableRoutineRepository.class));
                StringWriter diagnostic = new StringWriter();
                failure.printStackTrace(new PrintWriter(diagnostic));
                assertThat(diagnostic.toString(), containsString("leading return slot"));
                assertThat(diagnostic.toString(), not(containsString(entry.getValue())));
                verify(source, never()).getConnection();
            } finally {
                TestDataSourceFactory.reset();
            }
        }
        restartConfiguredCalls("named");
        CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
        assertThat(repository.configuredTotal(41), is(Optional.of(42)));
        assertThat(repository.inheritedConfiguredTotal(41), is(42));
        assertThat(repository.configuredOrdinaryReturn(2, 40), is(Optional.of(42)));
        assertThat(repository.configuredOrdinaryReturn(2, 39), is(Optional.of(41)));
    }

    @Test
    protected void materializesConfiguredCursorReturnsAndRetainsCaughtFailures() {
        for (String variant : List.of("named", "positional")) {
            Config sql = restartConfiguredCalls(variant);
            var definition = client.prepare(sql.get("cursor").asString().get(), List.of("return", "input"));
            CallableRoutineRepository repository = generated() ? manager.registry().get(CallableRoutineRepository.class) : null;
            if (vendor().equals("mysql")) {
                DataException failure = assertThrows(DataException.class, () -> definition.get().bind("input", 1).call()
                        .returns(JDBCType.REF_CURSOR).consume(result -> {
                            result.skipRemainingResults();
                            result.cursor("return", row -> row.get(1, Integer.class)).list();
                        }));
                assertThat(failure.getMessage(), containsString("callable"));
                if (generated()) {
                    DataException generatedFailure = assertThrows(DataException.class, () -> repository.configuredCursor(1));
                    assertThat(generatedFailure.getMessage(), containsString("callable"));
                }
                continue;
            }
            Tx.transaction(() -> {
                if (generated()) {
                    assertThat(repository.configuredCursor(-1), is(Optional.empty()));
                    assertThat(repository.configuredCursor(0), is(Optional.of(List.of())));
                    assertThat(repository.configuredCursor(2), is(Optional.of(List.of(42, 43))));
                } else {
                    for (int input : List.of(-1, 0, 2)) {
                        Optional<List<Integer>> expected = input < 0 ? Optional.empty()
                                : Optional.of(input == 0 ? List.of() : List.of(42, 43));
                        assertThat(definition.get().bind("input", input).call().returns(JDBCType.REF_CURSOR).map(result -> {
                            result.skipRemainingResults();
                            return result.optionalCursor("return", row -> row.get(1, Integer.class)).map(JdbcClient.Rows::list);
                        }), is(expected));
                    }
                }
                return Boolean.TRUE;
            });
            IllegalStateException failure = new IllegalStateException("The configured cursor mapper rejects the row.");
            Consumer<JdbcClient.CallResult> action = result -> {
                result.skipRemainingResults();
                assertThat(assertThrows(IllegalStateException.class, () -> result.cursor("return", row -> {
                    throw failure;
                }).list()), sameInstance(failure));
            };
            assertThrows(TxException.class, () -> Tx.transaction(() -> {
                assertThrows(IllegalStateException.class, () -> {
                    if (generated()) {
                        repository.configuredCursorCallback(1, action);
                    } else {
                        definition.get().bind("input", 1).call().returns(JDBCType.REF_CURSOR).consume(action);
                    }
                });
                return Boolean.TRUE;
            }));
            assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX").map(Integer.class).one() > 0, is(true));
        }
    }

    @Test
    protected void consumesConfiguredFunctionReturnWithOutputsAndCursor() {
        if (vendor().equals("mysql")) {
            DataException failure = assertThrows(DataException.class, () -> client.create(
                    "CREATE FUNCTION sp_function_out(p_input INTEGER, OUT value INTEGER) RETURNS INTEGER "
                            + "DETERMINISTIC RETURN p_input").execute());
            assertThat(failure.getMessage(), containsString("42000"));
            return;
        }
        if (vendor().equals("pgsql")) {
            // The current PostgreSQL 13 fixture supports INOUT but rejects OUT procedure parameters.
            DataException procedure = assertThrows(DataException.class, () -> client.create(
                    "CREATE OR REPLACE PROCEDURE sp_out(OUT value INTEGER) LANGUAGE plpgsql "
                            + "AS $$ BEGIN value := 42; END $$").execute());
            assertThat(procedure.getMessage(), containsString("0A000"));
            DataException failure = assertThrows(DataException.class, () -> client.create(
                    "CREATE OR REPLACE FUNCTION sp_function_out(p_input INTEGER, OUT value INTEGER, "
                            + "OUT other VARCHAR) "
                            + "RETURNS INTEGER LANGUAGE plpgsql AS $$ BEGIN value := p_input; other := 'done'; END $$").execute());
            assertThat(failure.getMessage(), containsString("42P13"));
            return;
        }
        for (String variant : List.of("named", "positional")) {
            Config sql = restartConfiguredCalls(variant);
            for (int input : List.of(-1, 0, 41)) {
                Consumer<JdbcClient.CallResult> action = result -> {
                    result.skipRemainingResults();
                    assertThat(result.get("return", Integer.class), is(input));
                    assertThat(result.get("value", Integer.class), is(input + 1));
                    if (input > 0) {
                        assertThat(result.cursor("rows", row -> row.get(1, Integer.class)).list(), is(List.of(42)));
                    } else {
                        Optional<List<Integer>> expected = input < 0 ? Optional.empty() : Optional.of(List.of());
                        assertThat(result.optionalCursor("rows", row -> row.get(1, Integer.class))
                                .map(JdbcClient.Rows::list), is(expected));
                    }
                };
                Tx.transaction(() -> {
                    client.prepare(sql.get("mixed").asString().get(), List.of("return", "input", "value", "rows")).get()
                            .bind("input", input).bind("value", 1).call().out("rows", JDBCType.REF_CURSOR)
                            .returns(JDBCType.INTEGER).out("value", JDBCType.INTEGER).consume(action);
                    return Boolean.TRUE;
                });
            }
        }
    }

    @Test
    protected void configuredProceduresPreserveLeadingNamedOutputs() {
        for (String variant : List.of("named", "positional")) {
            Config sql = restartConfiguredCalls(variant);
            if (generated()) {
                assertThat(manager.registry().get(CallableRoutineRepository.class).configuredLeadingOutputs(41, "before", 1),
                           is(new CallableRoutineRepository.ConfiguredValues("done", 42)));
            } else {
                JdbcClient.OutParameters result = client.prepare(sql.get("leading-outputs").asString().get(),
                        List.of("other", "value", "input")).get().bind("input", 41).bind("other", "before").bind("value", 1)
                        .call().out("value", JDBCType.INTEGER).out("other", JDBCType.VARCHAR).outputs();
                assertThat(result.get("other", String.class), is("done"));
                assertThat(result.get("value", Integer.class), is(42));
            }
        }
    }

    @Test
    protected void materializesConfiguredCursorOutputRecordsInBothStyles() {
        if (vendor().equals("mysql")) {
            DataException failure = assertThrows(DataException.class, () -> client.prepare("{? = call sp_add(:input)}").get()
                    .bind("input", 1).call().returns(JDBCType.REF_CURSOR).consume(result -> {
                        result.skipRemainingResults();
                        result.cursor("return", row -> row.get(1, Integer.class)).list();
                    }));
            assertThat(failure.getMessage(), containsString("callable"));
            return;
        }
        for (String variant : List.of("named", "positional")) {
            Config sql = restartConfiguredCalls(variant, true);
            if (vendor().equals("pgsql")) {
                client.create("CREATE OR REPLACE FUNCTION sp_bundle(p_input INTEGER, OUT value INTEGER, OUT p_rows refcursor) "
                        + "LANGUAGE plpgsql AS $$ BEGIN value := p_input + 1; IF p_input < 0 THEN p_rows := NULL; "
                        + "ELSE OPEN p_rows FOR SELECT 42 AS ID WHERE p_input > 0; END IF; END $$").execute();
            }
            var definition = client.prepare(sql.get("bundle").asString().get(), List.of("input", "value", "rows"));
            Tx.transaction(() -> {
                for (int input : List.of(-1, 0, 41)) {
                    Optional<List<CallableRoutineRepository.Projection>> rows = input < 0 ? Optional.empty()
                            : Optional.of(input == 0 ? List.of() : List.of(new CallableRoutineRepository.Projection(42)));
                    CallableRoutineRepository.InferredBundle expected =
                            new CallableRoutineRepository.InferredBundle(rows, input + 1);
                    if (generated()) {
                        assertThat(manager.registry().get(CallableRoutineRepository.class).configuredBundle(input), is(expected));
                    } else {
                        CallableRoutineRepository.InferredBundle actual = definition.get().bind("input", input).call()
                                .out("value", JDBCType.INTEGER).out("rows", JDBCType.REF_CURSOR).map(result -> {
                                    result.skipRemainingResults();
                                    int value = result.get("value", Integer.class);
                                    Optional<List<CallableRoutineRepository.Projection>> records = result.optionalCursor("rows",
                                            row -> new CallableRoutineRepository.Projection(row.get("id", Integer.class)))
                                            .map(JdbcClient.Rows::list);
                                    return new CallableRoutineRepository.InferredBundle(records, value);
                                });
                        assertThat(actual, is(expected));
                    }
                }
                return Boolean.TRUE;
            });
        }
    }

    @Test
    protected void distinguishesCaughtClientFailuresFromJoinedBoundaryFailures() {
        for (boolean joined : List.of(false, true)) {
            String name = joined ? "caught_joined_keys" : "caught_direct_keys";
            client.create("DELETE FROM CONTACT WHERE NAME = ?").bind(1, name).execute();
            IllegalStateException failure = new IllegalStateException("The test rejects a generated key row.");
            Runnable operation = () -> {
                JdbcClient.GeneratedKeys keys = client.create("INSERT INTO CONTACT (NAME) VALUES (?)")
                        .bind(1, name).generatedKeys();
                if (vendor().equals("oracle")) {
                    keys.addColumn("ID");
                }
                keys.map(_ -> { throw failure; }).one();
            };
            Callable<Boolean> task = () -> {
                if (joined) {
                    assertThrows(TxException.class, () -> Tx.transaction(() -> {
                        operation.run();
                        return Boolean.TRUE;
                    }));
                } else {
                    assertThat(assertThrows(IllegalStateException.class, operation::run), sameInstance(failure));
                }
                return Boolean.TRUE;
            };
            if (joined) {
                assertThrows(TxException.class, () -> Tx.transaction(task));
            } else {
                assertThat(Tx.transaction(task), is(true));
            }
            assertThat(client.create("SELECT COUNT(*) FROM CONTACT WHERE NAME = ?").bind(1, name)
                               .map(Long.class).one(), is(joined ? 0L : 1L));
        }
    }

    @Test
    protected void materializesAggregatesWithTheDriverFunctionProfile() {
        if (vendor().equals("mysql")) {
            assertThrows(DataException.class, () -> client.create("{? = call sp_add(?)}").bind(2, 41).call()
                    .out("return", 1, JDBCType.REF_CURSOR).consume(result -> {
                        result.skipRemainingResults();
                        result.cursor("return", row -> row.get(1, Integer.class)).list();
                    }));
            return;
        }
        if (vendor().equals("pgsql")) {
            manager.shutdown();
            TestConfigFactory.reset();
            Map<String, String> values = new HashMap<>(configuration().asMap().get());
            values.compute("data.clients.jdbc.0.connection.url", (_, url) -> url + "?escapeSyntaxCallMode=select");
            TestConfigFactory.config(Config.just(ConfigSources.create(values)));
            manager = ServiceRegistryManager.start();
            client = manager.registry().get(JdbcClient.class);
            // PostgreSQL OUT functions need select mode. Procedure mode requires bindings for every INOUT input.
            client.create("CREATE OR REPLACE FUNCTION sp_bundle(p_input INTEGER, OUT value INTEGER, OUT p_rows refcursor) "
                                  + "LANGUAGE plpgsql AS $$ BEGIN value := p_input + 1; "
                                  + "IF p_input < 0 THEN p_rows := NULL; ELSE OPEN p_rows FOR SELECT 42 AS ID "
                                  + "WHERE p_input > 0; END IF; END $$").execute();
        }
        Tx.transaction(() -> {
            if (generated()) {
                CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
                assertThat(repository.configuredBundle(41), is(new CallableRoutineRepository.InferredBundle(
                        Optional.of(List.of(new CallableRoutineRepository.Projection(42))), 42)));
                assertThat(repository.configuredBundle(-1),
                           is(new CallableRoutineRepository.InferredBundle(Optional.empty(), 0)));
                assertThat(repository.configuredMappedBundle(41, result -> {
                    result.skipRemainingResults();
                    int value = result.get("value", Integer.class);
                    return new CallableRoutineRepository.Bundle(
                            result.cursor("rows", row -> row.get(1, Integer.class)).list(), value);
                }), is(new CallableRoutineRepository.Bundle(List.of(42), 42)));
                assertThat(repository.recordBundle(41), is(new CallableRoutineRepository.RecordBundle(
                        List.of(new CallableRoutineRepository.Projection(42)), 42)));
                assertThat(repository.aggregateOptionalCursor(-1),
                           is(new CallableRoutineRepository.OptionalBundle(Optional.empty(), 0)));
                assertThat(repository.aggregateOptionalCursor(0),
                           is(new CallableRoutineRepository.OptionalBundle(Optional.of(List.of()), 1)));
                assertThat(repository.mappedBundle(41, result -> {
                    result.skipRemainingResults();
                    int value = result.get("value", Integer.class);
                    List<Integer> rows = result.cursor("rows", new CallableRoutineRepository.IntegerMapper()).list();
                    return new CallableRoutineRepository.Bundle(rows, value);
                }), is(new CallableRoutineRepository.Bundle(List.of(42), 42)));
            } else {
                CallableRoutineRepository.RecordBundle bundle = client.create("{call sp_bundle(?, ?, ?)}")
                        .bind(1, 41).call().out("value", 2, JDBCType.INTEGER).out("rows", 3, JDBCType.REF_CURSOR)
                        .map(result -> {
                            result.skipRemainingResults();
                            int value = result.get("value", Integer.class);
                            List<CallableRoutineRepository.Projection> rows = result.cursor("rows", row ->
                                    new CallableRoutineRepository.Projection(row.get("id", Integer.class))).list();
                            return new CallableRoutineRepository.RecordBundle(rows, value);
                        });
                assertThat(bundle, is(new CallableRoutineRepository.RecordBundle(
                        List.of(new CallableRoutineRepository.Projection(42)), 42)));
            }
            return Boolean.TRUE;
        });
    }

    @Test
    protected void scalarReturnAndNullableInput() {
        if (generated()) {
            CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
            assertThat(repository.scalar(41), is(42));
            assertThat(repository.nullable(null), is(Optional.empty()));
            assertThat(repository.mapped(41, result -> {
                result.skipRemainingResults();
                return result.get("return", Integer.class).toString();
            }), is("42"));
        } else {
            JdbcClient.OutParameters outputs = client.create("{? = call sp_add(?)}").bind(2, 41).call()
                    .out("return", 1, JDBCType.INTEGER).outputs();
            assertThat(outputs.get("return", Integer.class), is(42));
        }
    }

    /**
     * Inferred generated registrations preserve scalar, nullable, INOUT, and aggregate behavior across drivers.
     */
    @Test
    protected void inferredScalarOutputsAndTypedNullInout() {
        if (generated()) {
            CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
            assertThat(repository.inferredScalar(41), is(repository.scalar(41)));
            assertThat(repository.inferredNullable(null), is(repository.nullable(null)));
            assertThat(repository.inferredNullable(41), is(Optional.of(42)));
            assertThat(repository.inferredInout(2, 40), is(Optional.of(repository.inout(2, 40))));
            assertThat(repository.inferredInout(2, null), is(Optional.empty()));
            assertThat(repository.inferredDecimal(41).compareTo(new BigDecimal("42.25")), is(0));
            assertThat(repository.inferredAggregate(41, null, null),
                       is(new CallableRoutineRepository.InferredValues(42, "done")));
        } else {
            JdbcClient.Statement statement = client.create("{call sp_inout(?, ?)}").bind(1, 2);
            GeneratedJdbcData.bindNull(statement, 2, JDBCType.INTEGER);
            assertThat(statement.call().out("value", 2, JDBCType.INTEGER).outputs().optional("value", Integer.class),
                       is(Optional.empty()));
            assertThat(client.create("{? = call sp_decimal(?)}").bind(2, 41).call().out("return", 1, JDBCType.DECIMAL, 2)
                               .outputs().get("return", BigDecimal.class).compareTo(new BigDecimal("42.25")), is(0));
        }
        assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX").map(Integer.class).one(), is(1));
    }

    /**
     * Inferred cursors retain required and optional presence and report unsupported MySQL registration.
     */
    @Test
    protected void inferredCursorShapesAndUnsupportedCapability() {
        if (vendor().equals("mysql")) {
            DataException failure = assertThrows(DataException.class, () -> {
                if (generated()) {
                    manager.registry().get(CallableRoutineRepository.class).inferredUnsupportedCursor(41);
                } else {
                    client.create("{? = call sp_add(?)}").bind(2, 41).call().out("return", 1, JDBCType.REF_CURSOR)
                            .consume(result -> {
                                Objects.requireNonNull(result, "The callable result must not be null.");
                                result.skipRemainingResults();
                                result.cursor("return", row -> row.get(1, Integer.class)).list();
                            });
                }
            });
            assertThat(failure.getMessage(), containsString("callable"));
        } else {
            Tx.transaction(Tx.Type.REQUIRED, () -> {
                if (generated()) {
                    CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
                    assertThat(repository.inferredCursor(1), is(List.of(new CallableRoutineRepository.Projection(42))));
                    assertThat(repository.inferredOptionalCursor(-1), is(Optional.empty()));
                    assertThat(repository.inferredOptionalCursor(0), is(Optional.of(List.of())));
                    assertThat(repository.inferredOptionalCursor(1),
                               is(Optional.of(List.of(new CallableRoutineRepository.Projection(42)))));
                    if (vendor().equals("oracle")) {
                        assertThat(repository.inferredBundle(-1),
                                   is(new CallableRoutineRepository.InferredBundle(Optional.empty(), 0)));
                        assertThat(repository.inferredBundle(0),
                                   is(new CallableRoutineRepository.InferredBundle(Optional.of(List.of()), 1)));
                        assertThat(repository.inferredBundle(41),
                                   is(new CallableRoutineRepository.InferredBundle(
                                           Optional.of(List.of(new CallableRoutineRepository.Projection(42))), 42)));
                    }
                } else {
                    client.create("{? = call sp_cursor(?)}").bind(2, 1).call().out("return", 1, JDBCType.REF_CURSOR)
                            .consume(result -> {
                                Objects.requireNonNull(result, "The callable result must not be null.");
                                result.skipRemainingResults();
                                assertThat(result.cursor("return", row -> row.get(1, Integer.class)).list(), is(List.of(42)));
                            });
                }
                return null;
            });
        }
        assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX").map(Integer.class).one(), is(1));
    }

    /**
     * Caught inferred cursor mapping failures still roll back the transaction and release the connection reservation.
     */
    @Test
    protected void inferredMappingFailureRollsBackAndReleasesPool() {
        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            client.create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (?)").bind(1, "inferred-failure").execute();
            if (vendor().equals("mysql")) {
                assertThrows(DataException.class, () -> {
                    if (generated()) {
                        manager.registry().get(CallableRoutineRepository.class).inferredUnsupportedCursor(41);
                    } else {
                        client.create("{? = call sp_add(?)}").bind(2, 41).call()
                                .out("return", 1, JDBCType.REF_CURSOR).execute();
                    }
                });
            } else if (generated()) {
                assertThrows(IllegalArgumentException.class,
                             () -> manager.registry().get(CallableRoutineRepository.class).inferredMappingFailure(1));
            } else {
                assertThrows(IllegalArgumentException.class, () -> client.create("{? = call sp_cursor(?)}").bind(2, 1).call()
                        .out("return", 1, JDBCType.REF_CURSOR).consume(result -> {
                            Objects.requireNonNull(result, "The callable result must not be null.");
                            result.skipRemainingResults();
                            result.cursor("return", row -> new CallableRoutineRepository.RejectedProjection(
                                    row.get(1, Integer.class))).one();
                        }));
            }
            return null;
        }));
        assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?").bind(1, "inferred-failure")
                           .map(Integer.class).one(), is(0));
        assertThat(client.create("{? = call sp_add(?)}").bind(2, 41).call().out("return", 1, JDBCType.INTEGER)
                           .outputs().get("return", Integer.class), is(42));
    }

    @Test
    protected void scalarOutputsAndSnapshot() {
        if (generated()) {
            CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
            assertThat(repository.aggregate(41, null, null), is(new CallableRoutineRepository.Values(42, "done")));
            assertThat(repository.recordBundle_OUTPUT_0("baseline"),
                       is(new CallableRoutineRepository.QueryProjection("baseline")));
            assertThat(repository.snapshot(41, null, null).get("value", Integer.class), is(42));
            repository.consume(41, null, null, result -> {
                result.skipRemainingResults();
                assertThat(result.get("other", String.class), is("done"));
            });
        } else {
            JdbcClient.OutParameters result = client.create("{call sp_values(?, ?, ?)}").bind(1, 41).bind(2, 0).bind(3, "unused").call()
                    .out("value", 2, JDBCType.INTEGER).out("other", 3, JDBCType.VARCHAR).outputs();
            assertThat(result.get("value", Integer.class), is(42));
            assertThat(result.get("other", String.class), is("done"));
        }
    }

    /**
     * Detached timestamps retain fractional precision. Unsupported callable TIME conversions remain explicit driver failures.
     */
    @Test
    protected void detachedTemporalPrecisionAndDriverLimits() {
        LocalDateTime expected = LocalDateTime.of(2026, 10, 28, 12, 13, 14, 123456000);
        assertThat(client.create("{? = call sp_stamp()}").call().out("return", 1, JDBCType.TIMESTAMP)
                           .outputs().get("return", LocalDateTime.class), is(expected));
        if (generated()) {
            CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
            assertThat(repository.stamp().get("return", LocalDateTime.class), is(expected));
            assertThat(repository.configuredStamp().get("return", LocalDateTime.class), is(expected));
        }
        if (vendor().equals("pgsql")) {
            DataException failure = assertThrows(DataException.class, () -> client.create("{? = call sp_time()}").call()
                    .out("return", 1, JDBCType.TIME).outputs());
            assertThat(failure.getMessage(), containsString("22023"));
        } else if (vendor().equals("mysql")) {
            assertThat(client.create("{? = call sp_time()}").call().out("return", 1, JDBCType.TIME)
                               .outputs().get("return", LocalTime.class), is(expected.toLocalTime()));
        }
        assertThat(client.create("{? = call sp_add(?)}").bind(2, 41).call().out("return", 1, JDBCType.INTEGER)
                           .outputs().get("return", Integer.class), is(42));
    }

    @Test
    protected void inoutAndRegistration() {
        if (generated()) {
            assertThat(manager.registry().get(CallableRoutineRepository.class).inout(2, 40), is(42));
        } else {
            assertThat(client.create("{call sp_inout(?, ?)}").bind(1, 2).bind(2, 40).call()
                               .out("value", 2, JDBCType.INTEGER).outputs().get("value", Integer.class), is(42));
        }
    }

    /**
     * MySQL direct results remain separate events and later events are discarded by automatic mapping.
     */
    @Test
    protected void directResultsOrMissingDirectResultDiagnostic() {
        if (vendor().equals("mysql")) {
            if (generated()) {
                assertThat(manager.registry().get(CallableRoutineRepository.class).direct(), is(42));
            } else {
                client.create("{call sp_direct()}").call().consume(result -> {
                    assertThat(result.rows(row -> row.get(1, Integer.class)).one(), is(42));
                    result.next();
                    assertThat(result.rows(row -> row.get(1, Integer.class)).one(), is(43));
                    result.skipRemainingResults();
                    assertThat(result.resultKind(), is(JdbcClient.CallResultKind.END));
                });
            }
        } else if (generated()) {
            assertThrows(DataException.class, () -> manager.registry().get(CallableRoutineRepository.class).noDirect());
        } else {
            client.create("{? = call sp_add(?)}").bind(2, 41).call().out("return", 1, JDBCType.INTEGER)
                    .consume(result -> {
                        result.skipRemainingResults();
                        assertThrows(IllegalStateException.class, () -> result.rows(row -> row.get(1, Integer.class)));
                    });
        }
    }

    /**
     * Implicit routine commits cannot be undone by rollback and are outside the supported local transaction contract.
     */
    @Test
    protected void transactionControllingRoutinesRemainUnsupported() {
        AtomicBoolean routineCompleted = new AtomicBoolean();
        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            client.create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (?)").bind(1, "implicit-commit").execute();
            client.create("{call sp_ddl()}").call().execute();
            routineCompleted.set(true);
            throw new IllegalArgumentException("The test requests rollback after the unsupported routine.");
        }));
        assertThat(routineCompleted.get(), is(!vendor().equals("pgsql")));
        // This operation obtains an independent connection after the transaction boundary completes.
        assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?").bind(1, "implicit-commit")
                           .map(Integer.class).one(), is(vendor().equals("pgsql") ? 0 : 1));
        if (!vendor().equals("pgsql")) {
            client.create("DROP TABLE SP_DDL_PROBE").execute();
        }
    }

    /**
     * Cursor tests use an explicit local transaction. MySQL must reject REF_CURSOR without substitution.
     */
    @Test
    protected void cursorPresenceAndMaterialization() {
        if (vendor().equals("mysql")) {
            assertThrows(DataException.class, () -> client.create("{? = call sp_add(?)}").bind(2, 41).call()
                    .out("return", 1, JDBCType.REF_CURSOR).consume(result -> {
                        result.skipRemainingResults();
                        result.cursor("return", row -> row.get(1, Integer.class)).list();
                    }));
            return;
        }
        Tx.transaction(Tx.Type.REQUIRED, () -> {
            if (generated()) {
                CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
                assertThat(repository.cursor(1), is(List.of(42)));
                assertThat(repository.cursor(0), is(List.of()));
                assertThat(repository.cursor(2), is(List.of(42, 43)));
                assertThat(repository.cursorOne(1), is(42));
                assertThat(repository.cursorOptional(0), is(Optional.empty()));
                assertThat(repository.cursorOptional(1), is(Optional.of(42)));
                assertThat(repository.optionalCursor(-1), is(Optional.empty()));
                assertThat(repository.optionalCursor(0), is(Optional.of(List.of())));
                if (vendor().equals("oracle")) {
                    assertThat(repository.recordBundle(41),
                               is(new CallableRoutineRepository.RecordBundle(
                                       List.of(new CallableRoutineRepository.Projection(42)), 42)));
                    assertThat(repository.aggregateCursor(41), is(new CallableRoutineRepository.Bundle(List.of(42), 42)));
                    assertThat(repository.pair(), is(new CallableRoutineRepository.Pair(
                            List.of(new CallableRoutineRepository.Projection(44)), 7,
                            List.of(new CallableRoutineRepository.Projection(42),
                                    new CallableRoutineRepository.Projection(43)))));
                    assertThat(repository.mappedBundle(41, result -> {
                        result.skipRemainingResults();
                        int value = result.get("value", Integer.class);
                        List<Integer> rows = result.cursor("rows", new CallableRoutineRepository.IntegerMapper()).list();
                        return new CallableRoutineRepository.Bundle(rows, value);
                    }), is(new CallableRoutineRepository.Bundle(List.of(42), 42)));
                    assertThat(repository.aggregateOptionalCursor(-1),
                               is(new CallableRoutineRepository.OptionalBundle(Optional.empty(), 0)));
                    assertThat(repository.aggregateOptionalCursor(0),
                               is(new CallableRoutineRepository.OptionalBundle(Optional.of(List.of()), 1)));
                    assertThat(repository.aggregateOptionalCursor(41),
                               is(new CallableRoutineRepository.OptionalBundle(Optional.of(List.of(42)), 42)));
                }
            } else {
                for (int input : new int[] {1, 0, -1}) {
                    client.create("{? = call sp_cursor(?)}").bind(2, input).call()
                            .out("return", 1, JDBCType.REF_CURSOR).consume(result -> {
                                result.skipRemainingResults();
                                Optional<JdbcClient.Rows<Integer>> cursor =
                                        result.optionalCursor("return", row -> row.get(1, Integer.class));
                                result.next();
                                result.skipRemainingResults();
                                if (input < 0) {
                                    assertThat(cursor, is(Optional.empty()));
                                } else {
                                    assertThat(cursor.orElseThrow().list(), is(input == 0 ? List.of() : List.of(42)));
                                }
                            });
                }
            }
            return null;
        });
    }

    /**
     * The first mapper error survives a later callback error and forces rollback even when the terminal is caught.
     */
    @Test
    protected void retainsFirstErrorAndReleasesTransactionReservation() {
        Error first = new AssertionError("The callable row mapper failed.");
        Error later = new AssertionError("The callable callback failed later.");
        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            client.create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (?)").bind(1, "call-error").execute();
            JdbcClient.Call call;
            if (vendor().equals("mysql")) {
                call = client.create("{call sp_direct()}").call();
            } else {
                call = client.create("{? = call sp_cursor(?)}").bind(2, 1).call()
                        .out("return", 1, JDBCType.REF_CURSOR);
            }
            Error observed = assertThrows(Error.class, () -> call.consume(result -> {
                Objects.requireNonNull(result, "The callable result must not be null.");
                JdbcClient.RowMapper<Integer> mapper = row -> {
                    Objects.requireNonNull(row, "The current row must not be null.");
                    row.get(1, Integer.class);
                    throw first;
                };
                JdbcClient.Rows<Integer> rows;
                if (vendor().equals("mysql")) {
                    rows = result.rows(mapper);
                } else {
                    result.skipRemainingResults();
                    rows = result.cursor("return", mapper);
                }
                assertThat(assertThrows(Error.class, rows::list), sameInstance(first));
                throw later;
            }));
            assertThat(observed, sameInstance(first));
            assertThat(observed.getSuppressed().length, is(1));
            assertThat(observed.getSuppressed()[0], sameInstance(later));
            // The terminal has released its reservation while the transaction remains marked for rollback.
            assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?").bind(1, "call-error")
                               .map(Integer.class).one(), is(1));
            return null;
        }));
        assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?").bind(1, "call-error")
                           .map(Integer.class).one(), is(0));
    }

    /**
     * Required cursor cardinality and NULL contracts fail through transaction completion without retaining a lease.
     */
    @Test
    protected void cursorCardinalityFailures() {
        if (vendor().equals("mysql")) {
            assertThrows(DataException.class, () -> client.create("{? = call sp_add(?)}").bind(2, 1).call()
                    .out("return", 1, JDBCType.REF_CURSOR).consume(result -> {
                        result.skipRemainingResults();
                        result.cursor("return", row -> row.get(1, Integer.class)).one();
                    }));
            return;
        }
        for (int input : new int[] {-1, 0, 2}) {
            assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                if (generated()) {
                    manager.registry().get(CallableRoutineRepository.class).cursorOne(input);
                } else {
                    client.create("{? = call sp_cursor(?)}").bind(2, input).call()
                            .out("return", 1, JDBCType.REF_CURSOR).consume(result -> {
                                result.skipRemainingResults();
                                if (input < 0) {
                                    assertThrows(IllegalArgumentException.class,
                                                 () -> result.cursor("return", row -> row.get(1, Integer.class)).one());
                                } else if (input == 0) {
                                    assertThrows(NoResultException.class,
                                                 () -> result.cursor("return", row -> row.get(1, Integer.class)).one());
                                } else {
                                    assertThrows(NonUniqueResultException.class,
                                                 () -> result.cursor("return", row -> row.get(1, Integer.class)).one());
                                }
                            });
                }
                return null;
            }));
        }
    }

    /**
     * A callback keeps the transaction connection reserved even at END and rejects suspension before association changes.
     */
    @Test
    protected void connectionReservationAndRelease() {
        Tx.transaction(Tx.Type.REQUIRED, () -> {
            client.create("{? = call sp_add(?)}").bind(2, 41).call().out("return", 1, JDBCType.INTEGER)
                    .consume(result -> {
                        result.skipRemainingResults();
                        assertThat(result.get("return", Integer.class), is(42));
                        assertThrows(IllegalStateException.class, () -> client.create("SELECT 1").map(Integer.class).one());
                        assertThrows(IllegalStateException.class, () -> Tx.transaction(Tx.Type.NEW, () -> 1));
                        assertThrows(IllegalStateException.class, () -> Tx.transaction(Tx.Type.UNSUPPORTED, () -> 1));
                    });
            assertThat(client.create("{? = call sp_add(?)}").bind(2, 41).call()
                               .out("return", 1, JDBCType.INTEGER).outputs().get("return", Integer.class), is(42));
            return null;
        });
    }

    /**
     * Catching a required output failure cannot make the terminal or its transaction successful.
     */
    @Test
    protected void caughtRequiredNullStillFailsTerminalAndTransaction() {
        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            client.create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (?)").bind(1, "call-latched").execute();
            JdbcClient.Statement stage = client.create("{? = call sp_add(?)}");
            GeneratedJdbcData.bindNull(stage, 2, JDBCType.INTEGER);
            assertThrows(IllegalArgumentException.class, () -> stage.call().out("return", 1, JDBCType.INTEGER)
                    .consume(result -> {
                        result.skipRemainingResults();
                        assertThrows(IllegalArgumentException.class, () -> result.get("return", Integer.class));
                    }));
            return null;
        }));
        assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?").bind(1, "call-latched")
                           .map(Integer.class).one(), is(0));
    }

    /**
     * Real metadata failures caught inside materialized mappers still roll back and release a single pooled connection.
     */
    @Test
    protected void caughtMaterializedMetadataFailureRollsBackAndReleasesPool() throws Exception {
        try (CallableFailureFixture fixture = new CallableFailureFixture(connectionConfig)) {
            for (boolean physicalName : new boolean[] {false, true}) {
                AtomicReference<IllegalStateException> first = new AtomicReference<>();
                assertThrows(TxException.class, () -> fixture.transactions().transaction(Tx.Type.REQUIRED, () -> {
                    fixture.client().create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (?)").bind(1, "metadata-error").execute();
                    fixture.failMetadata(physicalName);
                    IllegalStateException observed = assertThrows(IllegalStateException.class, () ->
                            fixture.consume(generated(), result -> {
                                Objects.requireNonNull(result, "The callable result must not be null.");
                                JdbcClient.Rows<Integer> rows = callRows(result, row -> {
                                    Objects.requireNonNull(row, "The current row must not be null.");
                                    assertThrows(IllegalArgumentException.class, () -> row.get(" ", Integer.class));
                                    first.set(assertThrows(IllegalStateException.class, () -> row.get("VALUE", Integer.class)));
                                    assertThrows(IllegalStateException.class, () -> row.get(1, Integer.class));
                                    return 7;
                                });
                                assertThat(assertThrows(IllegalStateException.class, rows::list), sameInstance(first.get()));
                                assertThrows(IllegalStateException.class, result::resultKind);
                            }));
                    assertThat(observed, sameInstance(first.get()));
                    assertThat(fixture.client().create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?")
                            .bind(1, "metadata-error").map(Integer.class).one(), is(1));
                    return null;
                }));
                assertThat(fixture.activeConnections(), is(0));
                assertThat(fixture.client().create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?")
                        .bind(1, "metadata-error").map(Integer.class).one(), is(0));
            }
        }
    }

    /**
     * Fatal cleanup retains the application exception while real transaction rollback and pool recovery still complete.
     */
    @Test
    protected void fatalCallableCleanupRetainsApplicationAndReleasesPool() throws Exception {
        try (CallableFailureFixture fixture = new CallableFailureFixture(connectionConfig)) {
            RuntimeException application = new IllegalArgumentException("The application failed first.");
            Error fatal = new AssertionError("The callable statement cleanup failed.");
            fixture.failCleanup(fatal);
            assertThat(assertThrows(Error.class, () -> fixture.transactions().transaction(Tx.Type.REQUIRED, () -> {
                fixture.client().create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (?)").bind(1, "cleanup-error").execute();
                fixture.consume(generated(), _ -> { throw application; });
                return null;
            })), sameInstance(fatal));
            assertThat(fatal.getSuppressed(), is(new Throwable[] {application}));
            assertThat(fixture.activeConnections(), is(0));
            assertThat(fixture.client().create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?")
                    .bind(1, "cleanup-error").map(Integer.class).one(), is(0));
            fixture.transactions().transaction(Tx.Type.REQUIRED, () -> {
                fixture.consume(generated(), result -> {
                    Objects.requireNonNull(result, "The callable result must not be null.");
                    result.skipRemainingResults();
                });
                return null;
            });
            assertThat(fixture.activeConnections(), is(0));
        }
    }

    /**
     * Materialization closes a row channel while the parent call keeps its connection reserved.
     */
    @Test
    protected void materializedRowsRespectParentLifetime() {
        Tx.transaction(Tx.Type.REQUIRED, () -> {
            consumeCallRows(2, result -> {
                Objects.requireNonNull(result, "The callable result must not be null.");
                JdbcClient.Rows<Integer> rows = callRows(result, row -> {
                    Objects.requireNonNull(row, "The current row must not be null.");
                    return row.get(1, Integer.class);
                });
                assertThat(rows.list(), is(vendor().equals("mysql") ? List.of(42) : List.of(42, 43)));
                assertThrows(IllegalStateException.class, rows::list);
                assertThrows(IllegalStateException.class,
                             () -> client.create("SELECT 1").map(Integer.class).one());
                assertThrows(IllegalStateException.class, () -> Tx.transaction(Tx.Type.NEW, () -> 1));
                result.skipRemainingResults();
            });
            if (vendor().equals("oracle")) {
                Consumer<JdbcClient.CallResult> pair = result -> {
                    Objects.requireNonNull(result, "The callable result must not be null.");
                    result.skipRemainingResults();
                    assertThat(result.get("value", Integer.class), is(7));
                    assertThat(result.cursor("first", row -> row.get(1, Integer.class)).list().getFirst(), is(42));
                    assertThat(result.optionalCursor("second", row -> row.get(1, Integer.class))
                                       .orElseThrow().list(), is(List.of(44)));
                    assertThat(result.get("value", Integer.class), is(7));
                };
                if (generated()) {
                    manager.registry().get(CallableRoutineRepository.class).consumePair(pair);
                } else {
                    client.create("{call sp_pair(?, ?, ?)}").call()
                            .out("first", 1, JDBCType.REF_CURSOR).out("second", 2, JDBCType.REF_CURSOR)
                            .out("value", 3, JDBCType.INTEGER).consume(pair);
                }
            }
            assertThat(client.create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?").bind(1, "nested")
                               .map(Integer.class).one(), is(0));
            return null;
        });
    }

    @Test
    protected void detachedCanonicalValues() {
        assertAll(canonicalSamples().stream().map(sample -> (Executable) () -> assertAll(sample.type().name(), () -> {
            verifyCanonicalSnapshot(sample, 0);
            if (sample.type() == JDBCType.BOOLEAN || sample.type() == JDBCType.BIT) {
                verifyCanonicalSnapshot(new CanonicalSample(sample.type(), false), 2);
            }
        })));
    }

    @Test
    protected void detachedCanonicalNulls() {
        assertAll(canonicalSamples().stream().map(sample -> (Executable) () ->
                assertAll(sample.type().name(), () -> verifyCanonicalSnapshot(sample, 1))));
    }

    private List<CanonicalSample> canonicalSamples() {
        return List.of(JDBCType.BOOLEAN, JDBCType.BIT, JDBCType.TINYINT, JDBCType.SMALLINT, JDBCType.INTEGER,
                       JDBCType.BIGINT, JDBCType.REAL, JDBCType.FLOAT, JDBCType.DOUBLE, JDBCType.NUMERIC,
                       JDBCType.DECIMAL, JDBCType.CHAR, JDBCType.VARCHAR, JDBCType.LONGVARCHAR, JDBCType.NCHAR,
                       JDBCType.NVARCHAR, JDBCType.LONGNVARCHAR, JDBCType.BINARY, JDBCType.VARBINARY,
                       JDBCType.LONGVARBINARY, JDBCType.DATE, JDBCType.TIME, JDBCType.TIMESTAMP).stream().map(type -> {
                           Object value = switch (type) {
                           case BOOLEAN, BIT -> true;
                           case TINYINT -> (byte) -127;
                           case SMALLINT -> (short) -32767;
                           case INTEGER -> -2147483647;
                           case BIGINT -> 9007199254740993L;
                           case REAL -> 1.25F;
                           case FLOAT, DOUBLE -> 1.125D;
                           case NUMERIC, DECIMAL -> new BigDecimal("1234567890123.123456");
                           case CHAR, NCHAR -> "text";
                           case VARCHAR, LONGVARCHAR, NVARCHAR, LONGNVARCHAR -> "café";
                           case BINARY, VARBINARY, LONGVARBINARY -> new byte[] {0, 1, 127, (byte) 128, (byte) 255};
                           case DATE -> LocalDate.of(2026, 10, 28);
                           case TIME -> LocalTime.of(12, 13, 14, vendor().equals("oracle") ? 0 : 123456000);
                           case TIMESTAMP -> LocalDateTime.of(2026, 10, 28, 12, 13, 14, 123456000);
                           default -> throw new IllegalArgumentException("The sample requires a canonical JDBC type.");
                           };
                           return new CanonicalSample(type, value);
                       }).toList();
    }

    private void verifyCanonicalSnapshot(CanonicalSample sample, int nullMode) throws Exception {
        JDBCType type = sample.type();
        String routine = "sp_snap_" + type.name().toLowerCase(Locale.ROOT);
        String sqlType = switch (vendor()) {
        case "oracle" -> switch (type) {
        case BOOLEAN -> "BOOLEAN";
        case BIT -> "NUMBER";
        case TINYINT, SMALLINT, INTEGER, BIGINT, NUMERIC, DECIMAL -> "NUMBER";
        case REAL -> "BINARY_FLOAT";
        case FLOAT, DOUBLE -> "BINARY_DOUBLE";
        case CHAR -> "CHAR";
        case NCHAR -> "NCHAR";
        case NVARCHAR, LONGNVARCHAR -> "NVARCHAR2";
        case BINARY, VARBINARY, LONGVARBINARY -> "RAW";
        case DATE -> "DATE";
        case TIME, TIMESTAMP -> "TIMESTAMP";
        default -> "VARCHAR2";
        };
        case "pgsql" -> switch (type) {
        case BOOLEAN, BIT -> "BOOLEAN";
        case TINYINT, SMALLINT -> "SMALLINT";
        case INTEGER -> "INTEGER";
        case BIGINT -> "BIGINT";
        case REAL -> "REAL";
        case FLOAT, DOUBLE -> "DOUBLE PRECISION";
        case NUMERIC, DECIMAL -> "NUMERIC(24, 6)";
        case CHAR, NCHAR -> "CHAR(4)";
        case VARCHAR, NVARCHAR -> "VARCHAR(20)";
        case BINARY, VARBINARY, LONGVARBINARY -> "BYTEA";
        case DATE -> "DATE";
        case TIME -> "TIME(6)";
        case TIMESTAMP -> "TIMESTAMP(6)";
        default -> "TEXT";
        };
        case "mysql" -> switch (type) {
        case BOOLEAN, BIT -> "BOOLEAN";
        case TINYINT -> "TINYINT";
        case SMALLINT -> "SMALLINT";
        case INTEGER -> "INTEGER";
        case BIGINT -> "BIGINT";
        case REAL -> "FLOAT";
        case FLOAT, DOUBLE -> "DOUBLE";
        case NUMERIC, DECIMAL -> "DECIMAL(24, 6)";
        case CHAR -> "CHAR(4)";
        case NCHAR -> "NCHAR(4)";
        case VARCHAR -> "VARCHAR(20)";
        case NVARCHAR -> "NVARCHAR(20)";
        case LONGVARCHAR, LONGNVARCHAR -> "TEXT";
        case BINARY -> "BINARY(5)";
        case VARBINARY -> "VARBINARY(5)";
        case LONGVARBINARY -> "BLOB";
        case DATE -> "DATE";
        case TIME -> "TIME(6)";
        case TIMESTAMP -> "DATETIME(6)";
        default -> throw new IllegalArgumentException("The sample requires a canonical JDBC type.");
        };
        default -> throw new IllegalArgumentException("The fixture requires a supported database.");
        };
        String literal = switch (type) {
        case BOOLEAN, BIT -> vendor().equals("oracle") && type == JDBCType.BIT ? "1" : "TRUE";
        case CHAR, NCHAR -> "'text'";
        case VARCHAR, LONGVARCHAR, NVARCHAR, LONGNVARCHAR -> "'café'";
        case BINARY, VARBINARY, LONGVARBINARY -> switch (vendor()) {
        case "oracle" -> "HEXTORAW('00017f80ff')";
        case "pgsql" -> "DECODE('00017f80ff', 'hex')";
        default -> "X'00017f80ff'";
        };
        case DATE -> "DATE '2026-10-28'";
        case TIME -> vendor().equals("oracle") ? "TIMESTAMP '2026-10-28 12:13:14.123456'"
                : "TIME '12:13:14.123456'";
        case TIMESTAMP -> "TIMESTAMP '2026-10-28 12:13:14.123456'";
        default -> sample.value().toString();
        };
        String expression = "CASE WHEN p_null = 1 THEN NULL "
                + ((type == JDBCType.BOOLEAN || type == JDBCType.BIT)
                ? "WHEN p_null = 2 THEN " + (vendor().equals("oracle") && type == JDBCType.BIT ? "0 " : "FALSE ") : "")
                + "ELSE " + literal + " END";
        switch (vendor()) {
        case "oracle" -> client.create("CREATE OR REPLACE FUNCTION " + routine
                + "(p_null IN INTEGER) RETURN " + sqlType + " AS BEGIN RETURN " + expression + "; END;").execute();
        case "pgsql" -> client.create("CREATE OR REPLACE FUNCTION " + routine + "(p_null INTEGER) RETURNS " + sqlType
                + " AS $$ SELECT (" + expression + ")::" + sqlType + " $$ LANGUAGE SQL").execute();
        case "mysql" -> {
            client.create("DROP FUNCTION IF EXISTS " + routine).execute();
            client.create("CREATE FUNCTION " + routine + "(p_null INTEGER) RETURNS " + sqlType
                    + " DETERMINISTIC RETURN " + expression).execute();
        }
        default -> throw new IllegalArgumentException("The fixture requires a supported database.");
        }
        Supplier<JdbcClient.OutParameters> capture = () -> {
            if (generated()) {
                CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
                return switch (type) {
                case BOOLEAN -> repository.snapshotBoolean(nullMode);
                case BIT -> repository.snapshotBit(nullMode);
                case TINYINT -> repository.snapshotTinyint(nullMode);
                case SMALLINT -> repository.snapshotSmallint(nullMode);
                case INTEGER -> repository.snapshotInteger(nullMode);
                case BIGINT -> repository.snapshotBigint(nullMode);
                case REAL -> repository.snapshotReal(nullMode);
                case FLOAT -> repository.snapshotFloat(nullMode);
                case DOUBLE -> repository.snapshotDouble(nullMode);
                case NUMERIC -> repository.snapshotNumeric(nullMode);
                case DECIMAL -> repository.snapshotDecimal(nullMode);
                case CHAR -> repository.snapshotChar(nullMode);
                case VARCHAR -> repository.snapshotVarchar(nullMode);
                case LONGVARCHAR -> repository.snapshotLongvarchar(nullMode);
                case NCHAR -> repository.snapshotNchar(nullMode);
                case NVARCHAR -> repository.snapshotNvarchar(nullMode);
                case LONGNVARCHAR -> repository.snapshotLongnvarchar(nullMode);
                case BINARY -> repository.snapshotBinary(nullMode);
                case VARBINARY -> repository.snapshotVarbinary(nullMode);
                case LONGVARBINARY -> repository.snapshotLongvarbinary(nullMode);
                case DATE -> repository.snapshotDate(nullMode);
                case TIME -> repository.snapshotTime(nullMode);
                case TIMESTAMP -> repository.snapshotTimestamp(nullMode);
                default -> throw new IllegalArgumentException("The sample requires a canonical JDBC type.");
                };
            }
            return client.create("{? = call " + routine + "(?)}").bind(2, nullMode).call()
                    .out("return", 1, type).outputs();
        };
        String description = vendor() + " " + type + " nullMode=" + nullMode + " generated=" + generated();
        if (vendor().equals("pgsql") && (type == JDBCType.NCHAR || type == JDBCType.NVARCHAR
                || type == JDBCType.LONGNVARCHAR || type == JDBCType.TIME)) {
            SQLException nativeFailure;
            String phase;
            try (Connection connection = DriverManager.getConnection(connectionConfig.get("url").asString().get(),
                    connectionConfig.get("username").asString().get(), connectionConfig.get("password").asString().get());
                 CallableStatement statement = connection.prepareCall("{? = call " + routine + "(?)}")) {
                statement.registerOutParameter(1, type.getVendorTypeNumber());
                statement.setInt(2, nullMode);
                if (type == JDBCType.TIME) {
                    statement.execute();
                    phase = "getObject(LocalTime)";
                    nativeFailure = assertThrows(SQLException.class, () -> statement.getObject(1, LocalTime.class));
                } else {
                    phase = "execute";
                    nativeFailure = assertThrows(SQLException.class, statement::execute);
                }
            }
            String state = type == JDBCType.TIME ? "22023" : "42821";
            assertThat(description, nativeFailure.getSQLState(), is(state));
            assertThat(description, nativeFailure.getErrorCode(), is(0));
            DataException failure = assertThrows(DataException.class, capture::get, description);
            assertThat(failure.getMessage(), containsString("JDBC callable"));
            assertThat(failure.getMessage(), containsString("SQLSTATE '" + state + "' and vendor code 0"));
            StringWriter diagnostic = new StringWriter();
            failure.printStackTrace(new PrintWriter(diagnostic));
            assertThat(description, diagnostic.toString().contains(nativeFailure.getMessage()), is(false));
            assertThat(description, diagnostic.toString().contains(routine), is(false));
            assertThat(client.create("{? = call sp_add(?)}").bind(2, 41).call().out("return", 1, JDBCType.INTEGER)
                               .outputs().get("return", Integer.class), is(42));
            System.out.println("Callable snapshot unsupported: " + description + ", native type=" + sqlType
                    + ", phase=" + phase + ", SQLSTATE=" + state + ", vendor code=0");
            return;
        }
        if (vendor().equals("oracle") && type == JDBCType.TIME && nullMode == 0) {
            try (Connection connection = DriverManager.getConnection(connectionConfig.get("url").asString().get(),
                    connectionConfig.get("username").asString().get(), connectionConfig.get("password").asString().get());
                 CallableStatement statement = connection.prepareCall("{? = call " + routine + "(?)}")) {
                statement.registerOutParameter(1, JDBCType.TIMESTAMP.getVendorTypeNumber());
                statement.setInt(2, nullMode);
                statement.execute();
                assertThat(statement.getTimestamp(1).getNanos(), is(123456000));
                try (CallableStatement time = connection.prepareCall("{? = call " + routine + "(?)}")) {
                    time.registerOutParameter(1, JDBCType.TIME.getVendorTypeNumber());
                    time.setInt(2, nullMode);
                    time.execute();
                    assertThat(time.getObject(1, LocalTime.class), is(sample.value()));
                }
            }
            System.out.println("Callable snapshot precision: " + description
                    + ", the driver converts TIMESTAMP to LocalTime at whole seconds.");
        }
        JdbcClient.OutParameters snapshot = capture.get();
        Class<?> canonical = sample.value().getClass();
        Class<?> primitive = switch (type) {
        case BOOLEAN, BIT -> boolean.class;
        case TINYINT -> byte.class;
        case SMALLINT -> short.class;
        case INTEGER -> int.class;
        case BIGINT -> long.class;
        case REAL -> float.class;
        case FLOAT, DOUBLE -> double.class;
        default -> canonical;
        };
        assertThrows(IllegalArgumentException.class, () -> snapshot.get("return", Object.class), description);
        assertThrows(IllegalArgumentException.class, () -> snapshot.optional("return", Object.class), description);
        assertThrows(IllegalArgumentException.class, () -> snapshot.optional("missing", canonical), description);
        if (nullMode == 1) {
            assertThat(description, snapshot.optional("return", canonical), is(Optional.empty()));
            assertThat(description, snapshot.optional("return", primitive), is(Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> snapshot.get("return", canonical), description);
            assertThrows(IllegalArgumentException.class, () -> snapshot.get("return", primitive), description);
        } else {
            assertThat(description, snapshot.get("return", canonical), is(sample.value()));
            assertThat(description, snapshot.optional("return", canonical).orElseThrow(), is(sample.value()));
            assertThat(description, snapshot.get("return", primitive), is(sample.value()));
            if (canonical == byte[].class) {
                snapshot.get("return", byte[].class)[0] = 9;
                snapshot.optional("return", byte[].class).orElseThrow()[1] = 8;
                assertThat(description, capture.get().get("return", byte[].class), is(sample.value()));
                assertThat(description, snapshot.get("return", byte[].class), is(sample.value()));
            }
        }
        List<CompletableFuture<Void>> readers = List.of(1, 2, 3, 4).stream().map(_ -> CompletableFuture.runAsync(() -> {
            for (int read = 0; read < 100; read++) {
                if (nullMode == 1) {
                    assertThat(description, snapshot.optional("return", canonical), is(Optional.empty()));
                } else {
                    assertThat(description, snapshot.get("return", canonical), is(sample.value()));
                    if (canonical == byte[].class) {
                        snapshot.get("return", byte[].class)[0] = 99;
                    }
                }
            }
        })).toList();
        try {
            CompletableFuture.allOf(readers.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        } finally {
            readers.forEach(reader -> reader.cancel(true));
        }
        System.out.println("Callable snapshot supported: " + description);
    }

    private void consumeCallRows(int input, Consumer<JdbcClient.CallResult> action) {
        if (generated()) {
            CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
            if (vendor().equals("mysql")) {
                repository.configuredDirect(action);
            } else {
                repository.configuredCursorCallback(input, action);
            }
        } else if (vendor().equals("mysql")) {
            client.prepare("{call sp_direct()}").get().call().consume(action);
        } else {
            client.prepare("{? = call sp_cursor(:input)}").get().bind("input", input).call()
                    .returns(JDBCType.REF_CURSOR).consume(action);
        }
    }

    private JdbcClient.Rows<Integer> callRows(JdbcClient.CallResult result, JdbcClient.RowMapper<Integer> mapper) {
        if (vendor().equals("mysql")) {
            return result.rows(mapper);
        }
        result.skipRemainingResults();
        return result.cursor("return", mapper);
    }

    private Config restartConfiguredCalls(String variant) {
        return restartConfiguredCalls(variant, false);
    }

    private Config restartConfiguredCalls(String variant, boolean functionsOnly) {
        manager.shutdown();
        TestConfigFactory.reset();
        Map<String, String> values = new HashMap<>(configuration().asMap().get());
        if (vendor().equals("pgsql")) {
            values.compute("data.clients.jdbc.0.connection.url", (_, url) -> url + "?escapeSyntaxCallMode="
                    + (functionsOnly ? "select" : "callIfNoReturn"));
        }
        // Generated repositories keep procedure outputs named while switching only permitted inputs to positional SQL.
        String resource = generated() && variant.equals("positional") ? "positional-inputs" : variant;
        Config sql = Config.just(ConfigSources.classpath("configured-statements/callable/" + resource + "/application.yaml"));
        values.putAll(sql.asMap().get());
        if (vendor().equals("mysql")) {
            // Use an existing scalar function so the failure concerns REF_CURSOR rather than a missing routine.
            values.compute("application.calls.cursor", (_, call) -> call.replace("sp_cursor", "sp_add"));
        }
        Config configured = Config.just(ConfigSources.create(values));
        TestConfigFactory.config(configured);
        manager = ServiceRegistryManager.start();
        client = manager.registry().get(JdbcClient.class);
        return configured.get("application.calls");
    }

    private int committedCount(String value) {
        return client.create("SELECT COUNT(*) FROM TX_MATRIX WHERE DATA_VALUE = ?").bind(1, value).map(Integer.class).one();
    }

    private record CanonicalSample(JDBCType type, Object value) {
    }

}
