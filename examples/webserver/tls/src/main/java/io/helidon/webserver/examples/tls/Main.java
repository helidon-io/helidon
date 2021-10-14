/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletionStage;

import io.helidon.common.LogConfig;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerTls;

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
        startConfigBasedServer(config.get("config-based"))
                .thenAccept(ws -> {
                    System.out.println("Started config based WebServer on http://localhost:" + ws.port());
                });
        startBuilderBasedServer(config.get("builder-based"))
                .thenAccept(ws -> {
                    System.out.println("Started builder based WebServer on http://localhost:" + ws.port());
                });
    }

    static CompletionStage<WebServer> startBuilderBasedServer(Config config) {
        return WebServer.builder()
                .host("localhost")
                .config(config)
                .routing(routing())
                // now let's configure TLS
                .tls(WebServerTls.builder()
                        .privateKey(KeyConfig.keystoreBuilder()
                        .keystore(Resource.create("certificate.p12"))
                        .keystorePassphrase("helidon")))
                .build()
                .start();
    }

    static CompletionStage<WebServer> startConfigBasedServer(Config config) {
        return WebServer.builder()
                .host("localhost")
                .config(config)
                .routing(routing())
                .build()
                .start();
    }

    private static Routing routing() {
        return Routing.builder()
                .get("/", (req, res) -> res.send("Hello!"))
                .build();
    }
}
