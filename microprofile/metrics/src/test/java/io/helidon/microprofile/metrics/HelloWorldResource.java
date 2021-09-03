/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import io.helidon.webserver.ServerResponse;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HelloWorldResource class.
 */
@Path("helloworld")
@RequestScoped
@Counted
public class HelloWorldResource {

    private static final Logger LOGGER = Logger.getLogger(HelloWorldResource.class.getName());

    static final String SLOW_RESPONSE = "At last";

    // In case pipeline runs need a different time
    static final int SLOW_DELAY_MS = Integer.getInteger("helidon.microprofile.metrics.asyncSimplyTimedDelayMS", 2 * 1000);

    static final String MESSAGE_SIMPLE_TIMER = "messageSimpleTimer";
    static final String SLOW_MESSAGE_TIMER = "slowMessageTimer";
    static final String SLOW_MESSAGE_SIMPLE_TIMER = "slowMessageSimpleTimer";

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    static CountDownLatch slowRequestInProgress = null;
    static CountDownLatch slowRequestInProgressDataCaptured = null;
    static CountDownLatch slowRequestResponseSent = null;

    static void initSlowRequest() {
        slowRequestInProgress = new CountDownLatch(1);
        slowRequestInProgressDataCaptured = new CountDownLatch(1);
        slowRequestResponseSent = new CountDownLatch(1);
    }

    static void awaitSlowRequestStarted() throws InterruptedException {
        slowRequestInProgress.await();
    }

    static void reportDuringRequestFetched() {
        slowRequestInProgressDataCaptured.countDown();
    }

    static void awaitResponseSent() throws InterruptedException {
        slowRequestResponseSent.await();
    }

    static Optional<org.eclipse.microprofile.metrics.ConcurrentGauge> inflightRequests(MetricRegistry metricRegistry) {
        return metricRegistry.getConcurrentGauges((metricID, metric) -> metricID.getName().endsWith("inFlight"))
                .values()
                .stream()
                .findAny();
    }

    static long inflightRequestsCount(MetricRegistry metricRegistry) {
        return inflightRequests(metricRegistry).get().getCount();
    }

    @Inject
    MetricRegistry metricRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    private MetricRegistry vendorRegistry;

    public HelloWorldResource() {

    }

    // Do not add other metrics annotations to this method!
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String message() {
        metricRegistry.counter("helloCounter").inc();
        return "Hello World";
    }

    @GET
    @SimplyTimed(name = MESSAGE_SIMPLE_TIMER, absolute = true)
    @Path("/withArg/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String messageWithArg(@PathParam("name") String input){
        return "Hello World, " + input;
    }

    @GET
    @Path("/slow")
    @Produces(MediaType.TEXT_PLAIN)
    @SimplyTimed(name = SLOW_MESSAGE_SIMPLE_TIMER, absolute = true)
    @Timed(name = SLOW_MESSAGE_TIMER, absolute = true)
    public void slowMessage(@Suspended AsyncResponse ar, @Context ServerResponse serverResponse) {
        if (slowRequestInProgress == null) {
            ar.resume(new RuntimeException("slowRequestInProgress was unexpectedly null"));
            return;
        }
        serverResponse.whenSent()
                .thenAccept(r -> slowRequestResponseSent.countDown());

        long uponEntry = inflightRequestsCount();

        slowRequestInProgress.countDown();
        executorService.execute(() -> {
            try {
                long inAsyncExec = inflightRequestsCount();
                TimeUnit.MILLISECONDS.sleep(SLOW_DELAY_MS);
                slowRequestInProgressDataCaptured.await();
                if (!ar.resume(SLOW_RESPONSE)) {
                    throw new RuntimeException("Error resuming asynchronous response: not in suspended state");
                }
                long afterResume = inflightRequestsCount();
                LOGGER.log(Level.FINE,
                        "inAsyncExec: " + inAsyncExec + ", afterResume: " + afterResume);
            } catch (InterruptedException e) {
                throw new RuntimeException("Async test /slow wait was interrupted", e);
            }
        });
        LOGGER.log(Level.FINE, "uponEntry: " + uponEntry + ", beforeReturn: " + inflightRequestsCount());
    }

    @GET
    @Path("/slowWithArg/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    @SimplyTimed(name = SLOW_MESSAGE_SIMPLE_TIMER, absolute = true)
    @Timed(name = SLOW_MESSAGE_TIMER, absolute = true)
    public void slowMessageWithArg(@PathParam("name") String input, @Suspended AsyncResponse ar) {
        executorService.execute(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(SLOW_DELAY_MS);
                if (!ar.resume(SLOW_RESPONSE + " " + input)) {
                    throw new RuntimeException("Error resuming asynchronous response: not in suspended state");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Async test /slowWithArg{name} was interrupted", e);
            }
        });
    }

    @GET
    @Path("/testDeletedMetric")
    @Produces(MediaType.TEXT_PLAIN)
    public String testDeletedMetric() {
        return "Hello there";
    }

    @GET
    @Path("/error")
    @Produces(MediaType.TEXT_PLAIN)
    public void triggerAsyncError(@Suspended AsyncResponse ar) {
        executorService.execute(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(SLOW_DELAY_MS);
                if (!ar.resume(new Exception("Expected execption"))) {
                    throw new RuntimeException("Error resuming asynchronous response: not in suspended state");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Error test /error was interrupted");
            }
        });
    }

    private long inflightRequestsCount() {
        return inflightRequestsCount(vendorRegistry);
    }
}
