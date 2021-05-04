/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import static io.helidon.tests.integration.dbclient.appl.AbstractService.QUERY_ID_PARAM;
import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
/**
 * Web resource for test data verification.
 */
public class VerifyService  implements Service {

    private static final Logger LOGGER = Logger.getLogger(VerifyService.class.getName());

    private final DbClient dbClient;
    private final Config config;

    VerifyService(final DbClient dbClient, final Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/getPokemonById", this::getPokemonById)
                .get("/getDatabaseType", this::getDatabaseType)
                .get("/getConfigParam", this::getConfigParam);
    }

    // Get Pokemon by ID and return its data.
    private void getPokemonById(final ServerRequest request, final ServerResponse response) {
        try {
            final String idStr = AbstractService.param(request, QUERY_ID_PARAM);
            final int id = Integer.parseInt(idStr);
            final JsonObjectBuilder pokemonBuilder = Json.createObjectBuilder();
            dbClient.execute(
                    exec -> exec
                            .namedGet("get-pokemon-by-id", id))
                    .thenAccept(
                            data -> data.ifPresentOrElse(
                                    row -> {
                                        final JsonArrayBuilder typesBuilder = Json.createArrayBuilder();
                                        pokemonBuilder.add("name", row.column("name").as(String.class));
                                        pokemonBuilder.add("id", row.column("id").as(Integer.class));
                                        dbClient.execute(
                                                exec -> exec
                                                        .namedQuery("get-pokemon-types", id))
                                                .forEach(
                                                        typeRow -> typesBuilder.add(typeRow.as(JsonObject.class)))
                                                .onComplete(() -> {
                                                    pokemonBuilder.add("types", typesBuilder.build());
                                                    response.send(AppResponse.okStatus(pokemonBuilder.build()));
                                                })
                                                .exceptionally(t -> {
                                                    response.send(exceptionStatus(t));
                                                    return null;
                                                });
                                    },
                                    () -> response.send(
                                            AppResponse.okStatus(JsonObject.EMPTY_JSON_OBJECT))))
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException ex) {
            response.send(exceptionStatus(ex));
        }
    }

    // Get database type.
    private void getDatabaseType(final ServerRequest request, final ServerResponse response) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("type", dbClient.dbType());
        response.send(AppResponse.okStatus(job.build()));
    }

    // Get server configuration parameter.
    private void getConfigParam(final ServerRequest request, final ServerResponse response) {
        final String name;
        try {
            name = AbstractService.param(request, AbstractService.QUERY_NAME_PARAM);
        } catch (RemoteTestException ex) {
            LOGGER.fine(() -> String.format(
                    "Error in VerifyService.getConfigParam on server: %s",
                    ex.getMessage()));
            response.send(exceptionStatus(ex));
            return;
        }
        Config node = config.get(name);
        JsonObjectBuilder job = Json.createObjectBuilder();
        if (!node.exists()) {
            response.send(AppResponse.okStatus(job.build()));
            return;
        }
        job.add("config", node.as(String.class).get());
        response.send(AppResponse.okStatus(job.build()));
    }

}
