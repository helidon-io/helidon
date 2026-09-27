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

package io.helidon.webserver.benchmark.jmh;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.http1.Http1Prologue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class Http1ServerRequestTargetBenchmark {
    private static final int MAX_PROLOGUE_LENGTH = 4096;

    @Param
    private Form form;

    @Param({"32", "4000"})
    private int targetLength;

    private byte[] requestLine;

    @Setup
    public void setup() {
        String prefix = switch (form) {
        case ORIGIN -> "/";
        case ABSOLUTE -> "http://example.test/";
        };
        String requestTarget = prefix + "a".repeat(targetLength - prefix.length());
        requestLine = ("GET " + requestTarget + " HTTP/1.1\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    @Benchmark
    public HttpPrologue parse() {
        return new Http1Prologue(DataReader.create(() -> requestLine), MAX_PROLOGUE_LENGTH, true).readPrologue();
    }

    public enum Form {
        ORIGIN,
        ABSOLUTE
    }
}
