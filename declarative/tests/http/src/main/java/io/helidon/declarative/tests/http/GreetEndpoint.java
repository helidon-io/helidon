/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.http;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Default;
import io.helidon.common.context.Context;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Configuration;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.RestServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

/**
 * A simple endpoint to greet you. Examples:
 * <p>
 * Get default greeting message:
 * {@code curl -X GET http://localhost:8080/greet}
 * <p>
 * Get greeting message for Joe:
 * {@code curl -X GET http://localhost:8080/greet/Joe}
 * <p>
 * Change greeting
 * {@code curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting}
 * <p>
 * The message is returned as a JSON object.
 */
@RestServer.Listener(value = "@default", required = true)
@RestServer.Endpoint
@Service.Singleton
class GreetEndpoint implements GreetEndpointApi {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private static final AtomicInteger RETRY_CALLS = new AtomicInteger();
    private static volatile Instant lastCall = Instant.now();

    /**
     * The config value for the key {@code greeting}.
     */
    private final AtomicReference<String> greeting = new AtomicReference<>();

    @Service.Inject
    GreetEndpoint(@Configuration.Value("app.greeting") @Default.Value("Ciao") String greeting) {
        this.greeting.set(greeting);
    }

    /**
     * Return a worldly greeting message.
     */
    @Http.GET
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    public JsonObject getDefaultMessageHandler() {
        return response("World");
    }

    @FaultTolerance.Fallback(value = "fallback", applyOn = IllegalStateException.class)
    @Override
    public String failingFallback(@Http.HeaderParam(HeaderNames.HOST_NAME) String host) {
        throw new IllegalStateException("Failed");
    }

    @FaultTolerance.Retry(name = "named")
    @RestServer.Header(name = "X-Header", value = "X-Value")
    @RestServer.ComputedHeader(name = "X-Computed", producerClass = ServerHeaderProducer.class)
    @Override
    public String retriable() {
        int i = RETRY_CALLS.incrementAndGet();
        if (i % 2 == 0) {
            return "Success";
        }
        throw new IllegalStateException("Failed");
    }

    @FaultTolerance.CircuitBreaker(name = "named")
    @Override
    public String breaker() {
        Instant now = Instant.now();
        Duration duration = Duration.between(lastCall, now);
        lastCall = Instant.now();
        if (duration.getSeconds() > 3) {
            return "Success";
        }
        throw new HttpException("Failed", Status.FORBIDDEN_403);
    }

    @FaultTolerance.Timeout(time = "PT1S")
    @Override
    public String timeout(@Http.QueryParam("sleepSeconds") Optional<Integer> sleep) {
        try {
            Thread.sleep(sleep.orElse(0) * 1000);
        } catch (InterruptedException e) {
            return "Interrupted";
        }
        return "Success";
    }

    /**
     * Return a greeting message using the name that was provided.
     */
    @Http.GET
    @Http.Path("/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @Override
    public JsonObject getMessageHandler(@Http.PathParam("name") String name) {
        return response(name);
    }

    @Http.GET
    @Http.Path("/test/multiple/methods/with/same/name")
    public String getMessageHandler() {
        return "Success";
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param greetingMessage the entity
     */
    @Http.PUT
    @Http.Path("/greeting")
    @Http.Status(Status.NO_CONTENT_204_CODE)
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Override
    public void updateGreetingHandler(@Http.Entity JsonObject greetingMessage) {
        if (!greetingMessage.containsKey("greeting")) {
            // mapped by QuickstartErrorHandler
            throw new QuickstartException(Status.BAD_REQUEST_400, "No greeting provided");
        }

        greeting.set(greetingMessage.getString("greeting"));
    }

    /**
     * Return a worldly greeting message.
     */
    @Http.GET
    @Http.Produces("text/plain")
    String getDefaultMessageHandlerPlain(Context context) {
        return stringResponse("World");
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param greetingMessage the entity
     * @return Hello World message
     */
    @Override
    public JsonObject updateGreetingHandlerReturningCurrent(@Http.Entity JsonObject greetingMessage) {
        if (!greetingMessage.containsKey("greeting")) {
            // mapped by QuickstartErrorHandler
            throw new QuickstartException(Status.BAD_REQUEST_400, "No greeting provided");
        }
        JsonObject response = response("World");
        greeting.set(greetingMessage.getString("greeting"));
        return response;
    }

    String fallback(String host) {
        return "Fallback " + host;
    }

    private JsonObject response(String name) {
        return JSON.createObjectBuilder()
                .add("message", stringResponse(name))
                .build();
    }

    private String stringResponse(String name) {
        return String.format("%s %s!", greeting.get(), name);
    }

}
