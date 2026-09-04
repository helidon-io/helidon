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

package io.helidon.webserver.testing.junit5.http3;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.common.buffers.LazyString;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3LowLevelClientTest {
    private static final HeaderName TEST_HEADER = HeaderNames.create("x-test");

    @Test
    void decodedResponseHasValueSemantics() {
        Http3LowLevelClient.DecodedResponse first =
                Http3LowLevelClient.DecodedResponse.create(200, headers("first"), 17, new byte[] {1, 2, 3});
        Http3LowLevelClient.DecodedResponse equal =
                Http3LowLevelClient.DecodedResponse.create(200, headers("first"), 17, new byte[] {1, 2, 3});
        Http3LowLevelClient.DecodedResponse differentBody =
                Http3LowLevelClient.DecodedResponse.create(200, headers("first"), 17, new byte[] {1, 2, 4});
        Http3LowLevelClient.DecodedResponse differentHeaders =
                Http3LowLevelClient.DecodedResponse.create(200, headers("second"), 17, new byte[] {1, 2, 3});

        assertThat(first, equalTo(equal));
        assertThat(first.hashCode(), is(equal.hashCode()));
        assertThat(first, not(equalTo(differentBody)));
        assertThat(first, not(equalTo(differentHeaders)));

        Set<Http3LowLevelClient.DecodedResponse> responses = new HashSet<>();
        responses.add(first);
        responses.add(equal);
        responses.add(differentBody);
        assertThat(responses.size(), is(2));

        Map<Http3LowLevelClient.DecodedResponse, String> descriptions = new HashMap<>();
        descriptions.put(first, "response");
        assertThat(descriptions.get(equal), is("response"));
    }

    @Test
    void decodedResponseIsolatedFromInputAndAccessorMutation() {
        byte[] body = new byte[] {1, 2, 3};
        WritableHeaders<?> headers = headers("original");
        Http3LowLevelClient.DecodedResponse response =
                Http3LowLevelClient.DecodedResponse.create(201, headers, 23, body);

        body[0] = 9;
        headers.set(HeaderValues.create(TEST_HEADER, "changed"));
        byte[] returnedBody = response.body();
        returnedBody[1] = 9;
        WritableHeaders<?> returnedHeaders = (WritableHeaders<?>) response.headers();
        returnedHeaders.set(HeaderValues.create(TEST_HEADER, "also-changed"));

        assertThat(response.status(), is(201));
        assertThat(response.headersPayloadLength(), is(23));
        assertThat(response.headers().first(TEST_HEADER).orElseThrow(), is("original"));
        assertThat(response.body(), is(new byte[] {1, 2, 3}));
    }

    @Test
    void decodedResponseMaterializesSourceLazyHeader() {
        Header sourceHeader = lazyHeader("original");
        Http3LowLevelClient.DecodedResponse response =
                Http3LowLevelClient.DecodedResponse.create(200,
                                                           WritableHeaders.create().set(sourceHeader),
                                                           17,
                                                           new byte[0]);
        Http3LowLevelClient.DecodedResponse equal =
                Http3LowLevelClient.DecodedResponse.create(200, headers("original"), 17, new byte[0]);
        int originalHashCode = response.hashCode();

        sourceHeader.allValues().set(0, "source-changed");

        assertThat(response.headers().get(TEST_HEADER).allValues(), is(List.of("original")));
        assertThat(response, is(equal));
        assertThat(response.hashCode(), is(originalHashCode));
    }

    @Test
    void decodedResponseMaterializesAccessorLazyHeader() {
        Http3LowLevelClient.DecodedResponse response =
                Http3LowLevelClient.DecodedResponse.create(200,
                                                           WritableHeaders.create().set(lazyHeader("original")),
                                                           17,
                                                           new byte[0]);
        Http3LowLevelClient.DecodedResponse equal =
                Http3LowLevelClient.DecodedResponse.create(200, headers("original"), 17, new byte[0]);
        int originalHashCode = response.hashCode();

        Header returnedHeader = response.headers().get(TEST_HEADER);
        returnedHeader.allValues().set(0, "accessor-changed");

        assertThat(returnedHeader.allValues().getFirst(), is("accessor-changed"));
        assertThat(response.headers().get(TEST_HEADER).allValues(), is(List.of("original")));
        assertThat(response, is(equal));
        assertThat(response.hashCode(), is(originalHashCode));
    }

    @Test
    void decodedResponseMaterializationPreservesHeaderSemantics() {
        HeaderName name = HeaderNames.create("X-Materialized");
        Header sourceHeader = HeaderValues.create(name, true, true, "first", "second");
        Http3LowLevelClient.DecodedResponse response =
                Http3LowLevelClient.DecodedResponse.create(200,
                                                           WritableHeaders.create().set(sourceHeader),
                                                           17,
                                                           new byte[0]);

        Header materialized = response.headers().get(name);
        assertThat(materialized.headerName(), is(name));
        assertThat(materialized.name(), is("X-Materialized"));
        assertThat(materialized.allValues(), is(List.of("first", "second")));
        assertThat(materialized.changing(), is(true));
        assertThat(materialized.sensitive(), is(true));
    }

    @Test
    void decodedResponseRejectsNullMutableValues() {
        assertThrows(NullPointerException.class,
                     () -> Http3LowLevelClient.DecodedResponse.create(200, null, 0, new byte[0]));
        assertThrows(NullPointerException.class,
                     () -> Http3LowLevelClient.DecodedResponse.create(200, headers("value"), 0, null));
    }

    @Test
    void decodedResponseRenderingIsReadableBoundedAndSafe() {
        Http3LowLevelClient.DecodedResponse response =
                Http3LowLevelClient.DecodedResponse.create(202,
                                                           headers("not-for-logs"),
                                                           19,
                                                           "secret-body".getBytes(StandardCharsets.UTF_8));

        String rendered = response.toString();
        assertThat(rendered, containsString("status=202"));
        assertThat(rendered, containsString("headerCount=1"));
        assertThat(rendered, containsString("headersPayloadLength=19"));
        assertThat(rendered, containsString("bodyLength=11"));
        assertThat(rendered, not(containsString("not-for-logs")));
        assertThat(rendered, not(containsString("secret-body")));
        assertThat(rendered.length(), lessThan(128));
    }

    private static WritableHeaders<?> headers(String value) {
        return WritableHeaders.create().set(HeaderValues.create(TEST_HEADER, value));
    }

    private static Header lazyHeader(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        return HeaderValues.create(TEST_HEADER, new LazyString(bytes, StandardCharsets.US_ASCII));
    }
}
