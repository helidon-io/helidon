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

package io.helidon.webserver.examples.blocking;

import java.net.http.HttpClient;

import io.helidon.common.LogConfig;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.blocking.BlockingHandler;

/**
 * Main class of the example, starts the server.
 */
public final class Main {
    private Main() {
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // explicitly configure an executor service
        // the default uses virtual threads if available, but not enforced
        ThreadPoolSupplier executor = ThreadPoolSupplier.create(config.get("executor"));
        BlockingHandler.executorService(executor);

        // let's start our three server, we must start with the "sleeping server", as it is used
        // by the other two
        SleepingServer sleepingServer = new SleepingServer();
        int sleepingPort = sleepingServer.start(config);

        // now we can create a client to be used by other server
        WebClient webClient = WebClient.builder()
                .baseUri("http://localhost:" + sleepingPort + "/sleep")
                .keepAlive(true)
                .build();

        // client for blocking calls
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor.get())
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        BlockingServer blockingServer = new BlockingServer();
        int blockingPort = blockingServer.start(config, client, sleepingPort);

        ReactiveServer reactiveServer = new ReactiveServer();
        int reactivePort = reactiveServer.start(config, webClient);

        System.out.println("Servers started");
        System.out.println("  Sleeping service:");
        System.out.println("    http://localhost:" + sleepingPort + "/sleep");
        System.out.println("  Blocking service:");
        System.out.println("    http://localhost:" + blockingPort + "/call");
        System.out.println("  Reactive service:");
        System.out.println("    http://localhost:" + reactivePort + "/call");
    }
}
