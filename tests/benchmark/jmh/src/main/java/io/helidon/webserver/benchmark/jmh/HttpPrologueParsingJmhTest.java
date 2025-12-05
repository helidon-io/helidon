/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class HttpPrologueParsingJmhTest {
    private static final byte[] TECHEMPOWER_PROLOGUE = "GET /plaintext HTTP/1.1\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENCODED_PROLOGUE =
            "GET /one/two?a=b%26c=d&e=f&e=g&h=x%63%23e%3c#a%20frag%23ment HTTP/1.1\r\n".getBytes(
                    StandardCharsets.UTF_8);

    @Benchmark
    public void techEmpower(Blackhole bh) {
        HttpPrologue prologue = new Http1Prologue(DataReader.create(() -> TECHEMPOWER_PROLOGUE), 1024, false)
                .readPrologue();
        bh.consume(prologue);
    }

    @Benchmark
    public void encoded(Blackhole bh) {
        HttpPrologue prologue = new Http1Prologue(DataReader.create(() -> ENCODED_PROLOGUE), 1024, false)
                .readPrologue();
        bh.consume(prologue);
    }
}
