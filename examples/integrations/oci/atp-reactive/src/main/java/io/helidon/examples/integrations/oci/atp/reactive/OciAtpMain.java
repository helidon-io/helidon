/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.atp.reactive;

import java.io.IOException;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.database.DatabaseAsync;
import com.oracle.bmc.database.DatabaseAsyncClient;
import com.oracle.bmc.model.BmcException;

/**
 * Main class of the example.
 * This example sets up a web server to serve REST API to retrieve ATP wallet.
 */
public final class OciAtpMain {
    /**
     * Cannot be instantiated.
     */
    private OciAtpMain() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) throws IOException {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // this requires OCI configuration in the usual place
        // ~/.oci/config
        AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(ConfigFileReader.parseDefault());
        DatabaseAsync databaseAsyncClient = DatabaseAsyncClient.builder().build(authProvider);

        // Prepare routing for the server
        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(Routing.builder()
                                 .register("/atp", new AtpService(databaseAsyncClient, config))
                                 // OCI SDK error handling
                                 .error(BmcException.class, (req, res, ex) -> res.status(ex.getStatusCode())
                                         .send(ex.getMessage())))
                .build();

        // Start the server and print some info.
        server.start().thenAccept(ws -> {
            System.out.println(
                    "WEB server is up! http://localhost:" + ws.port() + "/");
        });

        // Server threads are not daemon. NO need to block. Just react.
        server.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
    }
}
