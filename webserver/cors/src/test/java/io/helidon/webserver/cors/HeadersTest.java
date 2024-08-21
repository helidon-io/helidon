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

import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ServerRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeadersTest {

    @Test
    void checkHeaders() {
        HttpRequest.Path path = mock(HttpRequest.Path.class);
        when(path.toString())
                .thenReturn("/testpath");

        RequestHeaders requestHeaders = mock(RequestHeaders.class);
        when(requestHeaders.toMap())
                .thenReturn(Map.of("Origin", List.of("http://myhost.com"),
                                   "Host", List.of("otherhost"),
                                   "Authorization", List.of("some-auth"),
                                   "X-Custom", List.of("myValue")));

        Http.RequestMethod requestMethod = mock(Http.RequestMethod.class);
        when(requestMethod.name()).thenReturn("POST");

        ServerRequest serverRequest = mock(ServerRequest.class);
        when(serverRequest.path()).thenReturn(path);
        when(serverRequest.method()).thenReturn(requestMethod);
        when(serverRequest.headers()).thenReturn(requestHeaders);

        RequestAdapterSe requestAdapterSe = new RequestAdapterSe(serverRequest);
        assertThat("Headers",
                   requestAdapterSe.toString(),
                   allOf(
                           containsString("path=/testpath"),
                           containsString("method=POST"),
                           containsString("Origin=[http://myhost.com]"),
                           containsString("Host=[otherhost]"),
                           not(containsString("Authorization")),
                           not(containsString("X-Custom"))));
    }
}
