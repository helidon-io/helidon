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
package io.helidon.webclient.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.common.testing.junit5.MatcherWithRetry;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import static org.hamcrest.Matchers.iterableWithSize;

final class TestSpanExporter implements SpanExporter, AutoCloseable {
    private static final int RETRY_COUNT = Integer.getInteger(TestSpanExporter.class.getName() + ".test.retryCount", 120);
    private static final int RETRY_DELAY_MS = Integer.getInteger(TestSpanExporter.class.getName() + ".test.retryDelayMs", 500);

    private final List<SpanData> spanData = new CopyOnWriteArrayList<>();

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        spanData.addAll(spans);
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        spanData.clear();
        return CompletableResultCode.ofSuccess();
    }

    List<SpanData> spanData(int expectedCount) {
        return MatcherWithRetry.assertThatWithRetry("Expected span count",
                                                    () -> new ArrayList<>(spanData),
                                                    iterableWithSize(expectedCount),
                                                    RETRY_COUNT,
                                                    RETRY_DELAY_MS);
    }

    @Override
    public void close() {
        shutdown();
    }
}
