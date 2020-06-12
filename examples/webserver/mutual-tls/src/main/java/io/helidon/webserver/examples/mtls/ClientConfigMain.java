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
package io.helidon.webserver.examples.mtls;

import io.helidon.config.Config;
import io.helidon.webclient.WebClient;

/**
 * Setting up {@link WebClient} to support mutual TLS via configuration.
 */
public class ClientConfigMain {

    /**
     * Start the example.
     * This example executes two requests by Helidon {@link WebClient} which are configured
     * by the configuration.
     *
     * You have to execute either {@link ServerBuilderMain} or {@link ServerConfigMain} for this to work.
     *
     * If any of the ports has been changed, you have to update ports in this main method also.
     *
     * @param args start arguments are ignored
     */
    public static void main(String[] args) {
        Config config = Config.create();
        WebClient webClient = WebClient.create(config.get("client"));

        System.out.println("Contacting unsecured endpoint!");
        System.out.println("Response: " + callUnsecured(webClient, 8080));

        System.out.println("Contacting secured endpoint!");
        System.out.println("Response: " + callSecured(webClient, 443));

    }

    static String callUnsecured(WebClient webClient, int port) {
        return webClient.get()
                .uri("http://localhost:" + port)
                .request(String.class)
                .await();
    }

    static String callSecured(WebClient webClient, int port) {
        return webClient.get()
                .uri("https://localhost:" + port)
                .request(String.class)
                .await();
    }



}
