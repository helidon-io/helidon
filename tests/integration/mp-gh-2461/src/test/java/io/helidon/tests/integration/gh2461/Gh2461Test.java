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

package io.helidon.tests.integration.gh2461;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@HelidonTest
@AddBean(Gh2461Test.TestResource.class)
class Gh2461Test {
    private static final Logger FILTER_LOGGER = Logger.getLogger("io.helidon.tracing.jersey.AbstractTracingFilter");
    private static final Logger JAEGER_LOGGER = Logger.getLogger("io.jaegertracing.internal.JaegerSpan");

    private final TestHandler filterTestHandler = new TestHandler();
    private final TestHandler jaegerTestHandler = new TestHandler();

    @Inject
    private WebTarget target;

    @BeforeEach
    void beforeEach() {
        FILTER_LOGGER.addHandler(filterTestHandler);
        JAEGER_LOGGER.addHandler(jaegerTestHandler);
    }

    @AfterEach
    void afterEach() {
        FILTER_LOGGER.removeHandler(filterTestHandler);
        JAEGER_LOGGER.removeHandler(jaegerTestHandler);
        filterTestHandler.reset();
        jaegerTestHandler.reset();
    }

    @Test
    void testTracing() {
        String response = target.path("/test")
                .request()
                .get(String.class);

        assertThat(response, is("Test Message"));

        response = target.path("/test")
                .request()
                .get(String.class);

        assertThat(response, is("Test Message"));

        int count = 0;
        for (LogRecord o : filterTestHandler.recordList) {
            if (o.getLevel() == Level.WARNING) {
                if (o.getMessage().contains("Response filter called twice.")) {
                    count++;
                }
            }
        }
        assertThat("Normal processing should not include warnings", count, is(0));

        // make sure
        // 08:03:44.288 [helidon-1] WARN  io.jaegertracing.internal.JaegerSpan - Span has already been finished; will not be
        //reported again.
        // not logged at all
        count = 0;
        for (LogRecord o : jaegerTestHandler.recordList) {
            if (o.getMessage().contains("Span has already been finished; will not be reported again.")) {
                count++;
            }
        }
        assertThat("Jaeger span should never be called twice.", count, is(0));
    }

    @Test
    void failTracing() {
        assertThrows(WebApplicationException.class, () -> target.path("/test")
                .queryParam("throwError", true)
                .request()
                .get(String.class));

        assertThrows(WebApplicationException.class, () -> target.path("/test")
                .queryParam("throwError", true)
                .request()
                .get(String.class));

        // make sure
        // 2020.10.18 21:25:53 WARNING io.helidon.tracing.jersey.AbstractTracingFilter filter io.helidon.tracing.jersey
        //.AbstractTracingFilter Thread[helidon-3,5,server]: Response filter called twice. Most likely a response with
        //streaming output was returned, where response had 200 status code, but streaming failed with another error. Status:
        //Too Many Requests
        // logged only once

        int count = 0;
        for (LogRecord o : filterTestHandler.recordList) {
            if (o.getLevel() == Level.WARNING) {
                if (o.getMessage().contains("Response filter called twice.")) {
                    count++;
                }
            }
        }
        assertThat("Exactly one warning should be logged when filter is called twice", count, is(1));

        // make sure
        // 08:03:44.288 [helidon-1] WARN  io.jaegertracing.internal.JaegerSpan - Span has already been finished; will not be
        //reported again.
        // not logged at all
        count = 0;
        for (LogRecord o : jaegerTestHandler.recordList) {
            if (o.getMessage().contains("Span has already been finished; will not be reported again.")) {
                count++;
            }
        }
        assertThat("Jaeger span should never be called twice.", count, is(0));
    }

    @Path("/test")
    @RequestScoped
    public static class TestResource {
        @GET
        public Response getTest(@QueryParam("throwError") final boolean throwError) {
            StreamingOutput output = outputStream -> {
                if (throwError) {
                    throw new WebApplicationException("Capacity Exceeded.", 429);
                }
                Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                writer.write("Test Message");
                writer.flush();
            };
            return Response.ok().entity(output).build();
        }
    }

    private static class TestHandler extends Handler {

        List<LogRecord> recordList = new ArrayList<>();

        void reset() {
            recordList.clear();
        }

        @Override
        public void publish(final LogRecord record) {
            recordList.add(record);
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {
            reset();
        }
    }
}
