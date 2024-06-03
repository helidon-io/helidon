/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.gh2631;

import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

public class Gh8818 {

    static final String ENDPOINT_PATH = "/greet";

    public static void main(String[] args) {
        startServer();
    }

    static WebServer startServer() {
        return WebServer.builder()
                .routing(Gh8818::routing)
                .build()
                .start();
    }

    static void routing(HttpRouting.Builder routing) {
        LogConfig.configureRuntime();

        routing.register(ENDPOINT_PATH, new TestResource());
    }

    private static class TestResource implements HttpService {

        @Override
        public void routing(HttpRules httpRules) {
            httpRules.get("/", this::getDefaultMessageHandler);
        }

        private void getDefaultMessageHandler(ServerRequest serverRequest,
                                              ServerResponse serverResponse) {
            serverResponse.send(serverRequest.requestedUri().path().path());
        }
    }
}
