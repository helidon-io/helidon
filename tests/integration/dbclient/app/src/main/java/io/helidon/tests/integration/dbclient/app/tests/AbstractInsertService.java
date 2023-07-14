/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.app.tests;

import java.lang.System.Logger.Level;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Type;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;

import static io.helidon.tests.integration.dbclient.common.model.Type.TYPES;
import static io.helidon.tests.integration.harness.AppResponse.okStatus;

/**
 * Base service to test insert statements.
 */
public abstract class AbstractInsertService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(AbstractInsertService.class.getName());
    private static final Map<String, String> POKEMON_NAMES = Collections.unmodifiableMap(initNames());
    private static final Map<String, List<Type>> POKEMON_TYPES = Collections.unmodifiableMap(initTypes());

    @SuppressWarnings("SpellCheckingInspection")
    private static Map<String, String> initNames() {
        Map<String, String> names = new HashMap<>();
        names.put("testCreateNamedInsertStrStrNamedArgs", "Bulbasaur");
        names.put("testCreateNamedInsertStrNamedArgs", "Ivysaur");
        names.put("testCreateNamedInsertStrOrderArgs", "Venusaur");
        names.put("testCreateInsertNamedArgs", "Magby");
        names.put("testCreateInsertOrderArgs", "Magmar");
        names.put("testNamedInsertOrderArgs", "Rattata");
        names.put("testInsertOrderArgs", "Raticate");
        names.put("testCreateNamedDmlWithInsertStrStrNamedArgs", "Torchic");
        names.put("testCreateNamedDmlWithInsertStrNamedArgs", "Combusken");
        names.put("testCreateNamedDmlWithInsertStrOrderArgs", "Treecko");
        names.put("testCreateDmlWithInsertNamedArgs", "Grovyle");
        names.put("testCreateDmlWithInsertOrderArgs", "Sceptile");
        names.put("testNamedDmlWithInsertOrderArgs", "Snover");
        names.put("testDmlWithInsertOrderArgs", "Abomasnow");
        return names;
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static Map<String, List<Type>> initTypes() {
        Map<String, List<Type>> types = new HashMap<>();
        types.put("Bulbasaur", List.of(TYPES.get(4), TYPES.get(12)));
        types.put("Ivysaur", List.of(TYPES.get(4), TYPES.get(12)));
        types.put("Venusaur", List.of(TYPES.get(4), TYPES.get(12)));
        types.put("Magby", List.of(TYPES.get(10)));
        types.put("Magmar", List.of(TYPES.get(10)));
        types.put("Rattata", List.of(TYPES.get(1)));
        types.put("Raticate", List.of(TYPES.get(1)));
        types.put("Torchic", List.of(TYPES.get(10)));
        types.put("Combusken", List.of(TYPES.get(2), TYPES.get(10)));
        types.put("Treecko", List.of(TYPES.get(12)));
        types.put("Grovyle", List.of(TYPES.get(12)));
        types.put("Sceptile", List.of(TYPES.get(12)));
        types.put("Snover", List.of(TYPES.get(12), TYPES.get(15)));
        types.put("Abomasnow", List.of(TYPES.get(12), TYPES.get(15)));
        return types;
    }

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient instance
     * @param statements statements
     */
    public AbstractInsertService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest request, ServerResponse response) {
        String testName = pathParam(request, "testName");
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on server", getClass().getSimpleName(), testName));
        int id = Integer.parseInt(queryParam(request, QueryParams.ID));
        String name = POKEMON_NAMES.get(testName);
        long count = switch (testName) {
            case "testCreateNamedInsertStrStrNamedArgs" -> testCreateNamedInsertStrStrNamedArgs(id, name);
            case "testCreateNamedInsertStrNamedArgs" -> testCreateNamedInsertStrNamedArgs(id, name);
            case "testCreateNamedInsertStrOrderArgs" -> testCreateNamedInsertStrOrderArgs(id, name);
            case "testCreateInsertNamedArgs" -> testCreateInsertNamedArgs(id, name);
            case "testCreateInsertOrderArgs" -> testCreateInsertOrderArgs(id, name);
            case "testNamedInsertOrderArgs" -> testNamedInsertOrderArgs(id, name);
            case "testInsertOrderArgs" -> testInsertOrderArgs(id, name);
            case "testCreateNamedDmlWithInsertStrStrNamedArgs" -> testCreateNamedDmlWithInsertStrStrNamedArgs(id, name);
            case "testCreateNamedDmlWithInsertStrNamedArgs" -> testCreateNamedDmlWithInsertStrNamedArgs(id, name);
            case "testCreateNamedDmlWithInsertStrOrderArgs" -> testCreateNamedDmlWithInsertStrOrderArgs(id, name);
            case "testCreateDmlWithInsertNamedArgs" -> testCreateDmlWithInsertNamedArgs(id, name);
            case "testCreateDmlWithInsertOrderArgs" -> testCreateDmlWithInsertOrderArgs(id, name);
            case "testNamedDmlWithInsertOrderArgs" -> testNamedDmlWithInsertOrderArgs(id, name);
            case "testDmlWithInsertOrderArgs" -> testDmlWithInsertOrderArgs(id, name);
            default -> throw new NotFoundException("test not found: " + testName);
        };
        Pokemon pokemon = new Pokemon(id, name, POKEMON_TYPES.get(name));
        response.send(okStatus(pokemon.toJsonObject()));
    }

    /**
     * Verify {@code createNamedInsert(String, String)} API method with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedInsertStrStrNamedArgs(int id, String name);

    /**
     * Verify {@code createNamedInsert(String)} API method with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedInsertStrNamedArgs(int id, String name);

    /**
     * Verify {@code createNamedInsert(String)} API method with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedInsertStrOrderArgs(int id, String name);

    /**
     * Verify {@code createInsert(String)} API method with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     */
    protected abstract long testCreateInsertNamedArgs(int id, String name);

    /**
     * Verify {@code createInsert(String)} API method with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateInsertOrderArgs(int id, String name);

    /**
     * Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testNamedInsertOrderArgs(int id, String name);

    /**
     * Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testInsertOrderArgs(int id, String name);

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with insert with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithInsertStrStrNamedArgs(int id, String name);

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithInsertStrNamedArgs(int id, String name);

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithInsertStrOrderArgs(int id, String name);

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateDmlWithInsertNamedArgs(int id, String name);

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateDmlWithInsertOrderArgs(int id, String name);

    /**
     * Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testNamedDmlWithInsertOrderArgs(int id, String name);

    /**
     * Verify {@code dml(String)} API method with insert with ordered parameters passed directly
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testDmlWithInsertOrderArgs(int id, String name);
}
