/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.appl;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.model.Type;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource for tests initialization services.
 */
public class InitService implements Service {

    private static final Logger LOGGER = Logger.getLogger(InitService.class.getName());

    private static boolean pingDml = true;

    private final DbClient dbClient;

    private final Config dbConfig;

    /**
     * Creates an instance of web resource for tests initialization services.
     *
     * @param dbClient DbClient instance
     * @param dbConfig testing application configuration
     */
    InitService(final DbClient dbClient, final Config dbConfig) {
        this.dbClient = dbClient;
        this.dbConfig = dbConfig;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/setup", this::setup)
                .get("/testPing", this::testHealthCheck)
                .get("/testDropSchema", this::testDropSchema)
                .get("/testInitSchema", this::testInitSchema)
                .get("/testInitTypes", this::testInitTypes)
                .get("/testInitPokemons", this::testInitPokemons)
                .get("/testInitPokemonTypes", this::testInitPokemonTypes);
    }

    public static void sendDmlResponse(final ServerResponse response, final Supplier<CompletableFuture<Long>> test) {
        test.get()
                .thenAccept(result -> response.send(okStatus(Json.createValue(result))))
                .exceptionally(t -> {
                    response.send(exceptionStatus(t));
                    return null;
                });
    }

    // Setup tests.
    @SuppressWarnings("null")
    private void setup(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running InitResource.setup on server");
        final Config cfgPingDml = dbConfig.get("test.ping-dml");
        pingDml = cfgPingDml.exists() ? cfgPingDml.asBoolean().get() : true;
        final JsonObjectBuilder data = Json.createObjectBuilder();
        data.add("ping-dml", pingDml);
        response.send(okStatus(data.build()));
    }

    // Database HealthCheck to make sure that database is alive.
    private void testHealthCheck(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running InitResource.testHealthCheck on server");
        HealthCheck check = DbClientHealthCheck.create(
                                dbClient,
                                dbConfig.get("health-check"));
        HealthCheckResponse checkResponse = check.call();
        HealthCheckResponse.State checkState = checkResponse.getState();
        final JsonObjectBuilder data = Json.createObjectBuilder();
        data.add("state", checkState.name());
        response.send(okStatus(data.build()));
    }

    // Drop database schema (tables)
    private void testDropSchema(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running InitResource.testDropSchema on server");
        sendDmlResponse(response,
                () -> dbClient.execute(
                        exec -> exec
                                .namedDml("drop-poketypes")
                                .flatMapSingle(result -> exec.namedDml("drop-pokemons"))
                                .flatMapSingle(result -> exec.namedDml("drop-types"))
                ).toCompletableFuture());
    }

    // Initialize database schema (tables)
    private void testInitSchema(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running InitResource.testInitSchema on server");
        sendDmlResponse(response,
                () -> dbClient.execute(
                        exec -> exec
                                .namedDml("create-types")
                                .flatMapSingle(result -> exec.namedDml("create-pokemons"))
                                .flatMapSingle(result -> exec.namedDml("create-poketypes"))
                ).toCompletableFuture());
    }

    // Initialize pokemon types list
    private void testInitTypes(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running InitResource.testInitTypes on server");
        sendDmlResponse(response,
                () -> dbClient.inTransaction(tx -> {
                    Single<Long> stage = null;
                    for (Map.Entry<Integer, Type> entry : Type.TYPES.entrySet()) {
                        if (stage == null) {
                            stage = tx.namedDml("insert-type", entry.getKey(), entry.getValue().getName());
                        } else {
                            stage = stage.flatMapSingle(result -> tx.namedDml(
                                    "insert-type", entry.getKey(), entry.getValue().getName()));
                        }
                    }
                    return stage;
                }
                ).toCompletableFuture());
    }

    // Initialize pokemons
    private void testInitPokemons(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running InitResource.testInitPokemons on server");
        sendDmlResponse(response,
                () -> dbClient.inTransaction(tx -> {
                    Single<Long> stage = null;
                    for (Map.Entry<Integer, Pokemon> entry : Pokemon.POKEMONS.entrySet()) {
                        if (stage == null) {
                            stage = tx.namedDml("insert-pokemon", entry.getKey(), entry.getValue().getName());
                        } else {
                            stage = stage.flatMapSingle(result -> tx.namedDml(
                                    "insert-pokemon", entry.getKey(), entry.getValue().getName()));
                        }
                    }
                    return stage;
                }
                ).toCompletableFuture());
    }

    // Initialize pokemon types relation
    private void testInitPokemonTypes(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running InitResource.testInitPokemonTypes on server");
        sendDmlResponse(response,
                () -> dbClient.inTransaction(tx -> {
                    Single<Long> stage = null;
                    for (Map.Entry<Integer, Pokemon> entry : Pokemon.POKEMONS.entrySet()) {
                        Pokemon pokemon = entry.getValue();
                        for (Type type : pokemon.getTypes()) {
                            if (stage == null) {
                                stage = tx.namedDml("insert-poketype", pokemon.getId(), type.getId());
                            } else {
                                stage = stage.flatMapSingle(result -> tx.namedDml(
                                        "insert-poketype", pokemon.getId(), type.getId()));
                            }
                        }
                    }
                    return stage;
                }
                ).toCompletableFuture());
    }

}
