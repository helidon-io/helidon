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

package io.helidon.integrations.micrometer.cdi;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.RequestScoped;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;

/**
 * HelloWorldResource class.
 */
@Path("helloworld")
@RequestScoped
public class HelloWorldResource {

    static final String SLOW_RESPONSE = "At last";

    // In case pipeline runs need a different time
    static final int SLOW_DELAY_SECS = Integer.getInteger("helidon.asyncSimplyTimedDelaySeconds", 2);
    static final int NOT_SO_SLOW_DELAY_MS = 250;

    static final String MESSAGE_RESPONSE = "Hello World";
    static final String MESSAGE_COUNTER = "messageCounter";
    static final String MESSAGE_TIMER = "messageTimer";
    static final String MESSAGE_TIMER_2 = "messageTimer2";
    static final String SLOW_MESSAGE_TIMER = "slowMessageTimer";
    static final String SLOW_MESSAGE_COUNTER = "slowMessageCounter";
    static final String SLOW_MESSAGE_FAIL_COUNTER = "slowMessageFailCounter";
    static final String DOOMED_COUNTER = "doomedCounter";
    static final String FAST_MESSAGE_COUNTER = "fastMessageCounter";
    static final String FAIL = "FAIL";

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    public HelloWorldResource() {
    }

    @Counted(MESSAGE_COUNTER)
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String message() {
        return MESSAGE_RESPONSE;
    }

    @GET
    @Timed(MESSAGE_TIMER)
    @Timed(MESSAGE_TIMER_2)
    @Path("/withArg/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String messageWithArg(@PathParam("name") String input){
        return "Hello World, " + input;
    }

    @GET
    @Counted(value = FAST_MESSAGE_COUNTER, recordFailuresOnly = true)
    @Path("/fail/{fail}")
    @Produces(MediaType.TEXT_PLAIN)
    public String sometimesFail(@PathParam("fail") boolean fail) {
        if (fail) {
            throw new IllegalStateException("Failure on request");
        }
        return "Hi";
    }

    @GET
    @Path("/slow")
    @Produces(MediaType.TEXT_PLAIN)
    @Timed(SLOW_MESSAGE_TIMER)
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
    @Counted(SLOW_MESSAGE_COUNTER)
    public void slowMessageWithArg(@PathParam("name") String input, @Suspended AsyncResponse ar) {
        executorService.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(SLOW_DELAY_SECS);
            } catch (InterruptedException e) {
                // absorb silently
            } finally {
                ar.resume(SLOW_RESPONSE + " " + input);
            }
        });
    }

    @GET
    @Path("/slowWithFail/{fail}")
    @Produces(MediaType.TEXT_PLAIN)
    @Counted(value = SLOW_MESSAGE_FAIL_COUNTER, recordFailuresOnly = true)
    public void slowMessageWithFail(@PathParam("fail") boolean fail, @Suspended AsyncResponse ar) {
        executorService.execute(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(NOT_SO_SLOW_DELAY_MS);
                if (fail) {
                    throw new IllegalStateException("Fail on request");
                } else {
                    ar.resume(SLOW_RESPONSE);
                }
            } catch (Exception e) {
                ar.resume(e);
            }
        });
    }

    @GET
    @Path("/slowFailNoCounter")
    @Produces(MediaType.TEXT_PLAIN)
    public void slowFailNoCounter(@Suspended AsyncResponse ar) {
        ar.register(new CompletionCallback() {

            @Override
            public void onComplete(Throwable throwable) {
                if (throwable == null) {
                    System.out.println("OK");
                } else {
                    System.out.println("No good");
                }
            }
        });
        executorService.execute(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(NOT_SO_SLOW_DELAY_MS);
            } catch (InterruptedException e) {
                // Don't care for testing.
            }
            ar.resume(new IllegalStateException("Fail on request"));
        });
    }

    @GET
    @Counted(DOOMED_COUNTER)
    @Path("/testDeletedMetric")
    @Produces(MediaType.TEXT_PLAIN)
    public String testDeletedMetric() {
        return "Hello there";
    }
}
