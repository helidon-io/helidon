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

import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriIpv4PredicateTest {
    private static final String OCTET = "([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])";
    // Independent oracle: the expression used before the Boolean predicate optimization.
    private static final Pattern IPV4 = Pattern.compile("^" + OCTET + "\\." + OCTET + "\\."
                                                               + OCTET + "\\." + OCTET + "$");

    @Test
    void everyOctetAndPaddingMatchesRegexOracle() {
        for (int position = 0; position < 4; position++) {
            for (int value = 0; value < 1000; value++) {
                for (int width = 1; width <= 3; width++) {
                    String octet = Integer.toString(value);
                    octet = "0".repeat(Math.max(0, width - octet.length())) + octet;
                    String[] parts = {"127", "0", "0", "1"};
                    parts[position] = octet;
                    assertMatchesOracle(String.join(".", parts));
                }
            }
        }
    }

    @Test
    void malformedAndNonAsciiInputsMatchRegexOracle() {
        var random = new Random(58838);
        String alphabet = "0123456789.x\r\n\u0085\u2028\u2029\uff10\ud800";
        for (int sample = 0; sample < 2048; sample++) {
            var input = new StringBuilder();
            int length = random.nextInt(24);
            for (int index = 0; index < length; index++) {
                input.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            assertMatchesOracle(input.toString());
        }
        assertMatchesOracle("9".repeat(16384));
        assertThrows(NullPointerException.class, () -> UriValidator.isIpv4Address(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1.2.3", "1.2.3.4.5", "1.2.3.4.", ".1.2.3.4", "1..2.3.4", "0000.0.0.1",
                            "1.2.3.4\n", "1.2.3.4\r\n", "1.2.3.4\u0085", "1.2.3.4\u2028", "１.2.3.4"})
    void boundaryInputsMatchRegexOracle(String input) {
        assertMatchesOracle(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "255.255.255.255", "256.1.2.3", "999.999.999.999", "001.002.003.004"})
    void numericRegisteredNamesRemainValid(String input) {
        UriValidator.validateNonIpLiteral(input);
        UriValidator.validateHost(input);
        assertMatchesOracle(input);
    }

    @Test
    void registeredNameFallbackRetainsValidationErrors() {
        assertThrows(NullPointerException.class, () -> UriValidator.validateNonIpLiteral(null));
        for (String input : new String[] {"", " ", "1.2.3.4\n", "１.2.3.4", "bad%2host", "bad/host"}) {
            assertThrows(UriValidationException.class, () -> UriValidator.validateNonIpLiteral(input), input);
        }
        // The IPv6 capture-dependent path must still reject leading-zero octets.
        UriValidator.validateIpLiteral("[::ffff:192.0.2.128]");
        assertThrows(UriValidationException.class,
                     () -> UriValidator.validateIpLiteral("[::ffff:192.000.002.128]"));
    }

    private static void assertMatchesOracle(String input) {
        assertThat("IPv4 predicate for " + input, UriValidator.isIpv4Address(input), is(IPV4.matcher(input).matches()));
    }
}
