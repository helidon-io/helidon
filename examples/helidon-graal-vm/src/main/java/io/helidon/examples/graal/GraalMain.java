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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

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

    public static void main(String[] args) {
        //        System.out.println("Config Parsers:");
        //        SpiHelper.loadServices(ConfigParser.class).stream().map(Object::getClass).map(Class::getName).forEach(System
        // .out::println);
        //        System.out.println("File type detectors:");
        //        SpiHelper.loadServices(FileTypeDetector.class).stream().map(Object::getClass).map(Class::getName).forEach
        // (System.out::println);

        // work around - could not set property using graal
        System.setProperty("java.runtime.name", "Graal SubstrateVM");

        long t = System.currentTimeMillis();
        //        Config config = Config.builder()
        //                .sources(ConfigSources.from(CollectionsHelper.mapOf("my-app.message", "Hello World!")))
        //                .build();
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.yaml"))
                .build();

        String message = config.get("my-app.message").asString();

        //SpiHelper.registerService(WebServerFactory.class, new Factory());

        WebServer.create(Routing.builder()
                                 .get("/", (req, res) -> {
                                     res.send(message);
                                 })
        )
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
}
