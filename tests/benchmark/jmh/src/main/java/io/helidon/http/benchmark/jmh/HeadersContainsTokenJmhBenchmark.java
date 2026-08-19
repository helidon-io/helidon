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

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
public class HeadersContainsTokenJmhBenchmark {
    private Headers commonHeaders;
    private Headers singleTokenHeaders;
    private Headers repeatedTokenHeaders;
    private Headers quotedTokenHeaders;
    private Header connectionClose;
    private Header connectionKeepAlive;
    private Header expectContinue;
    private Header repeatedTokens;
    private Header quotedToken;

    @Setup(Level.Trial)
    public void setup() {
        commonHeaders = WritableHeaders.create()
                .add(HeaderNames.HOST, "localhost");
        singleTokenHeaders = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "keep-alive");
        repeatedTokenHeaders = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "upgrade, keep-alive")
                .add(HeaderNames.CONNECTION, "close");
        quotedTokenHeaders = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "\"upgrade, websocket\", keep-alive");

        connectionClose = HeaderValues.CONNECTION_CLOSE;
        connectionKeepAlive = HeaderValues.CONNECTION_KEEP_ALIVE;
        expectContinue = HeaderValues.EXPECT_100;
        repeatedTokens = HeaderValues.create(HeaderNames.CONNECTION, "upgrade", "close");
        quotedToken = HeaderValues.create(HeaderNames.CONNECTION, "\"upgrade, websocket\"");
    }

    @Benchmark
    public void absentConnection(Blackhole blackhole) {
        blackhole.consume(commonHeaders.containsToken(connectionClose));
    }

    @Benchmark
    public void absentExpect(Blackhole blackhole) {
        blackhole.consume(commonHeaders.containsToken(expectContinue));
    }

    @Benchmark
    public void presentMatch(Blackhole blackhole) {
        blackhole.consume(singleTokenHeaders.containsToken(connectionKeepAlive));
    }

    @Benchmark
    public void presentMiss(Blackhole blackhole) {
        blackhole.consume(singleTokenHeaders.containsToken(connectionClose));
    }

    @Benchmark
    public void commaSeparatedAndRepeated(Blackhole blackhole) {
        blackhole.consume(repeatedTokenHeaders.containsToken(repeatedTokens));
    }

    @Benchmark
    public void quotedComma(Blackhole blackhole) {
        blackhole.consume(quotedTokenHeaders.containsToken(quotedToken));
    }
}
