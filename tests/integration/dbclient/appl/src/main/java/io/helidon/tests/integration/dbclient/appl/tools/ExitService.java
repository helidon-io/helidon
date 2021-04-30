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
package io.helidon.tests.integration.dbclient.appl.tools;

import java.util.logging.Logger;

import io.helidon.common.http.MediaType;
import io.helidon.tests.integration.dbclient.appl.InitService;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

/**
 * Web resource to terminate web server.
 */
public class ExitService implements Service {

    private static final Logger LOGGER = Logger.getLogger(InitService.class.getName());

    private WebServer server;

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/", this::exit);
    }

    public void setServer(final WebServer server) {
        this.server = server;
    }

    /**
     * Terminates web server.
     * @param request not used
     * @param response where to send server termination message.
     * @return {@code null} value
     */
    public String exit(final ServerRequest request, final ServerResponse response) {
        response.headers().contentType(MediaType.TEXT_PLAIN);
        response.send("Testing web application shutting down.");
        ExitThread.start(server);
        return null;
    }

}
