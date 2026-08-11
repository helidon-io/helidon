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

package io.helidon.webserver.staticcontent;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class StaticContentPreconditionsBenchmark {
    private static final Instant LAST_MODIFIED = Instant.parse("2026-08-11T12:34:56.123Z");
    private static final String ETAG = String.valueOf(LAST_MODIFIED.toEpochMilli());
    private static final Header LAST_MODIFIED_HEADER = HeaderValues.create(HeaderNames.LAST_MODIFIED,
                                                                            true,
                                                                            false,
                                                                            "Tue, 11 Aug 2026 12:34:56 GMT");
    private static final BiConsumer<ServerResponseHeaders, Instant> SET_MODIFIED =
            (headers, _) -> headers.set(LAST_MODIFIED_HEADER);

    private ServerRequestHeaders noConditionalHeader;
    private ServerRequestHeaders matchingIfNoneMatch;
    private ServerRequestHeaders nonMatchingIfNoneMatch;
    private ServerRequestHeaders quotedCommaList;
    private ServerRequestHeaders largeNonMatchingList;

    @Setup
    public void setup() {
        noConditionalHeader = ServerRequestHeaders.create();
        matchingIfNoneMatch = requestHeaders('"' + ETAG + '"');
        nonMatchingIfNoneMatch = requestHeaders("\"different-etag\"");
        quotedCommaList = requestHeaders("\"tag,with,commas\", \"different-etag\"");

        StringBuilder largeValue = new StringBuilder(8_000);
        for (int i = 0; i < 800; i++) {
            if (!largeValue.isEmpty()) {
                largeValue.append(", ");
            }
            largeValue.append('"').append("tag").append(i).append('"');
        }
        largeNonMatchingList = requestHeaders(largeValue.toString());
    }

    @Benchmark
    public ServerResponseHeaders noConditionalHeader200() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        process(noConditionalHeader, responseHeaders);
        return responseHeaders;
    }

    @Benchmark
    public void matchingIfNoneMatch304(Blackhole blackhole) {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        try {
            process(matchingIfNoneMatch, responseHeaders);
            throw new AssertionError("Expected 304 response");
        } catch (HttpException e) {
            if (e.status() != Status.NOT_MODIFIED_304) {
                throw e;
            }
            blackhole.consume(responseHeaders);
            blackhole.consume(e);
        }
    }

    @Benchmark
    public ServerResponseHeaders nonMatchingIfNoneMatch200() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        process(nonMatchingIfNoneMatch, responseHeaders);
        return responseHeaders;
    }

    @Benchmark
    public ServerResponseHeaders quotedCommaShortList200() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        process(quotedCommaList, responseHeaders);
        return responseHeaders;
    }

    @Benchmark
    public ServerResponseHeaders nearLimitIfNoneMatch200() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        process(largeNonMatchingList, responseHeaders);
        return responseHeaders;
    }

    private static void process(ServerRequestHeaders requestHeaders, ServerResponseHeaders responseHeaders) {
        StaticContentHandler.processPreconditions(ETAG,
                                                  LAST_MODIFIED,
                                                  requestHeaders,
                                                  responseHeaders,
                                                  SET_MODIFIED);
    }

    private static ServerRequestHeaders requestHeaders(String value) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderValues.create(HeaderNames.IF_NONE_MATCH, value));
        return ServerRequestHeaders.create(headers);
    }
}
