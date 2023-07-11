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
package io.helidon.tests.integration.dbclient.appl.tools;

import io.helidon.common.http.HttpMediaType;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * Web resource to terminate web server.
 */
public class ExitService implements HttpService {

    private WebServer server;

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::exit);
    }

    public void setServer(WebServer server) {
        this.server = server;
    }

    /**
     * Terminates web server.
     * @param request not used
     * @param response where to send server termination message.
     * @return {@code null} value
     */
    public String exit(ServerRequest request, ServerResponse response) {
        response.headers().contentType(HttpMediaType.TEXT_PLAIN);
        response.send("Testing web application shutting down.");
        ExitThread.start(server);
        return null;
    }

}
