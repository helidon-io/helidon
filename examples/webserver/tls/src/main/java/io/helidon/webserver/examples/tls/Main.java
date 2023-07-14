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

package io.helidon.webserver.examples.tls;

import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * Main class of TLS example.
 */
public final class Main {

    // utility class
    private Main() {
    }

    /**
     * Start the example.
     * This will start two Helidon WebServers, both protected by TLS - one configured from config, one using a builder.
     * Port of the servers will be configured from config, to be able to switch to an ephemeral port for tests.
     *
     * @param args start arguments are ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Config config = Config.create();

        WebServerConfig.Builder builder1 = WebServer.builder();
        setupConfigBased(builder1, config);
        WebServer server1 = builder1.build().start();
        System.out.println("Started config based WebServer on http://localhost:" + server1.port());

        WebServerConfig.Builder builder2 = WebServer.builder();
        setupBuilderBased(builder2, config);
        WebServer server2 = builder2.build().start();
        System.out.println("Started builder based WebServer on http://localhost:" + server2.port());
    }

    static void setupBuilderBased(WebServerConfig.Builder server, Config config) {
        server.config(config)
                .routing(Main::routing)
                .tls(tls -> tls
                        .privateKey(key -> key
                                .keystore(store -> store
                                        .keystore(Resource.create("certificate.p12"))
                                        .passphrase("helidon"))));
    }

    static void setupConfigBased(WebServerConfig.Builder server, Config config) {
        server.config(config).routing(Main::routing);
    }

    static void routing(HttpRouting.Builder routing) {
        routing.get("/", (req, res) -> res.send("Hello!"));
    }
}
