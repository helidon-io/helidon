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
}
