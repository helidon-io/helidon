/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.telemetry.mp.filterselectivity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.common.testing.junit5.MatcherWithRetry;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.enterprise.context.ApplicationScoped;

import static org.hamcrest.Matchers.iterableWithSize;

// Partially inspired by the MP Telemetry TCK InMemorySpanExporter.
@ApplicationScoped
public class TestSpanExporter implements SpanExporter {

    private final List<SpanData> spanData = new CopyOnWriteArrayList<>();
    private final System.Logger LOGGER = System.getLogger(TestSpanExporter.class.getName());

    private final int RETRY_COUNT = Integer.getInteger(TestSpanExporter.class.getName() + ".test.retryCount", 120);
    private final int RETRY_DELAY_MS = Integer.getInteger(TestSpanExporter.class.getName() + ".test.retryDelayMs", 500);


    private enum State {READY, STOPPED}

    private State state = State.READY;

    @Override
    public CompletableResultCode export(Collection<SpanData> collection) {
        if (state == State.STOPPED) {
            return CompletableResultCode.ofFailure();
        }
        spanData.addAll(collection);
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        state = State.STOPPED;
        spanData.clear();
        return CompletableResultCode.ofSuccess();
    }

    List<SpanData> spanData(int expectedCount) {
        long startTime = 0;
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            startTime = System.currentTimeMillis();
        }
        var result = MatcherWithRetry.assertThatWithRetry("Expected span count",
                                             () -> new ArrayList<>(spanData),
                                             iterableWithSize(expectedCount),
                                             RETRY_COUNT,
                                             RETRY_DELAY_MS);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "spanData waited "
                    + (System.currentTimeMillis() - startTime)
                    + " ms for expected spans to arrive.");
        }
        return result;
    }

    List<SpanData> spanData(Duration delay) throws InterruptedException {
        Thread.sleep(delay);
        return new ArrayList<>(spanData);
    }

    void clear() {
        spanData.clear();
    }
}
