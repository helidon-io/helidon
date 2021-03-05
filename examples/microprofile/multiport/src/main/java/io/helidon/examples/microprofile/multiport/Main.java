/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.microprofile.multiport;

import io.helidon.config.Config;
import io.helidon.microprofile.server.Server;

/**
 * Runs helidon on two ports - one for admin, one for business logic.
 */
public final class Main {
    private static Server server;

    private Main() {
    }

    /**
     * Run the MP application on multiple ports.
     * @param args ignored
     */
    public static void main(String[] args) {
        server = Server.builder()
                .config(Config.create())
                .build()
                .start();
    }

    static Server server() {
        return server;
    }
}
