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

class UriAuthorityTest {
    @Test
    void hostOnlyAuthority() {
        UriAuthority authority = UriAuthority.create("Api.Example.COM.");

        assertThat(authority.host(), is(UriHost.create("api.example.com")));
        assertThat(authority.port(), is(UriAuthority.UNDEFINED_PORT));
        assertThat(authority.hasPort(), is(false));
        assertThat(authority.portOrDefault(443), is(443));
        assertThat(authority.toString(), is("api.example.com"));
    }

    @Test
    void hostAndPortAuthority() {
        UriAuthority authority = UriAuthority.create("api.example.com:8443");

        assertThat(authority.host(), is(UriHost.create("api.example.com")));
        assertThat(authority.port(), is(8443));
        assertThat(authority.hasPort(), is(true));
        assertThat(authority.portOrDefault(443), is(8443));
        assertThat(authority.toString(), is("api.example.com:8443"));
    }

    @Test
    void ipv4Authority() {
        UriAuthority authority = UriAuthority.create("192.0.2.10:8080");

        assertThat(authority.host().kind(), is(UriHost.Kind.IPV4));
        assertThat(authority.host().value(), is("192.0.2.10"));
        assertThat(authority.port(), is(8080));
    }

    @Test
    void ipv6Authority() {
        UriAuthority authority = UriAuthority.create("[2001:DB8::1]:8443");

        assertThat(authority.host().kind(), is(UriHost.Kind.IPV6));
        assertThat(authority.host().value(), is("2001:db8::1"));
        assertThat(authority.port(), is(8443));
        assertThat(authority.toString(), is("[2001:db8::1]:8443"));
    }

    @Test
    void explicitCreate() {
        UriAuthority authority = UriAuthority.create(UriHost.create("api.example.com"), UriAuthority.UNDEFINED_PORT);

        assertThat(authority.host().value(), is("api.example.com"));
        assertThat(authority.hasPort(), is(false));
    }

    @Test
    void rejectsInvalidAuthority() {
        assertThrows(NullPointerException.class, () -> UriAuthority.create((String) null));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create(""));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("user@example.com"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("example.com:"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("example.com:-1"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("example.com:65536"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("example.com:abc"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("2001:db8::1"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("[::1"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("[::1]extra"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("[api.example.com]:443"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("[\uFF21::1]"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create("[::ffff:192.000.002.128]"));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create(UriHost.create("api.example.com"), -2));
    }

    @Test
    void equalityUsesNormalizedValue() {
        assertThat(UriAuthority.create("EXAMPLE.com:443"), is(UriAuthority.create("example.com.:443")));
    }

    @Test
    void registeredNameAuthority() {
        UriAuthority authority = UriAuthority.createForUri("API%5fNAME:8443");

        assertThat(authority.host().value(), is("api_name"));
        assertThat(authority.host().kind(), is(UriHost.Kind.REGISTERED_NAME));
        assertThat(authority.port(), is(8443));
        assertThat(authority.hasPort(), is(true));
        assertThat(authority.toString(), is("api_name:8443"));
        assertThat(authority, is(UriAuthority.createForUri("api_name", 8443)));
    }

    @Test
    void registeredNameAuthorityPreservesReservedEscapes() {
        UriAuthority authority = UriAuthority.createForUri("Name%2fPart%3aSuffix%40Origin:443");

        assertThat(authority.host().value(), is("name%2Fpart%3Asuffix%40origin"));
        assertThat(authority.host().kind(), is(UriHost.Kind.REGISTERED_NAME));
        assertThat(authority.toString(), is("name%2Fpart%3Asuffix%40origin:443"));
    }

    @ParameterizedTest
    @CsvSource({
            "API_NAME, api_name",
            "API_NAME:, api_name",
            "Example.COM:, example.com",
            "[2001:DB8::1]:, [2001:db8::1]"
    })
    void uriAuthorityHasNoPortForAbsentOrEmptyPort(String value, String expected) {
        UriAuthority authority = UriAuthority.createForUri(value);

        assertThat(authority.port(), is(UriAuthority.UNDEFINED_PORT));
        assertThat(authority.hasPort(), is(false));
        assertThat(authority.portOrDefault(443), is(443));
        assertThat(authority.toString(), is(expected));
    }

    @ParameterizedTest
    @CsvSource({
            "Api.Example.COM.:443, api.example.com:443",
            "%65XAMPLE.com:443, example.com:443",
            "%31%39%32.0.2.10:8080, 192.0.2.10:8080",
            "[2001:DB8::1]:8443, [2001:db8::1]:8443"
    })
    void uriAuthorityPreservesDnsAndIpNormalization(String value, String expected) {
        assertThat(UriAuthority.createForUri(value), is(UriAuthority.create(expected)));
    }

    @Test
    void uriAuthorityAcceptsPortBoundaries() {
        assertThat(UriAuthority.createForUri("api_name:0").port(), is(0));
        assertThat(UriAuthority.createForUri("api_name:65535").port(), is(65535));
        assertThat(UriAuthority.createForUri("api_name", 0).port(), is(0));
        assertThat(UriAuthority.createForUri("api_name", 65535).port(), is(65535));
        assertThat(UriAuthority.createForUri("api_name", UriAuthority.UNDEFINED_PORT).hasPort(), is(false));
    }

    @Test
    void uriAuthorityFromIpv6Host() {
        UriAuthority authority = UriAuthority.createForUri("2001:DB8::1", 8443);

        assertThat(authority.host().kind(), is(UriHost.Kind.IPV6));
        assertThat(authority.toString(), is("[2001:db8::1]:8443"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"API_NAME", "%65xample.com", "NAME%2fPART", "example.com:", "[::1]:"})
    void strictAuthorityFactoryStillRejectsRegisteredNamesAndEmptyPorts(String value) {
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create(value));
    }

    @Test
    void strictAuthorityFactoryRejectsRegisteredNameHost() {
        UriHost host = UriAuthority.createForUri("api_name").host();

        assertThrows(IllegalArgumentException.class, () -> UriAuthority.create(host, 443));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ":", ":80", "user@api_name", "host/path", "host?query", "host#fragment",
            "host\\name", "host%", "host%2", "host%GG", "host name", "host\r\nname", "2001:db8::1", "[::1",
            "[]", "[api_name]", "[[::1]]", "[::1]]", "[::1]extra", "host]", "host[", "[::1]:-1", "[::1]:65536",
            "[::1]:abc", "[::1]::80", "api_name:-1", "api_name:65536", "api_name:9999999999", "api_name:abc",
            "api_name:80:", "[\uFF21::1]", "[::ffff:192.000.002.128]"})
    void uriAuthorityRejectsInvalidValues(String value) {
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.createForUri(value));
    }

    @Test
    void uriAuthorityRejectsInvalidHostAndPortArguments() {
        assertThrows(NullPointerException.class, () -> UriAuthority.createForUri(null));
        assertThrows(NullPointerException.class, () -> UriAuthority.createForUri(null, 443));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.createForUri("", 443));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.createForUri("[::1]", 443));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.createForUri("api_name", -2));
        assertThrows(IllegalArgumentException.class, () -> UriAuthority.createForUri("api_name", 65536));
    }
}
