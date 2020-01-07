/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testsupport.LoggingTestUtils;

/**
 * The TyrusExampleMain.
 */
public final class TyrusExampleMain {

    public static final TyrusExampleMain INSTANCE = new TyrusExampleMain();

    private TyrusExampleMain() {
    }

    private volatile WebServer webServer;

    public static void main(String... args) throws InterruptedException, ExecutionException, TimeoutException {
        LoggingTestUtils.initializeLogging();
        INSTANCE.webServer(false);
    }

    synchronized WebServer webServer(boolean testing) throws InterruptedException, TimeoutException, ExecutionException {
        if (webServer != null) {
            return webServer;
        }

        ServerConfiguration.Builder builder =
                ServerConfiguration.builder().bindAddress(InetAddress.getLoopbackAddress());

        if (!testing) {
            // in case we're running as main an not in test, run on a fixed port
            builder.port(8080);
        }

        webServer = WebServer.create(
                builder.build(),
                Routing.builder().register(
                        "/tyrus",
                        TyrusSupport.builder().register(EchoEndpoint.class).build()));

        webServer.start().toCompletableFuture().get(10, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                webServer.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }));

        if (!testing) {
            System.out.println("WebServer Tyrus application started.");
            System.out.println("Hit CTRL+C to stop.");
            Thread.currentThread().join();
        }

        return webServer;
    }
}
