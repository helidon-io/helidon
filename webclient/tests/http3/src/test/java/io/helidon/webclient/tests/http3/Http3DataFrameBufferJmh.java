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

package io.helidon.webclient.tests.http3;

import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.http3.Http3Protocol;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class Http3DataFrameBufferJmh {
    public static void main(String[] args) throws Exception {
        Options options = new OptionsBuilder()
                .include(Http3DataFrameBufferJmh.class.getSimpleName())
                .shouldFailOnError(true)
                .addProfiler("gc")
                .build();
        new Runner(options).run();
    }

    @Benchmark
    public byte[] materialized(DataState state) {
        return BufferData.create(Http3Protocol.encodeDataFrame(state.payload)).readBytes();
    }

    @Benchmark
    public byte[] ownershipHandoff(DataState state) {
        return Http3Protocol.encodeDataFrameBuffer(state.payload, 0, state.payload.length).readBytes();
    }

    @State(Scope.Thread)
    public static class DataState {
        @Param({"1024", "16384", "1048576"})
        int payloadSize;

        private byte[] payload;

        @Setup(Level.Trial)
        public void setup() {
            payload = new byte[payloadSize];
        }
    }
}
