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
package io.helidon.rest.client.example.basic;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.webclient.WebClient;

/**
 * A standalone web client.
 */
public class StandaloneClientExample {

    private static final Logger LOGGER = Logger.getLogger(StandaloneClientExample.class.getName());

    private StandaloneClientExample() {

    }

    /**
     * Executes simple request using webclient.
     *
     * @param args arguments
     * @throws ExecutionException
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        LogManager.getLogManager().readConfiguration(StandaloneClientExample.class.getResourceAsStream("/logging.properties"));

        /*
         * Prepare helidon stuff
         */
        Config config = Config.create();
        Security security = Security.builder().build();

        SecurityContext securityContext = security.createContext("standalone-example");

        Context context = Context.builder().id("standalone-example").build();
        context.register(securityContext);

        /*
         * Initialize client.
         */
        WebClient client = WebClient.builder()
                .config(config.get("client"))
                .context(context)
                .build();

        /*
         * Each request is created using a builder like fluent api
         */
        //        CompletionStage<ClientResponse> response = client.put()
        //                .uri("http://localhost:8080/greeting")
        //                // parent span
        //                .property(ClientTracing.PARENT_SPAN, spanContext)
        //                // override tracing span
        //                .property(ClientTracing.SPAN_NAME, "myspan")
        //                // override metric name
        //                .property(ClientMetrics.ENDPOINT_NAME, "aServiceName")
        //                .property(ClientSecurity.PROVIDER_NAME, "http-basic-auth")
        //                // override security
        //                .property("io.helidon.security.outbound.username", "aUser")
        //                // add custom header
        //                .header("MY_HEADER", "Value")
        //                // override proxy configuration of client
        //                .proxy(Proxy.noProxy())
        //                // send entity (may be a publisher of chunks)
        //                // should support forms
        //                .submit("New Hello");
        //
        //
        //        response.thenApply(ClientResponse::status)
        //                .thenAccept(System.out::println)
        //                .toCompletableFuture()
        //                .join();

        client.get()
                .uri("https://www.google.com")
                .request(String.class)
                .thenAccept(System.out::println)
                .exceptionally(throwable -> {
                    // handle client error
                    LOGGER.log(Level.SEVERE, "Failed to invoke client", throwable);
                    return null;
                })
                // this is to make sure the VM does not exit before finishing the call
                .toCompletableFuture()
                .get();

    }
}
