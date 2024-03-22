/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.streaming;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;

/**
 * Class Main. Entry point to streaming application.
 */
public class Main {

    static final String LARGE_FILE_PATH = "target/classes/large-file.bin";

    private Main() {
    }

    /**
     * Setup {@link HttpRouting}.
     */
    static void routing(HttpRouting.Builder routing) {
        routing.register(new StreamingService());
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        WebServerConfig.Builder builder = WebServer.builder().port(8080);
        builder.routing(Main::routing);
        WebServer server = builder.build().start();
        System.out.println("Steaming service is up at http://localhost:" + server.port());
    }
}
