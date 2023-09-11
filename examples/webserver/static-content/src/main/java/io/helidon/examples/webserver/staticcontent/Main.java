/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.staticcontent;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.StaticContentService;

/**
 * The application main class.
 */
public final class Main {
    private static final Header UI_REDIRECT = Http.Headers.createCached(HeaderNames.LOCATION, "/ui");

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        WebServer server = WebServer.builder()
                .port(8080)
                .routing(Main::routing)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
    }

    /**
     * Updates HTTP Routing.
     */
    static void routing(HttpRouting.Builder routing) {
        routing.any("/", (req, res) -> {
                    // showing the capability to run on any path, and redirecting from root
                    res.status(Status.MOVED_PERMANENTLY_301);
                    res.headers().set(UI_REDIRECT);
                    res.send();
                })
                .register("/ui", CounterService::new)
                .register("/ui", StaticContentService.builder("WEB")
                        .welcomeFileName("index.html")
                        .build());
    }
}
