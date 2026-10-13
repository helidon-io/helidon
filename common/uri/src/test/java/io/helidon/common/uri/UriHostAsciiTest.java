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

package io.helidon.common.uri;

import java.net.IDN;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriHostAsciiTest {
    @ParameterizedTest
    @ValueSource(strings = {"example.com", "ExAmPlE.CoM", "ExAmPlE.CoM.", "XN--BCHER-KVA.example",
                            "xn--not-valid-punycode", "a--B.1", "123", "1.2.3", "127.000.0.001", "256.1.2.3"})
    void asciiDnsMatchesIdnOracle(String input) {
        assertDnsMatchesIdn(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BÜCHER.example", "a\u3002b", "a\uff0eb", "a\uff61b", "ＡＢ.example", "faß.de", "a\u00adb"})
    void unicodeFallbackMatchesIdnOracle(String input) {
        assertDnsMatchesIdn(input);
    }

    @Test
    void eligibleLabelsMatchIdnOracle() {
        String alphabet = "aZ09-";
        int combinations = 1;
        for (int length = 1; length <= 4; length++) {
            combinations *= alphabet.length();
            for (int index = 0; index < combinations; index++) {
                int remaining = index;
                var label = new StringBuilder();
                for (int digit = 0; digit < length; digit++) {
                    label.append(alphabet.charAt(remaining % alphabet.length()));
                    remaining /= alphabet.length();
                }
                if (label.charAt(0) != '-' && label.charAt(length - 1) != '-') {
                    assertDnsMatchesIdn(label.toString());
                    assertDnsMatchesIdn(label + ".Example");
                }
            }
        }
    }

    @Test
    void labelAndTotalLengthBoundariesArePreserved() {
        assertDnsMatchesIdn("a".repeat(63));
        String longest = "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertDnsMatchesIdn(longest);
        assertDnsMatchesIdn(longest + ".");
        assertIdnFailure("a".repeat(64));
        var failure = assertThrows(IllegalArgumentException.class, () -> UriHost.create(longest + "d"));
        assertThat(failure.getMessage(), is("Host DNS name is too long"));
        failure = assertThrows(IllegalArgumentException.class, () -> UriHost.create(longest + "d."));
        assertThat(failure.getMessage(), is("Host DNS name is too long"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a..b", ".a", "-a", "a-", "a.-b", "a.b-", "a_b", "a%b", "a%2Eb", "a b",
                            "a\u0000b", "a\u001fb", "a\u007fb", "a/b", "a\\b", "a?b", "a#b", "\ud800", "\udc00",
                            "a\ud800b"})
    void invalidFallbackPreservesIdnFailure(String input) {
        assertIdnFailure(input);
    }

    @Test
    void unicodeTerminalDotAndBlankRetainLaterValidation() {
        var unicodeDot = assertThrows(IllegalArgumentException.class, () -> UriHost.create("a\u3002"));
        assertThat(unicodeDot.getMessage(), is("Host DNS name contains an empty label"));
        var repeatedDot = assertThrows(IllegalArgumentException.class, () -> UriHost.create("a.."));
        assertThat(repeatedDot.getMessage(), is("Host DNS name contains an empty label"));
        for (String input : new String[] {"", " ", "."}) {
            assertThrows(IllegalArgumentException.class, () -> UriHost.create(input), input);
        }
        assertThrows(NullPointerException.class, () -> UriHost.create(null));
    }

    @Test
    void classificationStillPrecedesDnsNormalization() {
        assertThat(UriHost.create("127.0.0.1").kind(), is(UriHost.Kind.IPV4));
        assertThat(UriHost.create("127.0.0.1.").kind(), is(UriHost.Kind.DNS));
        assertThat(UriHost.create("127.000.0.001.").kind(), is(UriHost.Kind.DNS));
        assertThat(UriHost.create("2001:DB8::1").value(), is("2001:db8::1"));
        assertThat(UriHost.create("2001:DB8::1").kind(), is(UriHost.Kind.IPV6));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("fe80::1%eth0"));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("fe80::1%25eth0"));
    }

    private static void assertDnsMatchesIdn(String input) {
        String normalized = input.endsWith(".") ? input.substring(0, input.length() - 1) : input;
        String expected = IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        UriHost actual = UriHost.create(input);
        assertThat(input, actual.kind(), is(UriHost.Kind.DNS));
        assertThat(input, actual.value(), is(expected));
        assertThat(input, actual, is(UriHost.create(expected)));
        assertThat(input, actual.hashCode(), is(UriHost.create(expected).hashCode()));
        assertThat(input, UriHost.create(input), not(sameInstance(actual)));
    }

    private static void assertIdnFailure(String input) {
        var expected = assertThrows(IllegalArgumentException.class, () -> IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES), input);
        var actual = assertThrows(IllegalArgumentException.class, () -> UriHost.create(input), input);
        assertThat(input, actual.getMessage(), is("Host is not a valid DNS name"));
        assertThat(input, actual.getCause().getClass(), equalTo(expected.getClass()));
        assertThat(input, actual.getCause().getMessage(), is(expected.getMessage()));
    }
}
