/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import io.helidon.common.reactive.Single;
import io.helidon.logging.common.LogConfig;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.media.multipart.MultiPartSupport;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.WebServer;
import io.helidon.reactive.webserver.staticcontent.StaticContentSupport;

/**
 * This application provides a simple file upload service with a UI to exercise multipart.
 */
public final class Main {
    private static final Http.HeaderValue REDIRECT_LOCATION = Http.HeaderValue.createCached(Http.Header.LOCATION, "/ui");

    private Main() {
    }

    /**
     * Creates new {@link Routing}.
     *
     * @return the new instance
     */
    static Routing createRouting() {
        return Routing.builder()
                .any("/", (req, res) -> {
                    res.status(Http.Status.MOVED_PERMANENTLY_301);
                    res.headers().set(REDIRECT_LOCATION);
                    res.send();
                })
                .register("/ui", StaticContentSupport.builder("WEB")
                        .welcomeFileName("index.html")
                        .build())
                .register("/api", new FileService())
                .build();
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        startServer(8080);
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     */
    static Single<WebServer> startServer(int port) {
        LogConfig.configureRuntime();
        WebServer server = WebServer.builder(createRouting())
                .port(port)
                .addMediaSupport(MultiPartSupport.create())
                .addMediaSupport(JsonpSupport.create())
                .build();

        Single<WebServer> webserver = server.start();

        // Start the server and print some info.
        webserver.thenAccept(ws -> {
            System.out.println("WEB server is up! http://localhost:" + ws.port());
        });

        // Server threads are not demon. NO need to block. Just react.
        server.whenShutdown()
                .thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));

        return webserver;
    }


}
