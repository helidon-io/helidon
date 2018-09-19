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
package io.helidon.examples.graal;

import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.json.JsonSupport;

/**
 * Runnable class for Graal integration example.
 * <p>
 * Steps:
 * <ol>
 * <li>Follow "Setting up the development environment" guide from: https://github.com/cstancu/netty-native-demo</li>
 * <li>Update GRAAL_HOME with your installation directory in {@code./etc/graal/env.sh}</li>
 * <li>Invoke command: {@code source ./etc/graal/env.sh}</li>
 * <li>Install the library into local repository: {@code  mvn install:install-file -Dfile=${JAVA_HOME}/jre/lib/svm/builder/svm
 * .jar -DgroupId=com.oracle.substratevm -DartifactId=svm -Dversion=GraalVM-1.0.0-rc6 -Dpackaging=jar}</li>
 * <li>Build the project: {@code mvn clean package}</li>
 * <li>Build the native image: {@code ./etc/graal/svm-compile.sh}</li>
 * <li>Run the application: {@code ./helidon-graal-vm-full}</li>
 * </ol>
 */
public final class GraalMain {
    // private constructor
    private GraalMain() {
    }

    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().readConfiguration(GraalMain.class.getResourceAsStream("/logging.properties"));

        // this property is not available in Graal SVM, and is not mandatory, yet YAML parser fails if it not present
        System.setProperty("java.runtime.name", "Graal SubstrateVM");
        Logger.getLogger(GraalMain.class.getName()).severe("Does it even work?");

        long t = System.currentTimeMillis();

        Config config = Config.create();

        WebServer.create(ServerConfiguration.fromConfig(config.get("server")), routing(config))
                .start().thenAccept(webServer -> {
            long time = System.currentTimeMillis() - t;
            System.out.println("Application started in " + time + " milliseconds");
            System.out.println("Application is available at: http://localhost:" + webServer.port() + "/");
        })
                .exceptionally(throwable -> {
                    System.err.println("Failed to start webserver");
                    throwable.printStackTrace();
                    return null;
                });
    }

    private static Routing routing(Config config) {
        String message = config.get("my-app.message").asString();

        return Routing.builder()
                .get("/", (req, res) -> res.send(message))
                .get("/hello", (req, res) -> res.send("Hello World"))
                .register("/json", JsonSupport.get())
                .get("/json", GraalMain::jsonResponse)
                .build();
    }

    private static void jsonResponse(ServerRequest req, ServerResponse res) {
        String param = req.queryParams().first("param").orElse("default");
        try {
            JsonObject theObject = Json.createObjectBuilder()
                    .add("key", "value")
                    .add("time", System.currentTimeMillis())
                    .add("parameter", param)
                    .build();

            res.send(theObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
