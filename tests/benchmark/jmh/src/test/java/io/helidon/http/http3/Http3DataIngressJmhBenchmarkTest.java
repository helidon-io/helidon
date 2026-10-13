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

package io.helidon.http.http3;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.infra.BenchmarkParams;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http3DataIngressJmhBenchmarkTest {
    @Test
    void realReaderBulkMethodsConsumeAllChunksAndPreserveTrailersOrGuardRange() {
        var benchmark = new Http3DataIngressJmhBenchmark();
        for (int size : List.of(1, 1024, 131072, 1048576)) {
            for (String method : List.of("heapCompatibilityMaterializedBulk", "heapSliceBulk", "heapSliceTrailersCompletion")) {
                var state = state(size, method);
                try {
                    int actual = switch (method) {
                        case "heapCompatibilityMaterializedBulk" -> benchmark.heapCompatibilityMaterializedBulk(state);
                        case "heapSliceBulk" -> benchmark.heapSliceBulk(state);
                        case "heapSliceTrailersCompletion" -> benchmark.heapSliceTrailersCompletion(state);
                        default -> throw new IllegalArgumentException(method);
                    };
                    assertThat(method + "/" + size, actual, is(((size - 1) * 31 + 7) & 0xFF));
                } finally {
                    // The real fixture checks every destination byte, slice consumption and trailer/guard closure.
                    state.tearDownInvocation();
                }
            }
        }
    }

    @Test
    void legacyAndBufferOriginControlsRetainTheSameFullPayloadMeaning() {
        var benchmark = new Http3DataIngressJmhBenchmark();
        for (int size : List.of(1024, 131072)) {
            for (String method : List.of("heapLegacyMaterializedBulk", "unknownOriginSliceFallbackBulk",
                    "directOriginMaterializedFallbackBulk", "directOriginSliceFallbackBulk")) {
                var state = state(size, method);
                try {
                    int actual = switch (method) {
                        case "heapLegacyMaterializedBulk" -> benchmark.heapLegacyMaterializedBulk(state);
                        case "unknownOriginSliceFallbackBulk" -> benchmark.unknownOriginSliceFallbackBulk(state);
                        case "directOriginMaterializedFallbackBulk" -> benchmark.directOriginMaterializedFallbackBulk(state);
                        case "directOriginSliceFallbackBulk" -> benchmark.directOriginSliceFallbackBulk(state);
                        default -> throw new IllegalArgumentException(method);
                    };
                    assertThat(method + "/" + size, actual, is(((size - 1) * 31 + 7) & 0xFF));
                } finally {
                    state.tearDownInvocation();
                }
            }
        }
    }

    private static Http3DataIngressJmhBenchmark.DataState state(int size, String method) {
        var state = new Http3DataIngressJmhBenchmark.DataState();
        state.payloadSize = size;
        state.setUpTrial();
        BenchmarkParams params = mock(BenchmarkParams.class);
        when(params.getBenchmark()).thenReturn(Http3DataIngressJmhBenchmark.class.getName() + "." + method);
        state.setUpInvocation(params);
        return state;
    }
}
