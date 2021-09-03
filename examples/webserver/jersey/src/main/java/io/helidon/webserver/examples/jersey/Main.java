/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.jersey;

import java.util.concurrent.CompletionStage;

import io.helidon.common.LogConfig;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * The WebServer Jersey Main example class.
 *
 * @see #main(String[])
 * @see #startServer(int)
 */
public final class Main {

    private Main() {
    }

    /**
     * Run the Jersey WebServer Example.
     *
     * @param args arguments are not used
     */
    public static void main(String[] args) {
        // configure logging in order to not have the standard JVM defaults
        LogConfig.configureRuntime();

        // start the server on port 8080
        startServer(8080);
    }

    /**
     * Start the WebServer based on the provided configuration. When running from
     * a test, pass {@link null} to have a dynamically allocated port
     * the server listens on.
     *
     * @param port port to start server on
     * @return a completion stage indicating that the server has started and is ready to
     * accept http requests
     */
    static CompletionStage<WebServer> startServer(int port) {
        WebServer webServer = WebServer.builder(
                Routing.builder()
                       // register a Jersey Application at the '/jersey' context path
                       .register("/jersey",
                                 JerseySupport.create(new ResourceConfig(HelloWorld.class)))
                       .build())
                .port(port)
                .build();

        return webServer.start()
                        .whenComplete((server, t) -> {
                            System.out.println("Jersey WebServer started.");
                            System.out.println("To stop the application, hit CTRL+C");
                            System.out.println("Try the hello world resource at: http://localhost:" + server
                                    .port() + "/jersey/hello");
                        });
    }
}
