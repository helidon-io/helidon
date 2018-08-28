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

package io.helidon.microprofile.example.helloworld.implicit;

import io.helidon.microprofile.server.Server;

/**
 * Main method simulating trigger of main method of the server.
 */
public class Main {
    private static Server server;

    private Main() {
    }

    /**
     * Start the server and the server will start this application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        //this is to simulate command line trigger of the main method in server (e.g. when in a container)
        //io.helidon.microprofile.server.Main.main(args);

        //we must use server to start it, so we can get the port it was started on
        server = Server.create().start();

        System.out.println("You can find an endpoint on http://localhost:" + server.getPort() + "/helloworld");
    }

    static int getPort() {
        return server.getPort();
    }
}
