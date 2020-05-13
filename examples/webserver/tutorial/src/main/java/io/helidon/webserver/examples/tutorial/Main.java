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

package io.helidon.webserver.examples.tutorial;

import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.examples.tutorial.user.UserFilter;

/**
 * Application java main class.
 *
 * <p>The TUTORIAL application demonstrates various WebServer use cases together and in its complexity.
 * <p>It also serves web server tutorial articles composed from life examples.
 */
public final class Main {

    private Main() {
    }

    static Routing createRouting() {
        UpperXFilter upperXFilter = new UpperXFilter();
        return Routing.builder()
                    .any(new UserFilter())
                    .any((req, res) -> {
                        res.registerFilter(upperXFilter);
                        req.next();
                    })
                    .register("/article", new CommentService())
                    .post("/mgmt/shutdown", (req, res) -> {
                        res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                        res.send("Shutting down TUTORIAL server. Good bye!\n");
                        // Use reactive API nature to stop the server AFTER the response was sent.
                        res.whenSent().thenRun(() -> req.webServer().shutdown());
                    })
                    .build();
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        // Create a web server instance
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                port = 0;
            }
        }

        WebServer server = WebServer.builder(createRouting())
                .port(port)
                .build();

        // Start the server and print some info.
        server.start().thenAccept(ws -> {
            System.out.println("TUTORIAL server is up! http://localhost:" + ws.port());
            System.out.println("Call POST on 'http://localhost:" + ws.port() + "/mgmt/shutdown' to STOP the server!");
        });

        // Server threads are not demon. NO need to block. Just react.
        server.whenShutdown()
                .thenRun(() -> System.out.println("TUTORIAL server is DOWN. Good bye!"));

    }
}
