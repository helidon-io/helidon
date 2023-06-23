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

package io.helidon.nima.tests.integration.imperative;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.http.Http;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * Main class of this example, starts the server.
 */
public final class ImperativeMain {
    private ImperativeMain() {
    }

    /**
     * Start the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Config config = GlobalConfig.config();

        WebServer server = WebServer.create(ws -> ws.config(config.get("server"))
                        .routing(ImperativeMain::routing))
                .start();

        System.out.println("Server started. Server configuration: " + server.prototype());
    }

    static void routing(HttpRouting.Builder routing) {
        Http.Method list = Http.Method.create("LIST");

        routing.get("/", (req, res) -> res.send("Hello World!"))
                .route(list, "/", (req, res) -> res.send("lll"))
                .route(list, (req, res) -> res.send("listed"));
    }
}
