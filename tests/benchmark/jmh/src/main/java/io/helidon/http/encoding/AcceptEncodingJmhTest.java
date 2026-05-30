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

package io.helidon.http.encoding;

import java.util.List;

import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class AcceptEncodingJmhTest {
    private Headers weighted;
    private Headers wildcard;
    private Headers malformedToken;
    private List<String> serverOrder;

    @Setup
    public void setup() {
        weighted = headers("gzip;q=1.0, identity, br;q=1.0, deflate;q=0.6");
        wildcard = headers("*, gzip;q=0.8, identity;q=0");
        malformedToken = headers("g zip, br");
        serverOrder = List.of("br", "gzip", "deflate");
    }

    @Benchmark
    public AcceptEncoding parseWeighted() {
        return AcceptEncoding.create(weighted);
    }

    @Benchmark
    public AcceptEncoding.CodingQuality parseBestWeighted() {
        return AcceptEncoding.create(weighted)
                .best(serverOrder)
                .orElseThrow();
    }

    @Benchmark
    public List<AcceptEncoding.CodingQuality> parseAcceptedWildcard() {
        return AcceptEncoding.create(wildcard)
                .acceptedCodings(true);
    }

    @Benchmark
    public AcceptEncoding parseMalformedToken() {
        return AcceptEncoding.create(malformedToken);
    }

    private static Headers headers(String acceptEncoding) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ACCEPT_ENCODING, acceptEncoding);
        return headers;
    }
}
