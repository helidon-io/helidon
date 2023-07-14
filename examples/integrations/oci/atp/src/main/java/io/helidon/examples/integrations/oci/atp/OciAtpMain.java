/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.atp;

import java.io.IOException;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.webserver.WebServer;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.database.Database;
import com.oracle.bmc.database.DatabaseClient;
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

        // By default, this will pick up application.yaml from the classpath
        Config config = Config.create();

        // this requires OCI configuration in the usual place
        // ~/.oci/config
        ConfigFileReader.ConfigFile configFile = ConfigFileReader.parseDefault();
        AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(configFile);
        Database databaseClient = DatabaseClient.builder().build(authProvider);

        // Prepare routing for the server
        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routing
                        .register("/atp", new AtpService(databaseClient, config))
                        // OCI SDK error handling
                        .error(BmcException.class, (req, res, ex) ->
                                res.status(ex.getStatusCode())
                                        .send(ex.getMessage())))
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/");
    }
}
