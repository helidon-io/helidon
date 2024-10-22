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
package io.helidon.webserver.cors;

import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.ServerRequest;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeadersTest {

    @Test
    void checkHeaders() {
        RoutedPath path = Mockito.mock(RoutedPath.class);
        Mockito.when(path.path())
                .thenReturn("/testpath");

        ServerRequestHeaders serverRequestHeaders = ServerRequestHeaders.create(
                WritableHeaders.create()
                        .add(HeaderNames.ORIGIN, "http://myhost.com")
                        .add(HeaderNames.HOST, "otherhost")
                        .add(HeaderNames.AUTHORIZATION, "some-auth")
                        .add(HeaderNames.create("X-Custom"), "some-auth"));

        HttpPrologue httpPrologue = mock(HttpPrologue.class);
        when(httpPrologue.method())
                .thenReturn(Method.POST);
        ServerRequest serverRequest = Mockito.mock(ServerRequest.class);

        Mockito.when(serverRequest.path())
                .thenReturn(path);
        Mockito.when(serverRequest.prologue())
                .thenReturn(httpPrologue);
        Mockito.when(serverRequest.headers())
                .thenReturn(serverRequestHeaders);

        CorsServerRequestAdapter requestAdapterSe = new CorsServerRequestAdapter(serverRequest, null);
        assertThat("Headers",
                   requestAdapterSe.toString(),
                   allOf(
                           containsString("path=/testpath"),
                           containsString("method=POST"),
                           containsString("Origin: http://myhost.com"),
                           containsString("Host: otherhost"),
                           not(containsString("Authorization")),
                           not(containsString("X-Custom"))));
    }
}
