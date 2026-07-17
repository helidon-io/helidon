/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.grpc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.common.testing.junit5.MatcherWithRetry;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import static org.hamcrest.Matchers.iterableWithSize;

class TestSpanExporter implements SpanExporter {
    private final List<SpanData> spanData = new CopyOnWriteArrayList<>();
    private final int retryCount = Integer.getInteger(TestSpanExporter.class.getName() + ".test.retryCount", 120);
    private final int retryDelayMs = Integer.getInteger(TestSpanExporter.class.getName() + ".test.retryDelayMs", 500);
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
        return MatcherWithRetry.assertThatWithRetry("Expected span count",
                                                    () -> new ArrayList<>(spanData),
                                                    iterableWithSize(expectedCount),
                                                    retryCount,
                                                    retryDelayMs);
    }

    void clear() {
        spanData.clear();
    }

    private enum State {
        READY,
        STOPPED
    }
}
