/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.telemetry;

import static java.util.Comparator.comparingLong;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.enterprise.context.ApplicationScoped;
import org.hamcrest.Matchers;

@ApplicationScoped
public class InMemorySpanExporter implements SpanExporter {
    private boolean isStopped = false;
    private static final List<SpanData> finishedSpanItems = new CopyOnWriteArrayList<>();

    /**
     * In-memory span exporter inspired by TCKs.
     */
    public List<SpanData> getFinishedSpanItems(int spanCount) {
        assertSpanCount(spanCount);
        return finishedSpanItems.stream().sorted(comparingLong(SpanData::getStartEpochNanos).reversed())
                .collect(Collectors.toList());
    }

    public void assertSpanCount(int spanCount) {
        Awaitility.await().pollDelay(3, SECONDS).atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(finishedSpanItems.size(), Matchers.is(spanCount)));
    }

    public void reset() {
        finishedSpanItems.clear();
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (isStopped) {
            return CompletableResultCode.ofFailure();
        }
        finishedSpanItems.addAll(spans);
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        finishedSpanItems.clear();
        isStopped = true;
        return CompletableResultCode.ofSuccess();
    }
}