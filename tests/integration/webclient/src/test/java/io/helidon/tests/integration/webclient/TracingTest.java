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

package io.helidon.tests.integration.webclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.json.JsonObject;

import io.helidon.common.context.Context;
import io.helidon.media.jsonp.common.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.iterableWithSize;

/**
 * Test tracing integration.
 */
class TracingTest extends TestParent {

    @Test
    void testTracingNoServerSuccess() throws ExecutionException, InterruptedException {
        MockTracer mockTracer = new MockTracer();
        String uri = "http://localhost:" + webServer.port() + "/greet";
        Context context = Context.builder().id("tracing-unit-test").build();
        context.register(mockTracer);

        WebClient client = WebClient.builder()
                .baseUri(uri)
                .context(context)
                .addMediaSupport(JsonpSupport.create())
                .build();

        WebClientResponse response = client.get()
                .request()
                .toCompletableFuture()
                .get();

        // we must fully read entity for tracing to finish
        response.content().as(JsonObject.class)
                .toCompletableFuture()
                .get();

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertThat(mockSpans, iterableWithSize(1));

        MockSpan theSpan = mockSpans.get(0);

        assertThat(theSpan.operationName(), is("GET-" + uri));

        List<MockSpan.LogEntry> logEntries = theSpan.logEntries();
        assertThat(logEntries, empty());

        Map<String, Object> tags = theSpan.tags();
        assertThat(tags.get(Tags.HTTP_STATUS.getKey()), is(200));
        assertThat(tags.get(Tags.HTTP_METHOD.getKey()), is("GET"));
        assertThat(tags.get(Tags.HTTP_URL.getKey()), is(uri));
        assertThat(tags.get(Tags.COMPONENT.getKey()), is("helidon-webclient"));
    }

    @Test
    void testTracingNoServerFailure() throws ExecutionException, InterruptedException {
        MockTracer mockTracer = new MockTracer();

        Context context = Context.builder().id("tracing-unit-test").build();
        context.register(mockTracer);

        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/greet")
                .context(context)
                .addMediaSupport(JsonpSupport.create())
                .build();

        WebClientResponse response = client.get()
                .path("/error")
                .request()
                .toCompletableFuture()
                .get();

        // we must fully read entity, as otherwise tracing does not finish
        response.content().as(String.class)
                .toCompletableFuture()
                .get();

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertThat(mockSpans, iterableWithSize(1));

        MockSpan theSpan = mockSpans.get(0);

        assertThat(theSpan.operationName(), is("GET-http://localhost:" + webServer.port() + "/greet/error"));

        List<MockSpan.LogEntry> logEntries = theSpan.logEntries();
        assertThat(logEntries, iterableWithSize(1));
        MockSpan.LogEntry logEntry = logEntries.get(0);
        Map<String, ?> fields = logEntry.fields();
        assertThat(fields.get("event"), is("error"));
        assertThat(fields.get("message"), is("Response HTTP status: 404"));
        assertThat(fields.get("error.kind"), is("ClientError"));

        Map<String, Object> tags = theSpan.tags();
        assertThat(tags.get(Tags.HTTP_STATUS.getKey()), is(404));
    }
}
