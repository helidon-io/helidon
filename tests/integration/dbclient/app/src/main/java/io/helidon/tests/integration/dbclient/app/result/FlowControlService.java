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
package io.helidon.tests.integration.dbclient.app.result;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.AbstractService;
import io.helidon.tests.integration.dbclient.app.model.Type;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Service to test proper flow control handling in query processing.
 */
public class FlowControlService extends AbstractService {

    /**
     * Local logger instance.
     */
    private static final System.Logger LOGGER = System.getLogger(FlowControlService.class.getName());

    /**
     * Creates an instance of web resource to test proper flow control handling in query processing.
     *
     * @param dbClient   DbClient instance
     * @param statements statements from configuration file
     */
    public FlowControlService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testSourceData", this::testSourceData)
                .get("/testFlowControl", this::testFlowControl);
    }

    // Source data verification test.
    // Testing code is blocking, so it's running in a separate thread.
    private void testSourceData(ServerRequest request, ServerResponse response) {
        LOGGER.log(Level.DEBUG, "Running FlowControlService.testSourceData on server");
        Stream<DbRow> rows = dbClient().execute().namedQuery("select-types");
        if (rows == null) {
            throw new RemoteTestException("Rows value is null.");
        }
        List<DbRow> list = rows.toList();
        if (list.isEmpty()) {
            throw new RemoteTestException("Rows list is empty.");
        }
        if (list.size() != 18) {
            throw new RemoteTestException("Rows list size shall be 18.");
        }
        for (DbRow row : list) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            Type type = new Type(id, name);
            if (!Type.TYPES.get(id).getName().equals(name)) {
                throw new RemoteTestException("Expected type name \"%s\", but got \"%s\".",
                        Type.TYPES.get(id).getName(),
                        name);
            }
            LOGGER.log(Level.DEBUG, type::toString);
        }
        response.send(okStatus(JsonObject.EMPTY_JSON_OBJECT));
    }

    // Flow control test.
    // Testing code is blocking, so it's running in a separate thread.
    private void testFlowControl(ServerRequest request, ServerResponse response) {
        LOGGER.log(Level.DEBUG, "Running FlowControlService.testFlowControl on server");
        long count = dbClient().execute().namedQuery("select-types").count();
        LOGGER.log(Level.DEBUG, "Sending OK response.");
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("total", count);
        response.send(okStatus(job.build()));
    }
}
