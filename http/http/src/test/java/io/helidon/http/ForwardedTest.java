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
package io.helidon.http;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForwardedTest {
    @Test
    void testForOnly() {
        String header = "for=\"_mdn\"";
        Forwarded forwarded = Forwarded.create(header);

        assertThat(forwarded.by(), is(optionalEmpty()));
        assertThat(forwarded.host(), is(optionalEmpty()));
        assertThat(forwarded.proto(), is(optionalEmpty()));
        assertThat(forwarded.forClient(), optionalValue(is("_mdn")));
    }

    @Test
    void testForCaseInsensitive() {
        String header = "For=\"[2001:db8:cafe::17]:4711\"";
        Forwarded forwarded = Forwarded.create(header);

        assertThat(forwarded.by(), is(optionalEmpty()));
        assertThat(forwarded.host(), is(optionalEmpty()));
        assertThat(forwarded.proto(), is(optionalEmpty()));
        assertThat(forwarded.forClient(), optionalValue(is("[2001:db8:cafe::17]:4711")));
    }

    @Test
    void testForProtoAndBy() {
        String header = "for=192.0.2.60;proto=http;by=203.0.113.43";
        Forwarded forwarded = Forwarded.create(header);

        assertThat(forwarded.by(), optionalValue(is("203.0.113.43")));
        assertThat(forwarded.host(), is(optionalEmpty()));
        assertThat(forwarded.proto(), optionalValue(is("http")));
        assertThat(forwarded.forClient(), optionalValue(is("192.0.2.60")));
    }

    @Test
    void testAll() {
        String header = "for=192.0.2.60;proto=http;by=203.0.113.43;Host=10.10.10.10";
        Forwarded forwarded = Forwarded.create(header);

        assertThat(forwarded.by(), optionalValue(is("203.0.113.43")));
        assertThat(forwarded.host(), optionalValue(is("10.10.10.10")));
        assertThat(forwarded.proto(), optionalValue(is("http")));
        assertThat(forwarded.forClient(), optionalValue(is("192.0.2.60")));
    }

    @Test
    void testEmptyPairsAreIgnored() {
        Forwarded forwarded = Forwarded.create(";for=192.0.2.60;;proto=https");

        assertThat(forwarded.by(), is(optionalEmpty()));
        assertThat(forwarded.host(), is(optionalEmpty()));
        assertThat(forwarded.proto(), optionalValue(is("https")));
        assertThat(forwarded.forClient(), optionalValue(is("192.0.2.60")));
    }

    @Test
    void testMultiValuesCommaSeparated() {
        HeadersImpl<?> headers = new HeadersImpl<>();
        headers.add(HeaderNames.FORWARDED, "for=192.0.2.60;proto=http;by=203.0.113.43;Host=10.10.10.10,by=\"192.0.2.60\"");
        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(2));
        Forwarded forwarded = forwardedList.get(0);

        assertThat(forwarded.by(), optionalValue(is("203.0.113.43")));
        assertThat(forwarded.host(), optionalValue(is("10.10.10.10")));
        assertThat(forwarded.proto(), optionalValue(is("http")));
        assertThat(forwarded.forClient(), optionalValue(is("192.0.2.60")));

        forwarded = forwardedList.get(1);
        assertThat(forwarded.by(), optionalValue(is("192.0.2.60")));
        assertThat(forwarded.host(), is(optionalEmpty()));
        assertThat(forwarded.proto(), is(optionalEmpty()));
        assertThat(forwarded.forClient(), is(optionalEmpty()));
    }

    @Test
    void testEmptyListElementsAreIgnored() {
        HeadersImpl<?> headers = new HeadersImpl<>();
        headers.add(HeaderNames.FORWARDED, ",for=192.0.2.60,,for=198.51.100.17,");

        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(2));
        assertThat(forwardedList.get(0).forClient(), optionalValue(is("192.0.2.60")));
        assertThat(forwardedList.get(1).forClient(), optionalValue(is("198.51.100.17")));
    }

    @Test
    void testQuotedExtensionDelimiters() {
        HeadersImpl<?> headers = new HeadersImpl<>();
        headers.add(HeaderNames.FORWARDED, "for=client.example;proto=https;ext=\"a,b;c\"");

        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(1));
        assertThat(forwardedList.getFirst().forClient(), optionalValue(is("client.example")));
        assertThat(forwardedList.getFirst().proto(), optionalValue(is("https")));
    }

    @Test
    void testEscapedQuoteDoesNotExposeDelimiters() {
        HeadersImpl<?> headers = new HeadersImpl<>();
        headers.add(HeaderNames.FORWARDED, "for=client.example;ext=\"a\\\",b;c\";proto=https");

        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(1));
        assertThat(forwardedList.getFirst().forClient(), optionalValue(is("client.example")));
        assertThat(forwardedList.getFirst().proto(), optionalValue(is("https")));
    }

    @Test
    void testQuotedPairIsUnescaped() {
        Forwarded forwarded = Forwarded.create(
                "for=client.example;host=\"public.example\\:443\";proto=https");

        assertThat(forwarded.host(), optionalValue(is("public.example:443")));
    }

    @Test
    void testUnterminatedQuotedExtensionIsRejected() {
        HeadersImpl<?> headers = new HeadersImpl<>();
        headers.add(HeaderNames.FORWARDED, "for=client.example;proto=https;ext=\"unterminated");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> Forwarded.create(headers));

        assertThat(exception.getMessage(), is("Invalid Forwarded header"));
    }

    @Test
    void testDanglingQuotedPairIsRejected() {
        HeadersImpl<?> headers = new HeadersImpl<>();
        headers.add(HeaderNames.FORWARDED, "for=client.example;proto=https;ext=\"dangling\\");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> Forwarded.create(headers));

        assertThat(exception.getMessage(), is("Invalid Forwarded header"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "public.example:not-a-port",
            "public.example:-1",
            "public.example:+1",
            "[2001:db8::1]:not-a-port"
    })
    void testInvalidHostPortIsRejected(String host) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> Forwarded.create(
                                                                  "for=client.example;host=\"" + host + "\";proto=https"));

        assertThat(exception.getMessage(), is("Invalid Forwarded header"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"public.example:", "public.example:2147483648"})
    void testSyntacticallyValidHostPortsAreAccepted(String host) {
        Forwarded forwarded = Forwarded.create("for=client.example;host=\"" + host + "\";proto=https");

        assertThat(forwarded.host(), optionalValue(is(host)));
    }

    @Test
    void testMultiValues() {
        HeadersImpl<?> headers = new HeadersImpl<>();
        headers.add(HeaderNames.FORWARDED, "for=192.0.2.60;proto=http;by=203.0.113.43;Host=10.10.10.10",
                    "by=\"192.0.2.60\"");
        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(2));
        Forwarded forwarded = forwardedList.get(0);

        assertThat(forwarded.by(), optionalValue(is("203.0.113.43")));
        assertThat(forwarded.host(), optionalValue(is("10.10.10.10")));
        assertThat(forwarded.proto(), optionalValue(is("http")));
        assertThat(forwarded.forClient(), optionalValue(is("192.0.2.60")));

        forwarded = forwardedList.get(1);
        assertThat(forwarded.by(), optionalValue(is("192.0.2.60")));
        assertThat(forwarded.host(), is(optionalEmpty()));
        assertThat(forwarded.proto(), is(optionalEmpty()));
        assertThat(forwarded.forClient(), is(optionalEmpty()));
    }

    @Test
    void testMultiValuesAndCommaSeparated() {
        HeadersImpl<?> headers = new HeadersImpl<>();
        headers.add(HeaderNames.FORWARDED, "for=192.0.2.60;proto=http;by=203.0.113.43;Host=10.10.10.10",
                    "by=\"192.0.2.60\",for=\"14.22.11.22\"");
        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(3));
        Forwarded forwarded = forwardedList.get(0);

        assertThat(forwarded.by(), optionalValue(is("203.0.113.43")));
        assertThat(forwarded.host(), optionalValue(is("10.10.10.10")));
        assertThat(forwarded.proto(), optionalValue(is("http")));
        assertThat(forwarded.forClient(), optionalValue(is("192.0.2.60")));

        forwarded = forwardedList.get(1);
        assertThat(forwarded.by(), optionalValue(is("192.0.2.60")));
        assertThat(forwarded.host(), is(optionalEmpty()));
        assertThat(forwarded.proto(), is(optionalEmpty()));
        assertThat(forwarded.forClient(), is(optionalEmpty()));

        forwarded = forwardedList.get(2);
        assertThat(forwarded.by(), is(optionalEmpty()));
        assertThat(forwarded.host(), is(optionalEmpty()));
        assertThat(forwarded.proto(), is(optionalEmpty()));
        assertThat(forwarded.forClient(), optionalValue(is("14.22.11.22")));
    }
}
