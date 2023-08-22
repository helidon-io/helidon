/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.webclient.standalone;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.http.Http;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.metrics.WebClientMetrics;
import io.helidon.webclient.spi.WebClientService;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

/**
 * A simple WebClient usage class.
 * <p>
 * Each of the methods demonstrates different usage of the WebClient.
 */
public class ClientMain {

    private static final MeterRegistry METER_REGISTRY = Metrics.globalRegistry();
    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Map.of());
    private static final JsonObject JSON_NEW_GREETING;

    static {
        JSON_NEW_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
    }

    private ClientMain() {
    }

    /**
     * Executes WebClient examples.
     * <p>
     * If no argument provided it will take server port from configuration server.port.
     * <p>
     * User can override port from configuration by main method parameter with the specific port.
     *
     * @param args main method
     */
    public static void main(String[] args) {
        Config config = Config.create();
        String url;
        if (args.length == 0) {
            ConfigValue<Integer> port = config.get("server.port").asInt();
            if (!port.isPresent() || port.get() == -1) {
                throw new IllegalStateException("Unknown port! Please specify port as a main method parameter "
                        + "or directly to config server.port");
            }
            url = "http://localhost:" + port.get() + "/greet";
        } else {
            url = "http://localhost:" + Integer.parseInt(args[0]) + "/greet";
        }

        WebClient client = WebClient.builder()
                .baseUri(url)
                .config(config.get("client"))
                .build();

        performPutMethod(client);
        performGetMethod(client);
        followRedirects(client);
        getResponseAsAnJsonObject(client);
        saveResponseToFile(client);
        clientMetricsExample(url, config);
    }

    static Http.Status performPutMethod(WebClient client) {
        System.out.println("Put request execution.");
        try (HttpClientResponse response = client.put("/greeting").submit(JSON_NEW_GREETING)) {
            System.out.println("PUT request executed with status: " + response.status());
            return response.status();
        }
    }

    static String performGetMethod(WebClient client) {
        System.out.println("Get request execution.");
        String result = client.get().requestEntity(String.class);
        System.out.println("GET request successfully executed.");
        System.out.println(result);
        return result;
    }

    static String followRedirects(WebClient client) {
        System.out.println("Following request redirection.");
        try (HttpClientResponse response = client.get("/redirect").request()) {
            if (response.status() != Http.Status.OK_200) {
                throw new IllegalStateException("Follow redirection failed!");
            }
            String result = response.as(String.class);
            System.out.println("Redirected request successfully followed.");
            System.out.println(result);
            return result;
        }
    }

    static void getResponseAsAnJsonObject(WebClient client) {
        System.out.println("Requesting from JsonObject.");
        JsonObject jsonObject = client.get().requestEntity(JsonObject.class);
        System.out.println("JsonObject successfully obtained.");
        System.out.println(jsonObject);
    }

    static void saveResponseToFile(WebClient client) {
        Path file = Paths.get("test.txt");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Downloading server response to file: " + file);
        try (HttpClientResponse response = client.get().request()) {
            Files.copy(response.entity().inputStream(), file);
            System.out.println("Download complete!");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String clientMetricsExample(String url, Config config) {
        //This part here is only for verification purposes, it is not needed to be done for actual usage.
        String counterName = "example.metric.GET.localhost";
        Counter counter = METER_REGISTRY.getOrCreate(Counter.builder(counterName));
        System.out.println(counterName + ": " + counter.count());

        //Creates new metric which will count all GET requests and has format of example.metric.GET.<host-name>
        WebClientService clientService = WebClientMetrics.counter()
                .methods(Http.Method.GET)
                .nameFormat("example.metric.%1$s.%2$s")
                .build();

        //This newly created metric now needs to be registered to WebClient.
        WebClient client = WebClient.builder()
                .baseUri(url)
                .config(config)
                .addService(clientService)
                .build();

        //Perform any GET request using this newly created WebClient instance.
        String result = performGetMethod(client);
        //Verification for example purposes that metric has been incremented.
        System.out.println(counterName + ": " + counter.count());
        return result;
    }
}
