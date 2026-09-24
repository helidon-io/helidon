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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriHostTest {
    @Test
    void dnsHostIsNormalized() {
        UriHost host = UriHost.create("Api.Example.COM.");

        assertThat(host.value(), is("api.example.com"));
        assertThat(host.kind(), is(UriHost.Kind.DNS));
        assertThat(host.toString(), is("api.example.com"));
    }

    @Test
    void idnHostIsNormalized() {
        UriHost host = UriHost.create("Bücher.example");

        assertThat(host.value(), is("xn--bcher-kva.example"));
        assertThat(host.kind(), is(UriHost.Kind.DNS));
    }

    @Test
    void ipv4HostIsNormalized() {
        UriHost host = UriHost.create("192.0.2.10");

        assertThat(host.value(), is("192.0.2.10"));
        assertThat(host.kind(), is(UriHost.Kind.IPV4));
    }

    @Test
    void dottedNumericHostThatIsNotIpv4IsDns() {
        UriHost host = UriHost.create("192.000.002.010");

        assertThat(host.value(), is("192.000.002.010"));
        assertThat(host.kind(), is(UriHost.Kind.DNS));
        assertThat(UriHost.create("192.0.2.999").kind(), is(UriHost.Kind.DNS));
    }

    @Test
    void ipv6HostIsNormalizedWithoutBrackets() {
        UriHost host = UriHost.create("2001:DB8::1");

        assertThat(host.value(), is("2001:db8::1"));
        assertThat(host.kind(), is(UriHost.Kind.IPV6));
        assertThat(UriHost.create("::ffff:192.0.2.128").value(), is("::ffff:c000:280"));
    }

    @Test
    void rejectsInvalidHost() {
        assertThrows(NullPointerException.class, () -> UriHost.create(null));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create(""));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("   "));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("user@example.com"));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("*.example.com"));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("[::1]"));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("example.com:443"));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("2001:db8::1%eth0"));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("\uFF21::1"));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create("::ffff:192.000.002.128"));
    }

    @Test
    void equalityUsesNormalizedValueAndKind() {
        assertThat(UriHost.create("EXAMPLE.com"), is(UriHost.create("example.com.")));
        assertThat(UriHost.create("2001:DB8::1"), is(UriHost.create("2001:db8:0:0:0:0:0:1")));
    }

    @ParameterizedTest
    @CsvSource({
            "Api.Example.COM., api.example.com, DNS",
            "Bücher.example, xn--bcher-kva.example, DNS",
            "%41pi.%65xample.COM., api.example.com, DNS",
            "192.0.2.10, 192.0.2.10, IPV4",
            "%31%39%32.0.2.10, 192.0.2.10, IPV4",
            "192.000.002.010, 192.000.002.010, DNS",
            "2001:DB8::1, 2001:db8::1, IPV6",
            "::ffff:192.0.2.128, ::ffff:c000:280, IPV6"
    })
    void uriHostPreservesDnsAndIpNormalization(String value, String expected, UriHost.Kind kind) {
        UriHost host = UriHost.createForUri(value);

        assertThat(host.value(), is(expected));
        assertThat(host.kind(), is(kind));
        assertThat(host, is(UriHost.create(expected)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXAMPLE.COM.", "xn--bcher-kva.example", "XN--A.EXAMPLE", "a--b.example", "0.example",
            "123", "192.000.002.010", "192.0.2.999"})
    void asciiUriDnsClassificationMatchesStrictFactory(String value) {
        assertThat(UriHost.createForUri(value), is(UriHost.create(value)));
    }

    @Test
    void uriDnsClassificationKeepsDnsLengthBoundaries() {
        String longestLabel = "a".repeat(63);
        String longestName = longestLabel + "." + longestLabel + "." + longestLabel + "." + "b".repeat(61);

        assertThat(UriHost.createForUri(longestName), is(UriHost.create(longestName)));
        assertThat(UriHost.createForUri(longestLabel + ".example"), is(UriHost.create(longestLabel + ".example")));
        assertThat(UriHost.createForUri(longestName + "a").kind(), is(UriHost.Kind.REGISTERED_NAME));
        assertThat(UriHost.createForUri(longestLabel + "a.example").kind(), is(UriHost.Kind.REGISTERED_NAME));
    }

    @ParameterizedTest
    @CsvSource({
            "API_NAME, api_name",
            "%41PI%5fNAME, api_name",
            "Example~Name, example~name",
            "Example%7eName, example~name",
            "NAME%2fPART, name%2Fpart",
            "NAME%40PART, name%40part",
            "NAME%3aPART, name%3Apart",
            "NAME%2541, name%2541",
            "%c3%bc.example, %C3%BC.example",
            "NAME..Example, name..example",
            "-NAME, -name",
            "NAME-, name-",
            "API_NAME., api_name.",
            "., ."
    })
    void registeredNameIsNormalized(String value, String expected) {
        UriHost host = UriHost.createForUri(value);

        assertThat(host.value(), is(expected));
        assertThat(host.kind(), is(UriHost.Kind.REGISTERED_NAME));
        assertThat(host.toString(), is(expected));
        assertThat(host, is(UriHost.createForUri(expected)));
        assertThat(host.hashCode(), is(UriHost.createForUri(expected).hashCode()));
    }

    @Test
    void registeredNameAcceptsAllSubDelimiters() {
        UriHost host = UriHost.createForUri("NAME!$&'()*+,;=");

        assertThat(host.value(), is("name!$&'()*+,;="));
        assertThat(host.kind(), is(UriHost.Kind.REGISTERED_NAME));
    }

    @Test
    void registeredNameIsNotSubjectToDnsLabelLimits() {
        String value = "A".repeat(254);
        UriHost host = UriHost.createForUri(value);

        assertThat(host.value(), is("a".repeat(254)));
        assertThat(host.kind(), is(UriHost.Kind.REGISTERED_NAME));
        assertThrows(IllegalArgumentException.class, () -> UriHost.create(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"API_NAME", "*.example.com", "%65xample.com", "NAME%2fPART", "NAME..Example"})
    void strictHostFactoryStillRejectsRegisteredNames(String value) {
        assertThrows(IllegalArgumentException.class, () -> UriHost.create(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "user@example.com", "host/path", "host?query", "host#fragment", "host\\name",
            "host%", "host%2", "host%GG", "host name", "host\r\nname", "[::1]", "[host]", "host]", "host[",
            "example.com:443", "2001:db8::1%eth0", "\uFF21::1", "::ffff:192.000.002.128"})
    void uriHostRejectsInvalidValues(String value) {
        assertThrows(IllegalArgumentException.class, () -> UriHost.createForUri(value));
    }

    @Test
    void uriHostRejectsNull() {
        assertThrows(NullPointerException.class, () -> UriHost.createForUri(null));
    }
}
