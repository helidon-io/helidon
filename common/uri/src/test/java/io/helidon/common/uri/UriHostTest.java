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
}
