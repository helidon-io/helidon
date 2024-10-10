/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1;

import java.util.Arrays;

import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static io.helidon.webserver.http1.Http1Connection.validateHostHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ValidateHostHeaderTest {
    private static final HttpPrologue TEST_PROLOGUE =
            HttpPrologue.create("http", "http", "1.1", Method.GET, "/", false);

    @Test
    void testNone() {
        invokeExpectFailure("Host header must be present in the request");
    }

    @Test
    void testMany() {
        invokeExpectFailure("Only a single Host header is allowed in request", "first", "second");
    }

    @Test
    void testGoodHostname() {
        // sanity
        invoke("localhost");
        invoke("localhost:8080");
        // host names
        invoke("www.example.com:445");
        // percent encoded
        invoke("%65%78%61%6D%70%6C%65:8080");
        invoke("%65%78%61%6D%70%6C%65.com:8080");
        // with underscores
        invoke("www.exa_mple.com:8080");
    }

    @Test
    void testGoodIp4() {
        // IPv4
        invoke("192.167.1.1");
        invoke("192.167.1.1:8080");
    }

    @Test
    void testGoodIpLiteral6() {
        // IPv6
        invoke("[2001:0db8:0001:0000:0000:0ab9:C0A8:0102]");
        invoke("[2001:0db8:0001:0000:0000:0ab9:C0A8:0102]:8080");
        invoke("[::1]");
        invoke("[::1]:8080");
    }

    @Test
    void testGoodIpLiteral6Dual() {
        // IPv6
        invoke("[2001:db8:3333:4444:5555:6666:1.2.3.4]:8080");
        invoke("[::11.22.33.44]");
    }

    @Test
    void testGoodIpLiteralFuture() {
        // IPvFuture
        invoke("[v9.abc:def]");
        invoke("[v9.abc:def]:8080");
    }

    @Test
    void testBadPort() {
        // unparsable port
        invokeExpectFailure("Invalid port of the host header: 80a", "192.167.1.1:80a");
        invokeExpectFailure("Invalid port of the host header: 80_80", "localhost:80_80");
    }

    @Test
    void testBadPortSimpleValidation() {
        // these must fail even when validation is disabled
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderNames.HOST, "192.167.1.1:80a");
        var t = assertThrows(RequestException.class, () -> validateHostHeader(TEST_PROLOGUE, headers, false));
        assertThat(t.getMessage(), is("Invalid port of the host header: 80a"));

        headers.set(HeaderNames.HOST, "192.167.1.1:80_80");
        t = assertThrows(RequestException.class, () -> validateHostHeader(TEST_PROLOGUE, headers, false));
        assertThat(t.getMessage(), is("Invalid port of the host header: 80_80"));
    }


    @Test
    void testBadHosts() {
        // just empty
        invokeExpectFailure("Host header must not be empty", "");
        invokeExpectFailure("Invalid Host header: Host contains invalid character: int.the[.middle]",
                            "int.the[.middle]:8080");
    }

    @Test
    void testBadLiteral6() {
        // IPv6
        // empty segment
        invokeExpectFailure("Invalid Host header: Host IPv6 contains more than one skipped segment: [2001:db8::85a3::7334]",
                            "[2001:db8::85a3::7334]");
    }

    @Test
    void testBadLiteralFuture() {
        // IPv future
        // version must be present
        invokeExpectFailure("Invalid Host header: Version cannot be blank: [v.abc:def]",
                            "[v.abc:def]");
        // missing address
    }

    private static void invokeExpectFailure(String message, String... hosts) {
        var t = assertThrows(RequestException.class, () -> invoke(hosts), "Testing hosts: " + Arrays.toString(hosts));
        assertThat(t.getMessage(), is(message));
    }

    private static void invoke(String... values) {
        WritableHeaders<?> headers = WritableHeaders.create();
        if (values.length > 0) {
            headers.set(HeaderNames.HOST, values);
        }
        validateHostHeader(TEST_PROLOGUE, headers, true);
    }
}
