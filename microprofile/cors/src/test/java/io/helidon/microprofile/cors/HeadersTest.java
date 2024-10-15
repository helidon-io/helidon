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
package io.helidon.microprofile.cors;

import java.net.URI;
import java.util.Map;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
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

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://myhost.com/testpath"));
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getHeaders()).thenReturn(new MultivaluedHashMap<>(Map.of("Origin", "http://myhost.com",
                                                                              "Host", "otherhost",
                                                                              "Authorization", "some-auth",
                                                                              "X-Custom", "myValue")));
        when(context.getMethod()).thenReturn("POST");
        when(context.getUriInfo()).thenReturn(uriInfo);

        CorsSupportMp.RequestAdapterMp requestAdapterMp = new CorsSupportMp.RequestAdapterMp(context);

        assertThat("Headers",
                   requestAdapterMp.toString(),
                   allOf(
                           containsString("path=/testpath"),
                           containsString("method=POST"),
                           containsString("Origin=[http://myhost.com]"),
                           containsString("Host=[otherhost]"),
                           not(containsString("Authorization")),
                           not(containsString("X-Custom"))));
    }
}
