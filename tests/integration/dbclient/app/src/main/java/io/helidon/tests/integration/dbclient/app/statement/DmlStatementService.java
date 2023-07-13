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
package io.helidon.tests.integration.dbclient.app.statement;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.AbstractService;
import io.helidon.tests.integration.dbclient.app.model.Pokemon;

import jakarta.json.Json;

import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Service to test DbStatementDml methods.
 */
public class DmlStatementService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(DmlStatementService.class.getName());

    public DmlStatementService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest request, ServerResponse response) {
        String testName = pathParam(request, "testName");
        String name = queryParam(request, QUERY_NAME_PARAM);
        int id = Integer.parseInt(queryParam(request, QUERY_ID_PARAM));
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on server", getClass().getSimpleName(), testName));
        long count = switch (testName) {
            case "testDmlArrayParams" -> testDmlArrayParams(id, name);
            case "testDmlListParams" -> testDmlListParams(id, name);
            case "testDmlMapParams" -> testDmlMapParams(id, name);
            case "testDmlOrderParam" -> testDmlOrderParam(id, name);
            case "testDmlNamedParam" -> testDmlNamedParam(id, name);
            case "testDmlMappedNamedParam" -> testDmlMappedNamedParam(id, name);
            case "testDmlMappedOrderParam" -> testDmlMappedOrderParam(id, name);
            default -> throw new NotFoundException("test not found: " + testName);
        };
        response.send(okStatus(Json.createValue(count)));
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    private long testDmlArrayParams(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .params(name, id)
                .execute();
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    private long testDmlListParams(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .params(List.of(name, id))
                .execute();
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    private long testDmlMapParams(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .params(Map.of("name", name, "id", id))
                .execute();
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    private long testDmlOrderParam(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(name)
                .addParam(id)
                .execute();
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    private long testDmlNamedParam(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", name)
                .addParam("id", id)
                .execute();
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    private long testDmlMappedNamedParam(int id, String name) {
        Pokemon pokemon = new Pokemon(id, name, Pokemon.POKEMONS.get(id).getTypesArray());
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    private long testDmlMappedOrderParam(int id, String name) {
        Pokemon pokemon = new Pokemon(id, name, Pokemon.POKEMONS.get(id).getTypesArray());
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .indexedParam(pokemon)
                .execute();
    }
}
