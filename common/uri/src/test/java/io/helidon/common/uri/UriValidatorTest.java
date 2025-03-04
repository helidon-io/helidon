/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import java.net.URI;

import org.junit.jupiter.api.Test;

import static io.helidon.common.uri.UriValidator.validateFragment;
import static io.helidon.common.uri.UriValidator.validateHost;
import static io.helidon.common.uri.UriValidator.validateIpLiteral;
import static io.helidon.common.uri.UriValidator.validateScheme;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriValidatorTest {
    @Test
    void testSchemeValidation() {
        validateScheme("http");
        validateScheme("https");
        validateScheme("ws");
        validateScheme("abc123+-.");

        assertThrows(NullPointerException.class, () -> UriValidator.validateScheme(null));

        validateBadScheme("čttp",
                          "Scheme contains invalid char (non-ASCII): ?ttp, index: 0, char: 0x10d");
        validateBadScheme("h_ttp",
                          "Scheme contains invalid char: h_ttp, index: 1, char: '_'");
        validateBadScheme("h~ttp",
                          "Scheme contains invalid char: h~ttp, index: 1, char: '~'");
        validateBadScheme("h!ttp",
                          "Scheme contains invalid char: h!ttp, index: 1, char: '!'");
    }

    @Test
    void testFragmentValidation() {
        assertThrows(NullPointerException.class, () -> validateFragment(null));
        validateFragment("");
        validateFragment("fragment");
        validateFragment("frag_ment"); // unreserved
        validateFragment("frag~ment"); // unreserved
        validateFragment("frag=ment"); // sub-delim
        validateFragment("frag=!"); // sub-delim
        validateFragment("frag%61ment"); // pct-encoded
        validateFragment("frag@ment"); // at sign
        validateFragment("frag:ment"); // colon

        validateBadFragment("fragčment",
                            "Fragment contains invalid char (non-ASCII): frag?ment, index: 4, char: 0x10d");
        validateBadFragment("frag%6147%4",
                            "Fragment contains invalid % encoding, not enough chars left at index 9: frag%6147%4");
        // percent encoded: first char is invalid
        validateBadFragment("frag%6147%X1",
                            "Fragment has non hexadecimal char in % encoding: frag%6147%X1, index: 10, char: 'X'");
        validateBadFragment("frag%6147%č1",
                            "Fragment has non hexadecimal char in % encoding: frag%6147%?1, index: 10, char: 0x10d");
        // percent encoded: second char is invalid
        validateBadFragment("frag%6147%1X",
                            "Fragment has non hexadecimal char in % encoding: frag%6147%1X, index: 11, char: 'X'");
        validateBadFragment("frag%6147%1č",
                            "Fragment has non hexadecimal char in % encoding: frag%6147%1?, index: 11, char: 0x10d");
        // character not in allowed sets
        validateBadFragment("frag%6147{",
                            "Fragment contains invalid char: frag%6147?, index: 9, char: 0x7b");
        validateBadFragment("frag%6147\t",
                            "Fragment contains invalid char: frag%6147?, index: 9, char: 0x09");
    }

    @Test
    void testQueryValidation() {
        assertThrows(NullPointerException.class, () -> UriQuery.create((String) null));
        assertThrows(NullPointerException.class, () -> UriQuery.create((URI) null));
        assertThrows(NullPointerException.class, () -> UriQuery.create(null, true));
        assertThrows(NullPointerException.class, () -> UriValidator.validateQuery(null));

        UriQuery.create("", true);
        UriValidator.validateQuery("");
        UriQuery.create("a=b&c=d&a=e", true);
        // validate all rules
        // must be an ASCII (lower than 255)
        validateBadQuery("a=@/?%6147č",
                         "Query contains invalid char (non-ASCII): a=@/?%6147?, index: 10, char: 0x10d");
        // percent encoded:  must be full percent encoding
        validateBadQuery("a=@/?%6147%4",
                         "Query contains invalid % encoding, not enough chars left at index 10: a=@/?%6147%4");
        // percent encoded: first char is invalid
        validateBadQuery("a=@/?%6147%X1",
                         "Query has non hexadecimal char in % encoding: a=@/?%6147%X1, index: 11, char: 'X'");
        validateBadQuery("a=@/?%6147%č1",
                         "Query has non hexadecimal char in % encoding: a=@/?%6147%?1, index: 11, char: 0x10d");
        // percent encoded: second char is invalid
        validateBadQuery("a=@/?%6147%1X",
                         "Query has non hexadecimal char in % encoding: a=@/?%6147%1X, index: 12, char: 'X'");
        validateBadQuery("a=@/?%6147%1č",
                         "Query has non hexadecimal char in % encoding: a=@/?%6147%1?, index: 12, char: 0x10d");
        // character not in allowed sets
        validateBadQuery("a=@/?%6147{",
                         "Query contains invalid char: a=@/?%6147?, index: 10, char: 0x7b");
        validateBadQuery("a=@/?%6147\t",
                         "Query contains invalid char: a=@/?%6147?, index: 10, char: 0x09");
    }

    @Test
    void testAllCharsInQuery() {
        UriQuery.create("aA9-._~", true);
        UriQuery.create("%20%AB", true);
        UriQuery.create("!$&'()*+,;=", true);
        UriQuery.create(":@", true);
        UriQuery.create("/?", true);
    }

    @Test
    void testGoodHostname() {
        // sanity
        validateHost("localhost");
        // host names
        validateHost("www.example.com");
        // percent encoded
        validateHost("%65%78%61%6D%70%6C%65");
        validateHost("%65%78%61%6D%70%6C%65.com");
        // with underscores
        validateHost("www.exa_mple.com");
        // with sub-delims
        validateHost("www.exa$mple.com");
    }

    @Test
    void testGoodIp4() {
        // IPv4
        validateHost("192.167.1.1");
    }

    @Test
    void testGoodIpLiteral6() {
        // IPv6
        validateHost("[2001:0db8:0001:0000:0000:0ab9:C0A8:0102]");
        validateHost("[::1]");
        validateHost("[2001:db8:3333:4444:5555:6666:7777:8888]");
        validateHost("[2001:db8:3333:4444:CCCC:DDDD:EEEE:FFFF]");
        validateHost("[::]");
        validateHost("[2001:db8::]");
        validateHost("[::1234:5678]");
        validateHost("[::1234:5678:1]");
        validateHost("[2001:db8::1234:5678]");
        validateHost("[2001:db8:1::ab9:C0A8:102]");
    }

    @Test
    void testGoodIpLiteral6Dual() {
        // IPv6
        validateHost("[2001:db8:3333:4444:5555:6666:1.2.3.4]");
        validateHost("[::11.22.33.44]");
        validateHost("[2001:db8::123.123.123.123]");
        validateHost("[::1234:5678:91.123.4.56]");
        validateHost("[::1234:5678:1.2.3.4]");
        validateHost("[2001:db8::1234:5678:5.6.7.8]");
    }

    @Test
    void testGoodIpLiteralFuture() {
        // IPvFuture
        validateHost("[v9.abc:def]");
        validateHost("[v9.abc:def*]");
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

    private static void validateBadQuery(String query, String expected) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> UriQuery.create(query, true));
        assertThat(exception.getMessage(), is(expected));
    }

    private static void validateBadScheme(String scheme, String expected) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> validateScheme(scheme));
        assertThat(exception.getMessage(), is(expected));
    }

    private static void validateBadFragment(String fragment, String expected) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> validateFragment(fragment));
        assertThat(exception.getMessage(), is(expected));
    }

    private static void invokeExpectFailure(String message, String host) {
        var t = assertThrows(IllegalArgumentException.class, () -> validateHost(host), "Testing host: " + host);
        assertThat(t.getMessage(), is(message));
    }

    private static void invokeLiteralExpectFailure(String message, String host) {
        var t = assertThrows(IllegalArgumentException.class, () -> validateIpLiteral(host), "Testing host: " + host);
        assertThat(t.getMessage(), is(message));
    }
}