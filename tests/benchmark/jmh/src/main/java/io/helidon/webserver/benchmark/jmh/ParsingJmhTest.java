/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http1.Http1Headers;
import io.helidon.webserver.http1.Http1Prologue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/*
Prologue
original 5633378.812 ± 261834.000 ops/s
new      6842537.897 ± 109193.680 ops/s

Headers
original 2930640.115 ± 39855.689  ops/s
new
 */
@State(Scope.Benchmark)
public class ParsingJmhTest {
    private static final byte[] PROLOGUE_WITH_EOL = "POST /some/path?query=some_query_message_text HTTP/1.1\r\n"
            .getBytes(StandardCharsets.US_ASCII);
    private static final HttpPrologue PROLOGUE = HttpPrologue.create("HTTP", "HTTP", "1.1", Method.GET, "/", false);
    private static final byte[] HEADERS = """
            Host: localhost:8080\r
            User-Agent: curl/7.68.0\r
            Connection: keep-alive\r
            Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7\r
            \r
            """.getBytes(StandardCharsets.US_ASCII);

    //    @Benchmark
    public void prologue(Blackhole bh) {
        DataReader reader = new DataReader(() -> PROLOGUE_WITH_EOL);
        Http1Prologue p = new Http1Prologue(reader, 1024, false);
        HttpPrologue httpPrologue = p.readPrologue();
        bh.consume(httpPrologue.method());
        bh.consume(httpPrologue.query());
        bh.consume(httpPrologue.uriPath());
        bh.consume(httpPrologue);
    }

    @Benchmark
    public void headers(Blackhole bh) {
        DataReader reader = new DataReader(() -> HEADERS);
        Http1Headers p = new Http1Headers(reader, 1024, false);
        WritableHeaders<?> headers = p.readHeaders(PROLOGUE);

        bh.consume(headers);
    }
}
