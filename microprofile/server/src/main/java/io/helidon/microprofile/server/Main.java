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

package io.helidon.microprofile.server;

/**
 * Start a Helidon microprofile server that collects JAX-RS resources from
 * configuration or from classpath.
 */
public final class Main {
    private Main() {
    }

    /**
     * Main method to start server. The server will collection JAX-RS application automatically (through
     * CDI extension - just annotate it with {@link javax.enterprise.context.ApplicationScoped}).
     *
     * @param args command line arguments, currently ignored
     */
    public static void main(String[] args) {
        Server server = Server.create();
        server.start();
    }
}
