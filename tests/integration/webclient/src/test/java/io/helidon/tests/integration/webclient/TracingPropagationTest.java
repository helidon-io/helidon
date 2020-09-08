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

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Test tracing integration.
 */
class TracingPropagationTest {

    @Test
    void testTracingSuccess() throws ExecutionException, InterruptedException {
        MockTracer mockTracer = new MockTracer();

        WebServer webServer = Main.startServer(mockTracer).toCompletableFuture().get();

        Context context = Context.builder().id("tracing-unit-test").build();
        context.register(mockTracer);

        String uri = "http://localhost:" + webServer.port() + "/greet";

        WebClient client = WebClient.builder()
                .baseUri(uri)
                .context(context)
                .config(Config.create().get("client"))
                .build();

        client.get()
                .queryParam("some", "value")
                .fragment("fragment")
                .request()
                .thenCompose(WebClientResponse::close)
                .toCompletableFuture()
                .get();

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertThat("At least one client and one server span expected", mockSpans.size(), greaterThanOrEqualTo(2));

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
