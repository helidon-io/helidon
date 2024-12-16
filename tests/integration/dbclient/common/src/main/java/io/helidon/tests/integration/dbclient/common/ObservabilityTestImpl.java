/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.http.Status;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Types;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.hamcrest.CoreMatchers;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Actual implementation of {@link ObservabilityTest}.
 */
public final class ObservabilityTestImpl extends AbstractTestImpl implements ObservabilityTest {

    private final LazyValue<Http1Client> client;

    /**
     * Create a new instance.
     *
     * @param db             db client
     * @param config         config
     * @param clientSupplier client supplier
     */
    public ObservabilityTestImpl(DbClient db, Config config, Supplier<Http1Client> clientSupplier) {
        super(db, config);
        client = LazyValue.create(clientSupplier);
    }

    @Override
    public void testHttpHealthNoDetails() {
        // Call select-pokemons to warm up server
        List<DbRow> ignored = db.execute().namedQuery("select-pokemons").toList();

        ClientResponseTyped<String> response = get("/noDetails/health").request(String.class);
        assertThat(response.status(), equalTo(Status.NO_CONTENT_204));
    }

    @Override
    public void testHttpHealthDetails() {
        // Call select-pokemons to warm up server
        List<DbRow> ignored = db.execute().namedQuery("select-pokemons").toList();

        // Read and process health check response
        ClientResponseTyped<JsonObject> response = get("/details/health").request(JsonObject.class);
        assertThat(response.status(), equalTo(Status.OK_200));

        JsonArray checks = response.entity().asJsonObject().getJsonArray("checks");
        assertThat(checks.size(), greaterThan(0));
        for (JsonValue check : checks) {
            String status = check.asJsonObject().getString("status");
            assertThat(status, equalTo("UP"));
        }
    }

    @Override
    public void testHttpMetrics() {
        // Read and process metrics response
        JsonObject application = get("/observe/metrics/application")
                .accept(MediaTypes.APPLICATION_JSON)
                .requestEntity(JsonObject.class);

        int origSelectCount = application.getInt("db.counter.select-pokemons", 0);
        int origInsertCount = application.getInt("db.counter.insert-pokemon", 0);
        JsonObject insertTimer = application.getJsonObject("db.timer.insert-pokemon");
        int origInsertTimerCount = insertTimer == null ? 0 : insertTimer.getInt("count", 0);

        // Call select-pokemons
        List<DbRow> ignored = db.execute().namedQuery("select-pokemons").toList();

        // Call insert-pokemon
        Pokemon pokemon = new Pokemon(401, "Lickitung", Types.NORMAL);
        db.execute().namedInsert("insert-pokemon", pokemon.id(), pokemon.name());

        // Read and process metrics response
        application = get("/observe/metrics/application")
                .accept(MediaTypes.APPLICATION_JSON)
                .requestEntity(JsonObject.class);

        int actualSelectCount = application.getInt("db.counter.select-pokemons", 0);
        assertThat(actualSelectCount, is(origSelectCount + 1));

        int actualInsertCount = application.getInt("db.counter.insert-pokemon", 0);
        assertThat(actualInsertCount, equalTo(origInsertCount + 1));
        assertThat(application.containsKey("db.timer.insert-pokemon"), equalTo(true));

        insertTimer = application.getJsonObject("db.timer.insert-pokemon");
        assertThat(insertTimer, is(not(nullValue())));
        assertThat(insertTimer.containsKey("count"), equalTo(true));
        assertThat(insertTimer.containsKey("mean"), equalTo(true));
        assertThat(insertTimer.containsKey("max"), equalTo(true));

        int actualInsertTimerCount = insertTimer.getInt("count");
        assertThat(actualInsertTimerCount, equalTo(origInsertTimerCount + 1));
    }

    @Override
    public void testHealthCheck() {
        HealthCheck check = DbClientHealthCheck.create(db, config.get("db.health-check"));
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    @Override
    public void testHealthCheckWithName() {
        String hcName = "TestHC";
        HealthCheck check = DbClientHealthCheck.builder(db).config(config.get("db.health-check")).name(hcName).build();
        HealthCheckResponse response = check.call();
        String name = check.name();
        HealthCheckResponse.Status state = response.status();
        assertThat(name, equalTo(hcName));
        assertThat(state, equalTo(HealthCheckResponse.Status.UP));
    }

    @Override
    public void testHealthCheckWithCustomNamedDML() {
        HealthCheck check = DbClientHealthCheck.builder(db).dml().statementName("ping-dml").build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    @Override
    public void testHealthCheckWithCustomDML() {
        Config cfgStatement = config.get("db.statements.ping-dml");
        assertThat("Missing ping-dml statement in database configuration!", cfgStatement.exists(), equalTo(true));
        String statement = cfgStatement.asString().get();
        assertThat("Missing ping-dml statement String in database configuration!", statement, CoreMatchers.is(notNullValue()));
        HealthCheck check = DbClientHealthCheck.builder(db).dml().statement(statement).build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    @Override
    public void testHealthCheckWithCustomNamedQuery() {
        HealthCheck check = DbClientHealthCheck.builder(db).query().statementName("ping").build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    @Override
    public void testHealthCheckWithCustomQuery() {
        Config cfgStatement = config.get("db.statements.ping");
        assertThat("Missing ping-query statement in database configuration!", cfgStatement.exists(), equalTo(true));
        String statement = cfgStatement.asString().get();
        assertThat("Missing ping-query statement String in database configuration!", statement, CoreMatchers.is(notNullValue()));
        HealthCheck check = DbClientHealthCheck.builder(db).query().statement(statement).build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    private Http1ClientRequest get(String path) {
        return client.get().get(path);
    }
}
