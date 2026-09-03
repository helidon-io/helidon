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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class AcceptEncodingJmhTest {
    private Headers absent;
    private Headers weighted;
    private Headers wildcard;
    private Headers manyAcceptedCodings;
    private Headers collidingCodings;
    private Headers malformedToken;
    private Headers malformedList;
    private AcceptEncoding parsedWeighted;
    private AcceptEncoding parsedWildcard;
    private List<String> serverOrder;
    private ContentEncodingContext contentEncodingContext;
    private Headers canonicalNegotiation;
    private Headers aliasNegotiation;
    private Headers wildcardNegotiation;
    private Headers identityNegotiation;
    private Headers rejectedNegotiation;

    @Setup
    public void setup() {
        absent = WritableHeaders.create();
        weighted = headers("gzip;q=1.0, identity, br;q=1.0, deflate;q=0.6");
        wildcard = headers("*, gzip;q=0.8, identity;q=0");
        StringBuilder manyAcceptedCodings = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            if (!manyAcceptedCodings.isEmpty()) {
                manyAcceptedCodings.append(',');
            }
            manyAcceptedCodings.append("coding").append(i).append(";q=0.5");
        }
        this.manyAcceptedCodings = headers(manyAcceptedCodings.toString());
        collidingCodings = headers(String.join(",", collidingTokens(256)));
        malformedToken = headers("g zip, br");
        malformedList = headers("@,".repeat(4095) + "@");
        parsedWeighted = AcceptEncoding.create(weighted);
        parsedWildcard = AcceptEncoding.create(wildcard);
        serverOrder = List.of("br", "gzip", "deflate");
        contentEncodingContext = ContentEncodingContext.builder()
                .addContentEncoding(new BenchmarkEncoding("br", Set.of("br"), ContentEncoder.NO_OP))
                .addContentEncoding(new BenchmarkEncoding("gzip", Set.of("gzip", "x-gzip"), ContentEncoder.NO_OP))
                .build();
        canonicalNegotiation = headers("gzip, identity;q=0");
        aliasNegotiation = headers("x-gzip, identity;q=0");
        wildcardNegotiation = headers("*, identity;q=0");
        identityNegotiation = headers("identity");
        rejectedNegotiation = headers("br;q=0, gzip;q=0, x-gzip;q=0, identity;q=0");
    }

    @Benchmark
    public AcceptEncoding parseAbsent() {
        return AcceptEncoding.create(absent);
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
    public AcceptEncoding.CodingQuality bestWeighted() {
        return parsedWeighted.best(serverOrder)
                .orElseThrow();
    }

    @Benchmark
    public List<AcceptEncoding.CodingQuality> parseAcceptedWildcard() {
        return AcceptEncoding.create(wildcard)
                .acceptedCodings(true);
    }

    @Benchmark
    public List<AcceptEncoding.CodingQuality> parseManyAcceptedCodings() {
        return AcceptEncoding.create(manyAcceptedCodings)
                .acceptedCodings(false);
    }

    @Benchmark
    public AcceptEncoding parseCollidingCodings() {
        return AcceptEncoding.create(collidingCodings);
    }

    @Benchmark
    public void parseAcceptedWildcardRepeated(Blackhole blackhole) {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(wildcard);
        blackhole.consume(acceptEncoding.acceptedCodings(true));
        blackhole.consume(acceptEncoding.acceptedCodings(false));
        blackhole.consume(acceptEncoding.acceptedCodings(true));
    }

    @Benchmark
    public void acceptedWildcardRepeated(Blackhole blackhole) {
        blackhole.consume(parsedWildcard.acceptedCodings(true));
        blackhole.consume(parsedWildcard.acceptedCodings(false));
        blackhole.consume(parsedWildcard.acceptedCodings(true));
    }

    @Benchmark
    public AcceptEncoding parseMalformedToken() {
        return AcceptEncoding.create(malformedToken);
    }

    @Benchmark
    public AcceptEncoding parseMalformedList() {
        return AcceptEncoding.create(malformedList);
    }

    @Benchmark
    public ContentEncoder negotiateCanonical() {
        return contentEncodingContext.encoder(canonicalNegotiation);
    }

    @Benchmark
    public ContentEncoder negotiateAlias() {
        return contentEncodingContext.encoder(aliasNegotiation);
    }

    @Benchmark
    public ContentEncoder negotiateWildcard() {
        return contentEncodingContext.encoder(wildcardNegotiation);
    }

    @Benchmark
    public ContentEncoder negotiateIdentity() {
        return contentEncodingContext.encoder(identityNegotiation);
    }

    @Benchmark
    public Status negotiateRejected() {
        try {
            contentEncodingContext.encoder(rejectedNegotiation);
            throw new AssertionError("Expected rejected negotiation");
        } catch (HttpException e) {
            return e.status();
        }
    }

    private static Headers headers(String acceptEncoding) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ACCEPT_ENCODING, acceptEncoding);
        return headers;
    }

    private static List<String> collidingTokens(int count) {
        List<String> result = new ArrayList<>(count);
        int pairs = Integer.SIZE - Integer.numberOfLeadingZeros(count - 1);
        for (int value = 0; value < count; value++) {
            StringBuilder token = new StringBuilder(pairs * 2);
            for (int bit = 0; bit < pairs; bit++) {
                token.append((value & (1 << bit)) == 0 ? "a~" : "b_");
            }
            result.add(token.toString());
        }
        return result;
    }

    private record BenchmarkEncoding(String type, Set<String> ids, ContentEncoder encoder) implements ContentEncoding {
        @Override
        public String name() {
            return type;
        }

        @Override
        public boolean supportsEncoding() {
            return true;
        }

        @Override
        public boolean supportsDecoding() {
            return false;
        }

        @Override
        public ContentDecoder decoder() {
            throw new UnsupportedOperationException();
        }
    }
}
