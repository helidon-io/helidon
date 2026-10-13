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

package io.helidon.webclient.api;

import java.net.URI;

import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestOriginTest {
    @Test
    void currentHostAndUriAreReadForEveryOrigin() {
        ClientUri uri = ClientUri.create(URI.create("HTTPS://URI.Example/"));
        WritableHeaders<?> headers = WritableHeaders.create();
        assertThat(ClientRequestOrigin.create(uri, headers).toString(), is("https://uri.example:443"));

        headers.set(HeaderNames.HOST, "HOST.Example.:8443");
        assertThat(ClientRequestOrigin.create(uri, headers).toString(), is("https://host.example:8443"));

        headers.set(HeaderNames.HOST, "a_b:443");
        assertThat(ClientRequestOrigin.create(uri, headers).toString(), is("https://uri.example:443"));

        uri.host("CHANGED.Example");
        assertThat(ClientRequestOrigin.create(uri, headers).toString(), is("https://changed.example:443"));

        headers.set(HeaderNames.HOST, "CHANGED-HOST.Example");
        assertThat(ClientRequestOrigin.create(uri, headers).toString(), is("https://changed-host.example:443"));
    }

    @Test
    void explicitMinusOnePortIsNotAnUnspecifiedPort() {
        ClientUri uri = ClientUri.create().scheme("HTTPS").host("URI.Example").port(-1);
        assertThrows(IllegalArgumentException.class, () -> ClientRequestOrigin.create(uri, WritableHeaders.create()));
    }
}
