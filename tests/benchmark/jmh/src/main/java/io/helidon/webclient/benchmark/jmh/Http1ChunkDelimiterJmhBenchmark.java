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

package io.helidon.webclient.benchmark.jmh;

import io.helidon.common.buffers.DataReader;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

public class Http1ChunkDelimiterJmhBenchmark {
    private static final byte[] CRLF = {'\r', '\n'};

    @Benchmark
    public DataReader skipDelimiter(ReaderState state) {
        state.reader.skip(2);
        return state.reader;
    }

    @Benchmark
    public DataReader readAndValidateDelimiter(ReaderState state) {
        if (state.failure != null) {
            throw state.failure;
        }
        if (state.reader.read() != '\r' || state.reader.read() != '\n') {
            throw new IllegalStateException("Unexpected delimiter");
        }
        return state.reader;
    }

    @State(Scope.Thread)
    public static class ReaderState {
        private DataReader reader;
        private RuntimeException failure;

        @Setup(Level.Iteration)
        public void setup() {
            reader = DataReader.create(() -> CRLF);
            failure = null;
        }
    }
}
