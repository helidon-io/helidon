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

package io.helidon.declarative.tests.compatibility.app;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.tracing.Span;

/**
 * Stores completed spans for compatibility test assertions.
 */
public class TestSpanExporter {
    private final List<RecordedSpan> spans = new CopyOnWriteArrayList<>();

    /**
     * Waits up to ten seconds for a completed span with the given name.
     *
     * @param name span name
     * @return recorded span
     * @throws InterruptedException if interrupted while waiting
     * @throws AssertionError if no matching span is recorded before the timeout
     */
    public RecordedSpan awaitSpan(String name) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            for (RecordedSpan span : spans) {
                if (span.name().equals(name)) {
                    return span;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Span not exported: " + name + ", exported spans: " + spans);
    }

    /**
     * Clears all recorded spans.
     */
    public void clear() {
        spans.clear();
    }

    void record(String name, Span.Kind kind, Map<String, Object> tags, Throwable error) {
        spans.add(new RecordedSpan(name, kind, Map.copyOf(tags), error));
    }

    /**
     * Completed span data used by compatibility test assertions.
     *
     * @param name span name
     * @param kind span kind
     * @param tags span tags
     * @param error failure reported when ending the span, or {@code null} on normal completion
     */
    public record RecordedSpan(String name, Span.Kind kind, Map<String, Object> tags, Throwable error) {
    }
}
