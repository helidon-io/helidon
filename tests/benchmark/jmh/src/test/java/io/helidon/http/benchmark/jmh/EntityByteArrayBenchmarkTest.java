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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class EntityByteArrayBenchmarkTest {
    @ParameterizedTest
    @CsvSource({"0, 1", "64, 3", "16384, 1024", "1048576, 16384", "1048577, 16384"})
    void bothPathsReadTheCompletePayloadOnRepeatedInvocations(int bodyBytes, int chunkBytes) {
        var benchmark = new EntityByteArrayJmhBenchmark();
        benchmark.bodyBytes = bodyBytes;
        benchmark.chunkBytes = chunkBytes;
        benchmark.setup();
        byte[] expected = new byte[bodyBytes];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i * 31 + 17);
        }

        assertThat(benchmark.unknownLength(), is(expected));
        assertThat(benchmark.exactLength(), is(expected));
        assertThat(benchmark.unknownLength(), is(expected));
        assertThat(benchmark.exactLength(), is(expected));
    }
}
