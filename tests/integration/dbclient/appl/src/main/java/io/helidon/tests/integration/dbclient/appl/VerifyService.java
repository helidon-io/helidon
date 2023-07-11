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
package io.helidon.tests.integration.dbclient.appl;

import java.lang.System.Logger.Level;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import static io.helidon.tests.integration.dbclient.appl.AbstractService.QUERY_ID_PARAM;
import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource for test data verification.
 */
public class VerifyService implements HttpService {

    private static final System.Logger LOGGER = System.getLogger(VerifyService.class.getName());

    private final DbClient dbClient;
    private final Config config;

    VerifyService(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/getPokemonById", this::getPokemonById)
             .get("/getDatabaseType", this::getDatabaseType)
             .get("/getConfigParam", this::getConfigParam);
    }

    // Get Pokémon by ID and return its data.
    private void getPokemonById(ServerRequest request, ServerResponse response) {
        try {
            DbExecute exec = dbClient.execute();
            String idStr = AbstractService.param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            JsonObject jsonObject =
                    exec.namedGet("get-pokemon-by-id", id)
                        .map(row -> Json.createObjectBuilder()
                                        .add("name", row.column("name").as(String.class))
                                        .add("id", row.column("id").as(Integer.class))
                                        .add("types", exec.namedQuery("get-pokemon-types", id)
                                                          .map(typeRow -> typeRow.as(JsonObject.class))
                                                          .collect(
                                                                  Json::createArrayBuilder,
                                                                  JsonArrayBuilder::add,
                                                                  JsonArrayBuilder::add)
                                                          .build())
                                        .build())
                        .orElse(JsonObject.EMPTY_JSON_OBJECT);
            response.send(okStatus(jsonObject));
        } catch (RemoteTestException ex) {
            response.send(exceptionStatus(ex));
        }
    }

    // Get database type.
    private void getDatabaseType(ServerRequest request, ServerResponse response) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("type", dbClient.dbType());
        response.send(okStatus(job.build()));
    }

    // Get server configuration parameter.
    private void getConfigParam(ServerRequest request, ServerResponse response) {
        String name;
        try {
            name = AbstractService.param(request, AbstractService.QUERY_NAME_PARAM);
        } catch (RemoteTestException ex) {
            LOGGER.log(Level.WARNING,
                    String.format(
                            "Error in VerifyService.getConfigParam on server: %s",
                            ex.getMessage()),
                    ex);
            response.send(exceptionStatus(ex));
            return;
        }
        Config node = config.get(name);
        JsonObjectBuilder job = Json.createObjectBuilder();
        if (!node.exists()) {
            response.send(okStatus(job.build()));
            return;
        }
        job.add("config", node.as(String.class).get());
        response.send(okStatus(job.build()));
    }

}
