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
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.tracing.Span;

public class TestSpanExporter {
    private final List<RecordedSpan> spans = new CopyOnWriteArrayList<>();

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

    public void clear() {
        spans.clear();
    }

    void record(String name, Span.Kind kind, Map<String, Object> tags, Throwable error) {
        spans.add(new RecordedSpan(name, kind, Map.copyOf(tags), error));
    }

    public record RecordedSpan(String name, Span.Kind kind, Map<String, Object> tags, Throwable error) {
    }
}
