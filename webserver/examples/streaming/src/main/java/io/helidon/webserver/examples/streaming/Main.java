/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.streaming;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

/**
 * Class Main. Entry point to streaming application.
 */
public class Main {

    static final String LARGE_FILE_RESOURCE = "/large-file.bin";

    private Main() {}

    /**
     * Creates new {@link Routing}.
     *
     * @return the new instance
     */
    static Routing createRouting() {
        return Routing.builder()
                      .register(new StreamingService())
                      .build();
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        ServerConfiguration config = ServerConfiguration.builder()
                                                        .port(8080)
                                                        .build();
        WebServer server = WebServer.create(config, createRouting());

        server.start().thenAccept(ws ->
            System.out.println("Steaming service is up at http://localhost:" + ws.port())
        );

        server.whenShutdown().thenRun(() ->
            System.out.println("Streaming service is down")
        );
    }
}
