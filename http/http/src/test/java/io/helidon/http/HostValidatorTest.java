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

package io.helidon.http;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HostValidator.validate;
import static io.helidon.http.HostValidator.validateIpLiteral;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostValidatorTest {
    @Test
    void testGoodHostname() {
        // sanity
        validate("localhost");
        // host names
        validate("www.example.com");
        // percent encoded
        validate("%65%78%61%6D%70%6C%65");
        validate("%65%78%61%6D%70%6C%65.com");
        // with underscores
        validate("www.exa_mple.com");
        // with sub-delims
        validate("www.exa$mple.com");
    }

    @Test
    void testGoodIp4() {
        // IPv4
        validate("192.167.1.1");
    }

    @Test
    void testGoodIpLiteral6() {
        // IPv6
        validate("[2001:0db8:0001:0000:0000:0ab9:C0A8:0102]");
        validate("[::1]");
        validate("[2001:db8:3333:4444:5555:6666:7777:8888]");
        validate("[2001:db8:3333:4444:CCCC:DDDD:EEEE:FFFF]");
        validate("[::]");
        validate("[2001:db8::]");
        validate("[::1234:5678]");
        validate("[::1234:5678:1]");
        validate("[2001:db8::1234:5678]");
        validate("[2001:db8:1::ab9:C0A8:102]");
    }

    @Test
    void testGoodIpLiteral6Dual() {
        // IPv6
        validate("[2001:db8:3333:4444:5555:6666:1.2.3.4]");
        validate("[::11.22.33.44]");
        validate("[2001:db8::123.123.123.123]");
        validate("[::1234:5678:91.123.4.56]");
        validate("[::1234:5678:1.2.3.4]");
        validate("[2001:db8::1234:5678:5.6.7.8]");
    }

    @Test
    void testGoodIpLiteralFuture() {
        // IPvFuture
        validate("[v9.abc:def]");
        validate("[v9.abc:def*]");
    }

    @Test
    void testBadHosts() {
        // just empty
        invokeExpectFailure("Host cannot be blank: ", "");
        // invalid brackets
        invokeExpectFailure("Host contains invalid character: [start.but.not.end",
                            "[start.but.not.end");
        invokeExpectFailure("Host contains invalid character: end.but.not.start]",
                            "end.but.not.start]");
        invokeExpectFailure("Host contains invalid character: int.the[.middle]",
                            "int.the[.middle]");
        // invalid escape
        invokeExpectFailure("Host contains non-hexadecimal character in % encoding: www.%ZAxample.com",
                            "www.%ZAxample.com");
        invokeExpectFailure("Host contains non-hexadecimal character in % encoding: www.%AZxample.com",
                            "www.%AZxample.com");
        // invalid character (non-ASCII
        invokeExpectFailure("Host contains invalid character: www.čexample.com",
                            "www.čexample.com");
        // wrong trailing escape (must be two chars);
        invokeExpectFailure("Host contains invalid % encoding: www.example.com%4",
                            "www.example.com%4");
        invokeExpectFailure("Host contains invalid character in % encoding: www.example.com%č4",
                            "www.example.com%č4");
        invokeExpectFailure("Host contains invalid character in % encoding: www.example.com%4č",
                            "www.example.com%4č");
    }

    @Test
    void testBadLiteral6() {
        // IPv6
        // empty segment
        invokeExpectFailure("Host IPv6 contains more than one skipped segment: [2001:db8::85a3::7334]",
                            "[2001:db8::85a3::7334]");
        // wrong segment (G is not a hexadecimal number)
        invokeExpectFailure("IPv6 segment non hexadecimal character: "
                                    + "[GGGG:FFFF:0000:0000:0000:0000:0000:0000]",
                            "[GGGG:FFFF:0000:0000:0000:0000:0000:0000]");
        // non-ASCII character
        invokeExpectFailure("IPv6 segment non hexadecimal character: "
                                    + "[č:FFFF:0000:0000:0000:0000:0000:0000]",
                            "[č:FFFF:0000:0000:0000:0000:0000:0000]");
        // wrong segment (too many characters)
        invokeExpectFailure("IPv6 segment has more than 4 characters: [aaaaa:FFFF:0000:0000:0000:0000:0000:0000]",
                            "[aaaaa:FFFF:0000:0000:0000:0000:0000:0000]");
        // empty segment
        invokeExpectFailure("IPv6 segment is empty: [aaaa:FFFF:0000:0000:0000:0000:0000:]",
                            "[aaaa:FFFF:0000:0000:0000:0000:0000:]");
        // wrong number of segments
        invokeExpectFailure("Host IPv6 address contains too many segments: "
                                    + "[0000:0000:0000:0000:0000:0000:0000:0000:0000:0000]",
                            "[0000:0000:0000:0000:0000:0000:0000:0000:0000:0000]");
        // missing everything
        invokeExpectFailure("Host cannot be blank: []",
                            "[]");
        // wrong start (leading colon)
        invokeExpectFailure("Host IPv6 contains excessive colon: [:1:0::]",
                            "[:1:0::]");
        // wrong end, colon instead of value
        invokeExpectFailure("IPv6 segment non hexadecimal character: [1:0:::]",
                            "[1:0:::]");

        invokeLiteralExpectFailure("Invalid IP literal, missing square bracket(s): [::",
                                   "[::");
        invokeLiteralExpectFailure("Invalid IP literal, missing square bracket(s): ::]",
                                   "::]");
    }

    @Test
    void testBadLiteralDual() {
        invokeLiteralExpectFailure("Host IPv6 dual address contains invalid IPv4 address: [::14.266.44.74]",
                                   "[::14.266.44.74]");
        invokeLiteralExpectFailure("Host IPv6 dual address contains invalid IPv4 address: [::14.266.44]",
                                   "[::14.266.44]");
        invokeLiteralExpectFailure("Host IPv6 dual address contains invalid IPv4 address: [::14.123.-44.147]",
                                   "[::14.123.-44.147]");
    }

    @Test
    void testBadLiteralFuture() {
        // IPv future
        // version must be present
        invokeExpectFailure("Version cannot be blank: [v.abc:def]",
                            "[v.abc:def]");
        // missing address
        invokeExpectFailure("IP Future must contain 'v<version>.': [v2]",
                            "[v2]");
        invokeExpectFailure("IP Future cannot be blank: [v2.]",
                            "[v2.]");
        // invalid character in the host (valid future)
        invokeExpectFailure("Host contains invalid character: [v2./0:::]",
                            "[v2./0:::]");
        invokeExpectFailure("Host contains invalid character: [v2.0:č]",
                            "[v2.0:č]");
    }

    private static void invokeExpectFailure(String message, String host) {
        var t = assertThrows(IllegalArgumentException.class, () -> validate(host), "Testing host: " + host);
        assertThat(t.getMessage(), is(message));
    }

    private static void invokeLiteralExpectFailure(String message, String host) {
        var t = assertThrows(IllegalArgumentException.class, () -> validateIpLiteral(host), "Testing host: " + host);
        assertThat(t.getMessage(), is(message));
    }
}