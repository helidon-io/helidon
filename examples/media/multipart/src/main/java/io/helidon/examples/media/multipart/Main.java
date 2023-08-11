/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.media.multipart;

import io.helidon.common.http.Http;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.staticcontent.StaticContentService;

/**
 * This application provides a simple file upload service with a UI to exercise multipart.
 */
public final class Main {
    private static final Http.Header UI_LOCATION = Http.Headers.createCached(Http.HeaderNames.LOCATION, "/ui");

    private Main() {
    }

    /**
     * Executes the example.
     *
     * @param args command line arguments, ignored
     */
    public static void main(String[] args) {
        WebServer server = WebServer.builder()
                .routing(Main::routing)
                .port(8080)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    /**
     * Updates the routing rules.
     *
     * @param rules routing rules
     */
    static void routing(HttpRules rules) {
        rules.any("/", (req, res) -> {
                    res.status(Http.Status.MOVED_PERMANENTLY_301);
                    res.header(UI_LOCATION);
                    res.send();
                })
                .register("/ui", StaticContentService.builder("WEB")
                        .welcomeFileName("index.html")
                        .build())
                .register("/api", new FileService());
    }
}
