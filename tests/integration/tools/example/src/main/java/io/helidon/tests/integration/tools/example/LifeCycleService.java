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
package io.helidon.tests.integration.tools.example;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.dbclient.DbClient;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import jakarta.json.Json;

import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to handle web server life cycle.
 */
public class LifeCycleService implements HttpService {

    private WebServer server;
    private final DbClient dbClient;

    /**
     * Creates an instance of web service to handle web server life cycle.
     *
     * @param dbClient DbClient instance
     */
    public LifeCycleService(final DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/init", this::init)
                .get("/exit", this::exit);
    }

    /**
     * Set the server instance to support {@link WebServer#stop()}.
     *
     * @param server server
     */
    public void setServer(WebServer server) {
        this.server = server;
    }

    private void init(ServerRequest request, ServerResponse response) {
        long result;
        result = dbClient.execute().namedDml("create-table");
        result += dbClient.execute().namedDml("insert-person", "Ash", "Ash Ketchum");
        result += dbClient.execute().namedDml("insert-person", "Brock", "Brock Harrison");
        response.send(okStatus(Json.createValue(result)));
    }

    private void exit(ServerRequest request, ServerResponse response) {
        response.headers().contentType(MediaTypes.TEXT_PLAIN);
        response.send("Testing web server shutting down.");
        server.stop();
    }
}
