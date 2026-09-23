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
package io.helidon.microprofile.cors;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.common.http.Http;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SameOriginTest {

    private static final String HTTP_REQUEST = "http://localhost:7001/probe";
    private static final String HTTPS_REQUEST = "https://localhost:7001/probe";
    private static final String ALLOWED_ORIGIN = "http://trusted.example";

    @TestFactory
    Stream<DynamicTest> checksRequestOrigin() {
        return Stream.of(
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com", false, false),
                new RequestCase(HTTP_REQUEST, "EXAMPLE.COM", "HTTP://example.com", false, false),
                new RequestCase(HTTP_REQUEST, "my_app.example", "http://my_app.example", false, false),
                new RequestCase(HTTP_REQUEST, "my_app.example:00080", "http://my_app.example", false, false),
                new RequestCase(HTTP_REQUEST, "my_app.example:08080", "http://my_app.example:8080", false, false),
                new RequestCase(HTTPS_REQUEST, "example.com", "https://example.com", false, false),
                new RequestCase(HTTPS_REQUEST, "my_app.example:443", "https://my_app.example", false, false),
                new RequestCase(HTTP_REQUEST, "example.com:80", "http://example.com", false, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com:80", false, false),
                new RequestCase(HTTP_REQUEST, "example.com:443", "http://example.com:443", false, false),
                new RequestCase(HTTPS_REQUEST, "example.com:443", "https://example.com", false, false),
                new RequestCase(HTTPS_REQUEST, "example.com", "https://example.com:443", false, false),
                new RequestCase(HTTPS_REQUEST, "example.com:80", "https://example.com:80", false, false),
                new RequestCase(HTTPS_REQUEST, "example.com:8443", "https://example.com:8443", false, false),
                new RequestCase(HTTPS_REQUEST, "[::1]:443", "https://[::1]", false, false),
                new RequestCase(HTTP_REQUEST, "[2001:db8::1]:8080", "http://[2001:DB8::1]:8080", false, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com.attacker.invalid", true, false),
                new RequestCase(HTTP_REQUEST, "my_app.example", "http://my_app.example.attacker.invalid", true, false),
                new RequestCase(HTTP_REQUEST, "my_app.example", "http://my-app.example", true, false),
                new RequestCase(HTTPS_REQUEST, "example.com", "https://example.com.attacker.invalid", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "https://example.com", true, false),
                new RequestCase(HTTPS_REQUEST, "example.com", "http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com:443", "https://example.com", true, false),
                new RequestCase(HTTPS_REQUEST, "example.com:80", "http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com:8080", true, false),
                new RequestCase(HTTPS_REQUEST, "example.com:443", "https://example.com:4430", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com@attacker.invalid", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://user@example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://attacker.invalid/http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://attacker.invalid?q=http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://attacker.invalid#http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com/path", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com/", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com?query", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com#fragment", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com:", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com:65536", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com:-1", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com:+80", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com:bad", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com:99999999999999999999", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com http://other.invalid", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://example.com\\@attacker.invalid", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "http://", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "null", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", "", true, false),
                new RequestCase(HTTP_REQUEST, "[::1]", "http://[::2]", true, false),
                new RequestCase(HTTP_REQUEST, "[::1]", "http://[::1", true, false),
                new RequestCase(HTTP_REQUEST, "[::1]", "http://::1", true, false),
                new RequestCase(HTTP_REQUEST, "example%2ecom", "http://example%2ecom", true, false),
                new RequestCase(HTTP_REQUEST, "ex\u00e4mple.com", "http://ex\u00e4mple.com", true, false),
                new RequestCase(HTTP_REQUEST, "", "http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com:", "http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com:65536", "http://example.com:65536", true, false),
                new RequestCase(HTTP_REQUEST, "user@example.com", "http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com/path", "http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com?query", "http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com#fragment", "http://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com\\", "http://example.com", true, false),
                new RequestCase("/probe", "example.com", "http://example.com", true, false),
                new RequestCase("ftp://localhost/probe", "example.com", "ftp://example.com", true, false),
                new RequestCase(HTTP_REQUEST, "example.com", ALLOWED_ORIGIN, false, true),
                new RequestCase(HTTPS_REQUEST, "example.com", ALLOWED_ORIGIN, false, true))
                .map(requestCase -> DynamicTest.dynamicTest(requestCase.toString(), () -> verifyRequest(requestCase)));
    }

    private static void verifyRequest(RequestCase requestCase) {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create(requestCase.uri()));
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getUriInfo()).thenReturn(uriInfo);
        when(context.getMethod()).thenReturn("POST");
        when(context.getHeaders()).thenReturn(new MultivaluedHashMap<>(Map.of(
                Http.Header.HOST, requestCase.host(),
                Http.Header.ORIGIN, requestCase.origin())));

        CorsSupportMp support = CorsSupportMp.builder()
                .allowOrigins(ALLOWED_ORIGIN)
                .allowMethods("POST")
                .build();
        CorsSupportMp.RequestAdapterMp requestAdapter = new CorsSupportMp.RequestAdapterMp(context);
        CorsSupportMp.ResponseAdapterMp responseAdapter = new CorsSupportMp.ResponseAdapterMp();
        Optional<Response> response = support.processRequest(requestAdapter, responseAdapter);

        if (requestCase.rejected()) {
            try (Response denial = response.orElseThrow()) {
                assertThat(denial.getStatus(), is(403));
                assertThat(denial.getEntity(), is("CORS origin is not in allowed list"));
                assertThat(denial.getHeaderString(ACCESS_CONTROL_ALLOW_ORIGIN), nullValue());
            }
        } else {
            assertThat(response, is(Optional.empty()));
            support.prepareResponse(requestAdapter, responseAdapter);
            try (Response prepared = responseAdapter.ok()) {
                if (requestCase.corsHeaders()) {
                    assertThat(prepared.getHeaderString(ACCESS_CONTROL_ALLOW_ORIGIN), is(ALLOWED_ORIGIN));
                } else {
                    assertThat(prepared.getHeaderString(ACCESS_CONTROL_ALLOW_ORIGIN), nullValue());
                }
            }
        }
    }

    private record RequestCase(String uri, String host, String origin, boolean rejected, boolean corsHeaders) {
    }
}
