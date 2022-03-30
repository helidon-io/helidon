/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver.tyrus;

import java.net.URI;
import java.util.function.Consumer;

import javax.websocket.server.ServerEndpointConfig;

import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * The TyrusSupportBaseTest.
 */
abstract class TyrusSupportBaseTest {
    private final int port;

    protected TyrusSupportBaseTest(WebServer ws) {
        this.port = ws.port();
    }

    protected static void routing(Routing.Rules rules, Class<?>... classes) {
        routing(rules, it -> {
            for (Class<?> clazz : classes) {
                it.register(clazz);
            }
        });
    }

    @SafeVarargs
    protected static <T extends ServerEndpointConfig> void routing(Routing.Rules rules, T... endpoints) {
        routing(rules, it -> {
            for (T endpoint : endpoints) {
                it.register(endpoint);
            }
        });
    }

    protected URI uri(String path) {
        return URI.create("ws://localhost:" + port + "/" + path);
    }

    private static void routing(Routing.Rules rules, Consumer<TyrusSupport.Builder> updater) {
        TyrusSupport.Builder tyrusSupportBuilder = TyrusSupport.builder();
        updater.accept(tyrusSupportBuilder);
        rules.register("/tyrus", tyrusSupportBuilder.build());
    }
}
