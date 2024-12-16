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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("removal")
class HostValidatorTest {
    @Test
    void testGoodHostname() {
        // sanity
        io.helidon.http.HostValidator.validate("localhost");
        // host names
        io.helidon.http.HostValidator.validate("www.example.com");
        // percent encoded
        io.helidon.http.HostValidator.validate("%65%78%61%6D%70%6C%65");
        io.helidon.http.HostValidator.validate("%65%78%61%6D%70%6C%65.com");
        // with underscores
        io.helidon.http.HostValidator.validate("www.exa_mple.com");
        // with sub-delims
        io.helidon.http.HostValidator.validate("www.exa$mple.com");
    }

    @Test
    void testGoodIp4() {
        // IPv4
        io.helidon.http.HostValidator.validate("192.167.1.1");
    }

    @Test
    void testGoodIpLiteral6() {
        // IPv6
        io.helidon.http.HostValidator.validate("[2001:0db8:0001:0000:0000:0ab9:C0A8:0102]");
        io.helidon.http.HostValidator.validate("[::1]");
        io.helidon.http.HostValidator.validate("[2001:db8:3333:4444:5555:6666:7777:8888]");
        io.helidon.http.HostValidator.validate("[2001:db8:3333:4444:CCCC:DDDD:EEEE:FFFF]");
        io.helidon.http.HostValidator.validate("[::]");
        io.helidon.http.HostValidator.validate("[2001:db8::]");
        io.helidon.http.HostValidator.validate("[::1234:5678]");
        io.helidon.http.HostValidator.validate("[::1234:5678:1]");
        io.helidon.http.HostValidator.validate("[2001:db8::1234:5678]");
        io.helidon.http.HostValidator.validate("[2001:db8:1::ab9:C0A8:102]");
    }

    @Test
    void testGoodIpLiteral6Dual() {
        // IPv6
        io.helidon.http.HostValidator.validate("[2001:db8:3333:4444:5555:6666:1.2.3.4]");
        io.helidon.http.HostValidator.validate("[::11.22.33.44]");
        io.helidon.http.HostValidator.validate("[2001:db8::123.123.123.123]");
        io.helidon.http.HostValidator.validate("[::1234:5678:91.123.4.56]");
        io.helidon.http.HostValidator.validate("[::1234:5678:1.2.3.4]");
        io.helidon.http.HostValidator.validate("[2001:db8::1234:5678:5.6.7.8]");
    }

    @Test
    void testGoodIpLiteralFuture() {
        // IPvFuture
        io.helidon.http.HostValidator.validate("[v9.abc:def]");
        io.helidon.http.HostValidator.validate("[v9.abc:def*]");
    }

    @Test
    void testBadHosts() {
        // just empty
        invokeExpectFailure("Host cannot be blank", "");
        // invalid brackets
        invokeExpectFailure("Host contains invalid char: [start.but.not.end, index: 0, char: '['",
                            "[start.but.not.end");
        invokeExpectFailure("Host contains invalid char: end.but.not.start], index: 17, char: ']'",
                            "end.but.not.start]");
        invokeExpectFailure("Host contains invalid char: int.the[.middle], index: 7, char: '['",
                            "int.the[.middle]");
        // invalid escape
        invokeExpectFailure("Host has non hexadecimal char in % encoding: www.%ZAxample.com, index: 5, char: 'Z'",
                            "www.%ZAxample.com");
        invokeExpectFailure("Host has non hexadecimal char in % encoding: www.%AZxample.com, index: 6, char: 'Z'",
                            "www.%AZxample.com");
        // invalid character (non-ASCII
        invokeExpectFailure("Host contains invalid char (non-ASCII): www.?example.com, index: 4, char: 0x10d",
                            "www.čexample.com");
        // wrong trailing escape (must be two chars);
        invokeExpectFailure("Host contains invalid % encoding, not enough chars left at index 15: www.example.com%4",
                            "www.example.com%4");
        invokeExpectFailure("Host has non hexadecimal char in % encoding: www.example.com%?4, index: 16, char: 0x10d",
                            "www.example.com%č4");
        invokeExpectFailure("Host has non hexadecimal char in % encoding: www.example.com%4?, index: 17, char: 0x10d",
                            "www.example.com%4č");
    }

    @Test
    void testBadLiteral6() {
        // IPv6
        // empty segment
        invokeExpectFailure("Host IPv6 contains more than one skipped segment: [2001:db8::85a3::7334]",
                            "[2001:db8::85a3::7334]");
        // wrong segment (G is not a hexadecimal number)
        invokeExpectFailure("IPv6 segment has non hexadecimal char: GGGG, index: 0, char: 'G'. "
                                    + "Value: [GGGG:FFFF:0000:0000:0000:0000:0000:0000]",
                            "[GGGG:FFFF:0000:0000:0000:0000:0000:0000]");
        // non-ASCII character
        invokeExpectFailure("IPv6 segment has non hexadecimal char: ?, index: 0, char: 0x10d. "
                                    + "Value: [?:FFFF:0000:0000:0000:0000:0000:0000]",
                            "[č:FFFF:0000:0000:0000:0000:0000:0000]");
        // wrong segment (too many characters)
        invokeExpectFailure("IPv6 segment has more than 4 chars: aaaaa. "
                                    + "Value: [aaaaa:FFFF:0000:0000:0000:0000:0000:0000]",
                            "[aaaaa:FFFF:0000:0000:0000:0000:0000:0000]");
        // empty segment
        invokeExpectFailure("IPv6 segment is empty: [aaaa:FFFF:0000:0000:0000:0000:0000:]",
                            "[aaaa:FFFF:0000:0000:0000:0000:0000:]");
        // wrong number of segments
        invokeExpectFailure("Host IPv6 address contains too many segments: "
                                    + "[0000:0000:0000:0000:0000:0000:0000:0000:0000:0000]",
                            "[0000:0000:0000:0000:0000:0000:0000:0000:0000:0000]");
        // missing everything
        invokeExpectFailure("Host cannot be blank. Value: []",
                            "[]");
        // wrong start (leading colon)
        invokeExpectFailure("Host IPv6 contains excessive colon: :1:0::. Value: [:1:0::]",
                            "[:1:0::]");
        // wrong end, colon instead of value
        invokeExpectFailure("IPv6 segment has non hexadecimal char: :, index: 0, char: ':'. Value: [1:0:::]",
                            "[1:0:::]");

        invokeLiteralExpectFailure("Invalid IP literal, missing square bracket(s): [::, index: 2, char: ':'",
                                   "[::");
        invokeLiteralExpectFailure("Invalid IP literal, missing square bracket(s): ::], index: 0, char: ':'",
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
        invokeExpectFailure("Version cannot be blank. Value: [v.abc:def]",
                            "[v.abc:def]");
        // missing address
        invokeExpectFailure("IP Future must contain 'v<version>.': [v2]",
                            "[v2]");
        invokeExpectFailure("IP Future cannot be blank. Value: [v2.]",
                            "[v2.]");
        // invalid character in the host (valid future)
        invokeExpectFailure("Host contains invalid char: [v2./0:::], index: 3, char: '/'",
                            "[v2./0:::]");
        invokeExpectFailure("Host contains invalid char (non-ASCII): 0:?, index: 2, char: 0x10d",
                            "[v2.0:č]");
    }

    private static void invokeExpectFailure(String message, String host) {
        var t = assertThrows(IllegalArgumentException.class,
                             () -> io.helidon.http.HostValidator.validate(host),
                             "Testing host: " + host);
        assertThat(t.getMessage(), is(message));
    }

    private static void invokeLiteralExpectFailure(String message, String host) {
        var t = assertThrows(IllegalArgumentException.class,
                             () -> io.helidon.http.HostValidator.validateIpLiteral(host),
                             "Testing host: " + host);
        assertThat(t.getMessage(), is(message));
    }
}