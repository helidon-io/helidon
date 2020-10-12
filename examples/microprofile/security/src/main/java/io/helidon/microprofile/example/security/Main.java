/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.example.security;

import java.util.concurrent.TimeUnit;

import io.helidon.microprofile.server.Server;

/**
 * Main class to start the application.
 * See resources/META-INF/microprofile-config.properties.
 */
public final class Main {
    private Main() {
    }

    /**
     * Run this example.
     *
     * @param args command line arguments (ignored)
     */
    public static void main(String[] args) {
        long now = System.nanoTime();

        Server server = Server.create(StaticContentApp.class, OtherApp.class)
                .start();

        now = System.nanoTime() - now;
        System.out.println("Start server: " + TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS));
        System.out.println("Endpoint available at http://localhost:" + server.port() + "/static/helloworld");
        System.out.println("Alternative endpoint (second application) available at http://localhost:" + server
                .port() + "/other/helloworld");
    }
}
