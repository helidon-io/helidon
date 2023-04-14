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

package io.helidon.tests.integration.webclient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.testing.MatcherWithRetry;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.tracing.opentracing.OpenTracing;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test tracing integration.
 */
class TracingPropagationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void testTracingSuccess() throws ExecutionException, InterruptedException {
        MockTracer mockTracer = new MockTracer();

        WebServer webServer = Main.startServer(mockTracer).await(TIMEOUT);

        Context context = Context.builder().id("tracing-unit-test").build();
        context.register(OpenTracing.create(mockTracer));

        String uri = "http://localhost:" + webServer.port() + "/greet";

        WebClient client = WebClient.builder()
                .baseUri(uri)
                .context(context)
                .config(Config.create().get("client"))
                .addMediaSupport(JsonpSupport.create())
                .build();

        WebClientResponse response = client.get()
                .queryParam("some", "value")
                .fragment("fragment")
                .request()
                .await(TIMEOUT);

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.content().as(JsonObject.class).await(TIMEOUT), notNullValue());
        response.close();

        // the server traces asynchronously, some spans may be written after we receive the response.
        // we need to try to wait for such spans
        MatcherWithRetry.assertThatWithRetry("There should be 3 spans reported", mockTracer::finishedSpans, hasSize(3));
        List<MockSpan> mockSpans = mockTracer.finishedSpans();

        // we need the first span - parentId 0
        MockSpan clientSpan = findSpanWithParentId(mockSpans, 0);
        assertThat(clientSpan.operationName(), is("GET-" + uri));
        List<MockSpan.LogEntry> logEntries = clientSpan.logEntries();
        assertThat(logEntries, empty());
        Map<String, Object> tags = clientSpan.tags();
        assertThat(tags.get(Tags.HTTP_STATUS.getKey()), is(200));

        // now we want to test first child - first WebServer span
        MockSpan wsSpan = findSpanWithParentId(mockSpans, clientSpan.context().spanId());
        assertThat(wsSpan.operationName(), is("HTTP Request"));
        tags = wsSpan.tags();
        assertThat(tags.get(Tags.HTTP_METHOD.getKey()), is("GET"));
        assertThat(tags.get(Tags.HTTP_URL.getKey()), is("/greet?some=value#fragment"));
        assertThat(tags.get(Tags.HTTP_STATUS.getKey()), is(200));
        assertThat(tags.get(Tags.COMPONENT.getKey()), is("helidon-webserver"));

        webServer.shutdown().toCompletableFuture().get();
    }

    private MockSpan findSpanWithParentId(List<MockSpan> mockSpans, long parentId) {
        return mockSpans
                .stream()
                .filter(it -> it.parentId() == parentId)
                .findFirst()
                .orElseGet(() -> Assertions.fail("Could not find span with parent id " + parentId + " in " + mockSpans));
    }
}
