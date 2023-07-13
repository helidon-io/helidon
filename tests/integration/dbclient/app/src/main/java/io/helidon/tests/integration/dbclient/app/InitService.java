/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.app;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbTransaction;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.model.Pokemon;
import io.helidon.tests.integration.dbclient.app.model.Type;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Service for tests initialization services.
 */
public class InitService implements HttpService {

    private static final System.Logger LOGGER = System.getLogger(InitService.class.getName());

    private final DbClient dbClient;
    private final Config dbConfig;

    /**
     * Creates an instance of web resource for tests initialization services.
     *
     * @param dbClient DbClient instance
     * @param dbConfig testing application configuration
     */
    InitService(DbClient dbClient, Config dbConfig) {
        this.dbClient = dbClient;
        this.dbConfig = dbConfig;
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/setup", this::setup)
                .get("/testPing", this::testHealthCheck)
                .get("/testDropSchema", this::testDropSchema)
                .get("/testInitSchema", this::testInitSchema)
                .get("/testInitTypes", this::testInitTypes)
                .get("/testInitPokemons", this::testInitPokemons)
                .get("/testInitPokemonTypes", this::testInitPokemonTypes);
    }

    private void setup(ServerRequest request, ServerResponse response) {
        boolean ping = dbConfig.get("test.ping-dml")
                .map(c -> c.asBoolean().get())
                .orElse(false);
        JsonObjectBuilder data = Json.createObjectBuilder();
        data.add("ping-dml", ping);
        response.send(okStatus(data.build()));
    }

    private void testHealthCheck(ServerRequest request, ServerResponse response) {
        HealthCheck check = DbClientHealthCheck.create(
                dbClient,
                dbConfig.get("health-check"));
        HealthCheckResponse checkResponse = check.call();
        HealthCheckResponse.Status checkState = checkResponse.status();
        JsonObjectBuilder data = Json.createObjectBuilder();
        data.add("state", checkState.name());
        response.send(okStatus(data.build()));
    }

    private void testDropSchema(ServerRequest request, ServerResponse response) {
        DbExecute exec = dbClient.execute();
        long count = 0;
        for (String schema : List.of("poketypes", "pokemons", "types")) {
            String statementName = "drop-" + schema;
            try {
                count += exec.namedDml(statementName);
            } catch (DbClientException ex) {
                LOGGER.log(Level.DEBUG, () -> String.format(
                        "Unable to execute statement: %s, %s",
                        statementName,
                        ex.getMessage()));
            }
        }
        response.send(okStatus(Json.createValue(count)));
    }

    private void testInitSchema(ServerRequest request, ServerResponse response) {
        DbExecute exec = dbClient.execute();
        long count = 0;
        for (String schema : List.of("types", "pokemons", "poketypes")) {
            String statementName = "create-" + schema;
            try {
                count += exec.namedDml(statementName);
            } catch (DbClientException ex) {
                LOGGER.log(Level.DEBUG, () -> String.format(
                        "Unable to execute statement: %s, %s",
                        statementName,
                        ex.getMessage()));
            }
        }
        response.send(okStatus(Json.createValue(count)));
    }

    private void testInitTypes(ServerRequest request, ServerResponse response) {
        DbTransaction tx = dbClient.transaction();
        long count = 0;
        for (Map.Entry<Integer, Type> entry : Type.TYPES.entrySet()) {
            count += tx.namedDml("insert-type", entry.getKey(), entry.getValue().name());
        }
        tx.commit();
        response.send(okStatus(Json.createValue(count)));
    }

    private void testInitPokemons(ServerRequest request, ServerResponse response) {
        DbTransaction tx = dbClient.transaction();
        long count = 0;
        for (Map.Entry<Integer, Pokemon> entry : Pokemon.POKEMONS.entrySet()) {
            count += tx.namedDml("insert-pokemon", entry.getKey(), entry.getValue().getName());
        }
        tx.commit();
        response.send(okStatus(Json.createValue(count)));
    }

    private void testInitPokemonTypes(ServerRequest request, ServerResponse response) {
        DbTransaction tx = dbClient.transaction();
        long count = 0;
        for (Map.Entry<Integer, Pokemon> entry : Pokemon.POKEMONS.entrySet()) {
            Pokemon pokemon = entry.getValue();
            for (Type type : pokemon.getTypes()) {
                count += tx.namedDml("insert-poketype", pokemon.getId(), type.id());
            }
        }
        tx.commit();
        response.send(okStatus(Json.createValue(count)));
    }
}
