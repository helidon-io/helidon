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

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * HelloWorldResource class.
 */
@Path("helloworld")
@RequestScoped
@Counted
public class HelloWorldResource {

    static final String SLOW_RESPONSE = "At last";

    // In case pipeline runs need a different time
    static final int SLOW_DELAY_SECS = Integer.getInteger("helidon.asyncSimplyTimedDelaySeconds", 2);

    static final String MESSAGE_SIMPLE_TIMER = "messageSimpleTimer";
    static final String SLOW_MESSAGE_TIMER = "slowMessageTimer";
    static final String SLOW_MESSAGE_SIMPLE_TIMER = "slowMessageSimpleTimer";

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Inject
    MetricRegistry metricRegistry;

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
    public void slowMessage(@Suspended AsyncResponse ar) {
        executorService.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(SLOW_DELAY_SECS);
            } catch (InterruptedException e) {
                // absorb silently
            }
            ar.resume(SLOW_RESPONSE);
        });
    }

    @GET
    @Path("/slowWithArg/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    @SimplyTimed(name = SLOW_MESSAGE_SIMPLE_TIMER, absolute = true)
    @Timed(name = SLOW_MESSAGE_TIMER, absolute = true)
    public void slowMessageWithArg(@PathParam("name") String input, @Suspended AsyncResponse ar) {
        executorService.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(SLOW_DELAY_SECS);
            } catch (InterruptedException e) {
                // absorb silently
            }
            ar.resume(SLOW_RESPONSE + " " + input);
        });
    }

    @GET
    @Path("/testDeletedMetric")
    @Produces(MediaType.TEXT_PLAIN)
    public String testDeletedMetric() {
        return "Hello there";
    }
}
