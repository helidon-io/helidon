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

package io.helidon.examples.microprofile.security.idcs;

import io.helidon.microprofile.server.Server;

/**
 * IDCS example.
 */
public final class IdcsMain {
    private IdcsMain() {
    }

    /**
     * Start the server and use the application picked up by CDI.
     *
     * @param args command line arguments, ignored
     */
    public static void main(String[] args) {
        Server.create().start();

        System.out.println("Endpoints:");
        System.out.println("Login");
        System.out.println("  http://localhost:7987/rest/login");
        System.out.println("Full security with scopes and roles (see IdcsResource.java)");
        System.out.println("  http://localhost:7987/rest/scopes");
        System.out.println("A protected reactive service (see application.yaml - security.web-server)");
        System.out.println("  http://localhost:7987/reactive");
        System.out.println("A protected static resource (see application.yaml - security.web-server");
        System.out.println("  http://localhost:7987/web/resource.html");
    }
}
