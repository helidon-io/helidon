/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import io.helidon.http.BadRequestException;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentEncodingSupportTest {

    @Test
    void testAcceptEncodingParserOne() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("gzip"));

        assertQuality(acceptEncoding.match("gzip", false).orElseThrow(), "gzip", 1D, false);
        assertThat(acceptEncoding.best(List.of("gzip")).orElseThrow().coding(), is("gzip"));
    }

    @Test
    void testAcceptEncodingParserAbsentUsesSingleton() {
        Headers headers = WritableHeaders.create();

        AcceptEncoding first = AcceptEncoding.create(headers);
        AcceptEncoding second = AcceptEncoding.create(headers);

        assertThat(first.present(), is(false));
        assertThat(first.valid(), is(true));
        assertThat(second, sameInstance(first));
    }

    @Test
    void testAcceptEncodingParserMany() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("gzip,compress,  br  "));

        assertQuality(acceptEncoding.match("gzip", false).orElseThrow(), "gzip", 1D, false);
        assertQuality(acceptEncoding.match("compress", false).orElseThrow(), "compress", 1D, false);
        assertQuality(acceptEncoding.match("br", false).orElseThrow(), "br", 1D, false);
        assertThat(acceptEncoding.best(List.of("br", "compress", "gzip")).orElseThrow().coding(), is("gzip"));
    }

    @Test
    void testAcceptEncodingParserWithQs() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("gzip;q=1.0, deflate;q=0.6,  identity;q=0.3"));

        assertQuality(acceptEncoding.match("gzip", false).orElseThrow(), "gzip", 1D, false);
        assertQuality(acceptEncoding.match("deflate", false).orElseThrow(), "deflate", 0.6D, false);
        assertQuality(acceptEncoding.identity().orElseThrow(), "identity", 0.3D, false);
        assertThat(acceptEncoding.best(List.of("deflate", "gzip")).orElseThrow().coding(), is("gzip"));
    }

    @Test
    void testAcceptEncodingParserTreatsQParameterCaseInsensitively() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("gzip; Q=0.5"));

        assertThat(acceptEncoding.valid(), is(true));
        assertQuality(acceptEncoding.match("gzip", false).orElseThrow(), "gzip", 0.5D, false);
    }

    @Test
    void testAcceptEncodingParserTreatsUnknownCodingAsValid() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("zstd, identity;q=0"));

        assertThat(acceptEncoding.valid(), is(true));
        assertQuality(acceptEncoding.match("zstd", false).orElseThrow(), "zstd", 1D, false);
        assertThat(acceptEncoding.best(List.of("gzip")).isEmpty(), is(true));
    }

    @Test
    void testCodingQualityEqualityWorksAcrossImplementations() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("*;q=0.5"));
        AcceptEncoding.CodingQuality wildcardEntry = acceptEncoding.acceptedCodings(true).getFirst();
        AcceptEncoding.CodingQuality wildcardMatch = acceptEncoding.match("*", true).orElseThrow();

        assertThat(wildcardEntry, is(wildcardMatch));
        assertThat(wildcardMatch, is(wildcardEntry));
        assertThat(wildcardEntry.hashCode(), is(wildcardMatch.hashCode()));
    }

    @Test
    void testAcceptedCodingsUsesCachedImmutableViews() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("gzip;q=0.8, br, identity, *;q=0.7"));

        List<AcceptEncoding.CodingQuality> noWildcard = acceptEncoding.acceptedCodings(false);
        List<AcceptEncoding.CodingQuality> wildcard = acceptEncoding.acceptedCodings(true);

        assertThat(acceptEncoding.acceptedCodings(false), sameInstance(noWildcard));
        assertThat(acceptEncoding.acceptedCodings(true), sameInstance(wildcard));
        assertThat(noWildcard.size(), is(2));
        assertQuality(noWildcard.get(0), "br", 1D, false);
        assertQuality(noWildcard.get(1), "gzip", 0.8D, false);
        assertThat(wildcard.size(), is(3));
        assertQuality(wildcard.get(2), "*", 0.7D, true);
        assertThrows(UnsupportedOperationException.class, noWildcard::clear);

        AcceptEncoding wildcardFirst = AcceptEncoding.create(headers("gzip;q=0.8, br, identity, *;q=0.7"));
        List<AcceptEncoding.CodingQuality> wildcardFirstWithWildcard = wildcardFirst.acceptedCodings(true);
        List<AcceptEncoding.CodingQuality> wildcardFirstNoWildcard = wildcardFirst.acceptedCodings(false);
        assertThat(wildcardFirst.acceptedCodings(true), sameInstance(wildcardFirstWithWildcard));
        assertThat(wildcardFirst.acceptedCodings(false), sameInstance(wildcardFirstNoWildcard));

        AcceptEncoding rejectedWildcard = AcceptEncoding.create(headers("gzip, *;q=0"));
        List<AcceptEncoding.CodingQuality> rejectedWildcardNoWildcard = rejectedWildcard.acceptedCodings(false);
        assertThat(rejectedWildcard.acceptedCodings(true), sameInstance(rejectedWildcardNoWildcard));
    }

    @Test
    void testAcceptEncodingParserRejectsInvalidCodingToken() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("g zip, identity;q=0"));

        assertThat(acceptEncoding.valid(), is(false));
    }

    @Test
    void testAcceptEncodingParserRejectsUnexpectedParameters() {
        assertInvalidAcceptEncoding("gzip;level=1");
        assertInvalidAcceptEncoding("gzip;q=0.5;level=1");
        assertInvalidAcceptEncoding("gzip;q=0.5;q=0.4");
        assertInvalidAcceptEncoding("gzip;");
        assertInvalidAcceptEncoding("gzip;q=0.5;");
        assertInvalidAcceptEncoding("gzip;q =0.5");
        assertInvalidAcceptEncoding("gzip;q= 0.5");
    }

    @Test
    void testAcceptEncodingParserRejectsInvalidQvalueSyntax() {
        assertInvalidAcceptEncoding("gzip;q=.5");
        assertInvalidAcceptEncoding("gzip;q=00.5");
        assertInvalidAcceptEncoding("gzip;q=0.1234");
        assertInvalidAcceptEncoding("gzip;q=1.001");
    }

    @Test
    void testRuntimeEncoderRejectsQZero() {
        ContentEncodingContext context = context();

        assertThat(context.encoder(headers("gzip;q=0")), sameInstance(ContentEncoder.NO_OP));
    }

    @Test
    void testRuntimeEncoderHonorsIdentityPreference() {
        ContentEncodingContext context = context(gzipEncoder());

        assertThat(context.encoder(headers("gzip;q=0.5, identity;q=1")), sameInstance(ContentEncoder.NO_OP));
    }

    @Test
    void testRuntimeEncoderUsesHeaderOrderBetweenExplicitIdentityAndCoding() {
        ContentEncoder gzipEncoder = gzipEncoder();
        ContentEncodingContext context = context(gzipEncoder);

        assertThat(context.encoder(headers("gzip, identity")), sameInstance(gzipEncoder));
        assertThat(context.encoder(headers("identity, gzip")), sameInstance(ContentEncoder.NO_OP));
    }

    @Test
    void testBestUsesHeaderOrderWhenIdentityIsBetweenConcreteCodings() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("gzip, identity, br"));

        assertThat(acceptEncoding.best(List.of("br", "gzip")).orElseThrow().coding(), is("gzip"));
    }

    @Test
    void testBestUsesIdentityWhenItPrecedesEqualQualityCodings() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("identity, gzip, br"));

        assertQuality(acceptEncoding.best(List.of("br", "gzip")).orElseThrow(), "identity", 1D, false);
    }

    @Test
    void testBestWildcardReturnsConcreteServerCoding() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("*;q=0.5, identity;q=0"));

        assertQuality(acceptEncoding.best(List.of("br")).orElseThrow(), "br", 0.5D, true);
    }

    @Test
    void testBestWildcardUsesFallbackOrder() {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers("*;q=0.5, identity;q=0"));

        assertQuality(acceptEncoding.best(List.of("br", "gzip")).orElseThrow(), "br", 0.5D, true);
    }

    @Test
    void testContentEncodingIdsExcludeIdentity() {
        assertThat(context().contentEncodingIds(), is(List.of("gzip")));
        assertThat(ContentEncodingContext.create().contentEncodingIds(), is(List.of()));
    }

    @Test
    void testContentEncodingIdsDefaultUsesPrototype() {
        ContentEncodingContextConfig prototype = context().prototype();
        ContentEncodingContext context = new ContentEncodingContext() {
            @Override
            public boolean contentEncodingEnabled() {
                return true;
            }

            @Override
            public boolean contentDecodingEnabled() {
                return false;
            }

            @Override
            public boolean contentEncodingSupported(String encodingId) {
                return "gzip".equals(encodingId);
            }

            @Override
            public boolean contentDecodingSupported(String encodingId) {
                return false;
            }

            @Override
            public ContentEncoder encoder(String encodingId) {
                return gzipEncoder();
            }

            @Override
            public ContentDecoder decoder(String encodingId) {
                return ContentDecoder.NO_OP;
            }

            @Override
            public ContentEncoder encoder(Headers headers) {
                return ContentEncoder.NO_OP;
            }

            @Override
            public ContentEncodingContextConfig prototype() {
                return prototype;
            }
        };

        assertThat(context.contentEncodingIds(), is(List.of("gzip")));
    }

    @Test
    void testContentEncodingIdsUseProviderOrder() {
        ContentEncodingContext context = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(gzipEncoder(), Set.of("BR"), true, false, "BR"))
                .addContentEncoding(new TestEncoding(gzipEncoder(), Set.of("gzip"), false, true, "gzip"))
                .addContentEncoding(new TestEncoding(gzipEncoder(), Set.of("GZIP"), true, false, "GZIP"))
                .addContentEncoding(new TestEncoding(gzipEncoder(), Set.of("br"), true, false, "br"))
                .build();

        assertThat(context.contentEncodingIds(), is(List.of("br", "gzip")));
    }

    @Test
    void testContentEncodingIdsUseCanonicalProviderTypeBeforeAliases() {
        ContentEncodingContext context = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(gzipEncoder(), Set.of("gzip", "x-gzip"), true, false, "gzip"))
                .build();

        assertThat(context.contentEncodingIds(), is(List.of("gzip", "x-gzip")));
    }

    @Test
    void testRuntimeEncoderRejectsInvalidQuality() {
        ContentEncodingContext context = context();

        assertThrows(BadRequestException.class, () -> context.encoder(headers("gzip;q=NaN")));
    }

    @Test
    void testRuntimeEncoderTreatsUnknownCodingAsUnavailable() {
        ContentEncodingContext context = context();

        assertThat(context.encoder(headers("zstd")), sameInstance(ContentEncoder.NO_OP));
    }

    @Test
    void testRuntimeEncoderHonorsIdentityRejection() {
        ContentEncoder gzipEncoder = gzipEncoder();
        ContentEncodingContext context = context(gzipEncoder);

        assertThat(context.encoder(headers("identity;q=0, gzip")), sameInstance(gzipEncoder));
    }

    @Test
    void testRuntimeEncoderWildcardUsesFirstEncoder() {
        ContentEncoder gzipEncoder = gzipEncoder();
        ContentEncodingContext context = context(gzipEncoder);

        assertThat(context.encoder(headers("*")), sameInstance(gzipEncoder));
    }

    @Test
    void testRuntimeEncoderUsesHeaderOrderForEqualQualityCodings() {
        ContentEncoder brEncoder = gzipEncoder();
        ContentEncoder gzipEncoder = gzipEncoder();
        ContentEncodingContext context = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(brEncoder, Set.of("br"), true, false, "br"))
                .addContentEncoding(new TestEncoding(gzipEncoder, Set.of("gzip"), true, false, "gzip"))
                .build();

        assertThat(context.encoder(headers("gzip, br")), sameInstance(gzipEncoder));
        assertThat(context.encoder(headers("br, gzip")), sameInstance(brEncoder));
    }

    @Test
    void testRuntimeEncoderWildcardCanUseAliasWhenCanonicalCodingIsRejected() {
        ContentEncodingContext context = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(gzipHeaderEncoder(), Set.of("gzip", "x-gzip"), true, false, "gzip"))
                .build();

        assertContentEncoding(context.encoder(headers("gzip;q=0, *")), "x-gzip");
    }

    @Test
    void testRuntimeEncoderConcreteCodingBeatsEarlierWildcard() {
        ContentEncoder brEncoder = gzipEncoder();
        ContentEncoder gzipEncoder = gzipEncoder();
        ContentEncodingContext context = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(brEncoder, Set.of("br"), true, false, "br"))
                .addContentEncoding(new TestEncoding(gzipEncoder, Set.of("gzip"), true, false, "gzip"))
                .build();

        assertThat(context.encoder(headers("*, gzip, identity;q=0")), sameInstance(gzipEncoder));
    }

    @Test
    void testRuntimeEncoderSupportsExplicitAlias() {
        ContentEncoder gzipEncoder = gzipHeaderEncoder();
        ContentEncodingContext context = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(gzipEncoder, Set.of("gzip", "x-gzip"), true, false, "gzip"))
                .build();

        assertContentEncoding(context.encoder(headers("x-gzip, identity;q=0")), "x-gzip");
    }

    @Test
    void testRuntimeEncoderUsesAcceptedAliasWhenEmittedCodingIsRejected() {
        ContentEncoder gzipEncoder = gzipHeaderEncoder();
        ContentEncodingContext context = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(gzipEncoder, Set.of("gzip", "x-gzip"), true, false, "gzip"))
                .build();

        assertContentEncoding(context.encoder(headers("x-gzip, gzip;q=0, identity;q=0")), "x-gzip");
    }

    @Test
    void testRuntimeEncoderUsesAcceptedAliasQuality() {
        ContentEncoder gzipEncoder = gzipHeaderEncoder();
        ContentEncodingContext context = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(gzipEncoder, Set.of("gzip", "x-gzip"), true, false, "gzip"))
                .build();

        assertContentEncoding(context.encoder(headers("x-gzip, identity;q=0.75, gzip;q=0.5")), "x-gzip");
    }

    @Test
    void testRuntimeEncoderWildcardUsedWhenIdentityRejected() {
        ContentEncoder gzipEncoder = gzipEncoder();
        ContentEncodingContext context = context(gzipEncoder);

        assertThat(context.encoder(headers("identity;q=0, *")), sameInstance(gzipEncoder));
    }

    @Test
    void testRuntimeEncoderFallsBackWhenNoRepresentationAccepted() {
        ContentEncodingContext context = context();

        assertNotAcceptable(context, "identity;q=0");
        assertNotAcceptable(context, "zstd, identity;q=0");
        assertNotAcceptable(context, "gzip;q=0, *;q=0");
    }

    private static ContentEncodingContext context() {
        return context(gzipEncoder());
    }

    private static ContentEncodingContext context(ContentEncoder encoder) {
        return ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding(encoder))
                .build();
    }

    private static ContentEncoder gzipEncoder() {
        return new ContentEncoder() {
            @Override
            public OutputStream apply(OutputStream network) {
                return network;
            }
        };
    }

    private static ContentEncoder gzipHeaderEncoder() {
        return new ContentEncoder() {
            @Override
            public OutputStream apply(OutputStream network) {
                return network;
            }

            @Override
            public void headers(WritableHeaders<?> headers) {
                headers.set(HeaderNames.CONTENT_ENCODING, "gzip");
            }
        };
    }

    private static Headers headers(String acceptEncoding) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ACCEPT_ENCODING, acceptEncoding);
        return headers;
    }

    private static void assertQuality(AcceptEncoding.CodingQuality actual, String coding, double q, boolean wildcard) {
        assertThat(actual.coding(), is(coding));
        assertThat(actual.q(), is(q));
        assertThat(actual.wildcard(), is(wildcard));
    }

    private static void assertContentEncoding(ContentEncoder encoder, String contentEncoding) {
        WritableHeaders<?> headers = WritableHeaders.create();

        encoder.headers(headers);

        assertThat(headers.get(HeaderNames.CONTENT_ENCODING).get(), is(contentEncoding));
    }

    private static void assertInvalidAcceptEncoding(String headerValue) {
        assertThat(AcceptEncoding.create(headers(headerValue)).valid(), is(false));
    }

    private static void assertNotAcceptable(ContentEncodingContext context, String headerValue) {
        HttpException actual = assertThrows(HttpException.class, () -> context.encoder(headers(headerValue)));

        assertThat(actual.status(), is(Status.NOT_ACCEPTABLE_406));
    }

    private record TestEncoding(ContentEncoder encoder,
                                Set<String> ids,
                                boolean supportsEncoding,
                                boolean supportsDecoding,
                                String type) implements ContentEncoding {
        TestEncoding(ContentEncoder encoder) {
            this(encoder, Set.of("gzip"), true, false, "gzip");
        }

        TestEncoding(ContentEncoder encoder, Set<String> ids, boolean supportsEncoding, boolean supportsDecoding) {
            this(encoder, ids, supportsEncoding, supportsDecoding, ids.iterator().next());
        }

        @Override
        public Set<String> ids() {
            return ids;
        }

        @Override
        public boolean supportsEncoding() {
            return supportsEncoding;
        }

        @Override
        public boolean supportsDecoding() {
            return supportsDecoding;
        }

        @Override
        public ContentDecoder decoder() {
            return ContentDecoder.NO_OP;
        }

        @Override
        public ContentEncoder encoder() {
            return encoder;
        }

        @Override
        public String name() {
            return ids.iterator().next();
        }

        @Override
        public String type() {
            return type;
        }
    }
}
