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

package io.helidon.http.benchmark.jmh;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class HeadersCreationJmhBenchmark {
    private static final HeaderName CUSTOM_HEADER = HeaderNames.create("X-Custom");

    @Benchmark
    public WritableHeaders<?> create() {
        return WritableHeaders.create();
    }

    @Benchmark
    public void createAndCheckMissingCustomHeader(Blackhole blackhole) {
        WritableHeaders<?> headers = WritableHeaders.create();
        blackhole.consume(headers.contains(CUSTOM_HEADER));
        blackhole.consume(headers);
    }

    @Benchmark
    public WritableHeaders<?> createAndRemoveMissingCustomHeader() {
        return WritableHeaders.create()
                .remove(CUSTOM_HEADER);
    }

    @Benchmark
    public WritableHeaders<?> createAndClear() {
        return WritableHeaders.create()
                .clear();
    }
}
