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

package io.helidon.security.examples.webserver.basic;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.webserver.WebServer;

final class BasicExampleUtil {
    private BasicExampleUtil() {
    }

    static void startAndPrintEndpoints(Supplier<WebServer> startMethod) {
        long t = System.nanoTime();

        WebServer webServer = startMethod.get();

        long time = System.nanoTime() - t;
        System.out.printf("Server started in %d ms ms%n", TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
        System.out.printf("Started server on localhost:%d%n", webServer.port());
        System.out.println();
        System.out.println("Users:");
        System.out.println("Jack/password in roles: user, admin");
        System.out.println("Jill/password in roles: user");
        System.out.println("John/password in no roles");
        System.out.println();
        System.out.println("***********************");
        System.out.println("** Endpoints:        **");
        System.out.println("***********************");
        System.out.println("No authentication:");
        System.out.printf("  http://localhost:%1$d/public%n", webServer.port());
        System.out.println("No roles required, authenticated:");
        System.out.printf("  http://localhost:%1$d/noRoles%n", webServer.port());
        System.out.println("User role required:");
        System.out.printf("  http://localhost:%1$d/user%n", webServer.port());
        System.out.println("Admin role required:");
        System.out.printf("  http://localhost:%1$d/admin%n", webServer.port());
        System.out.println("Always forbidden (uses role nobody is in), audited:");
        System.out.printf("  http://localhost:%1$d/deny%n", webServer.port());
        System.out.println(
                "Admin role required, authenticated, authentication optional, audited (always forbidden - challenge is not "
                        + "returned as authentication is optional):");
        System.out.printf("  http://localhost:%1$d/noAuthn%n", webServer.port());
        System.out.println("Static content, requires user role:");
        System.out.printf("  http://localhost:%1$d/static/index.html%n", webServer.port());
        System.out.println();
    }
}
