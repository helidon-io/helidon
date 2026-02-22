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

package io.helidon.webserver.cors;

import java.time.Duration;
import java.util.Set;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeaderValue;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.http.HeaderNames.VARY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
A unit test for CorsPathValidator
This ensures that various CORS requests are handled as expected without the involvement of WebServer
 */
public class CorsPathValidatorTest {
    static {
        LogConfig.initClass();
    }

    @Test
    public void testNotMatched() {
        CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                       .pathPattern("/greet")
                                                                       .build());

        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, "http://example.com");
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);

        CorsPathValidator.Result r = validator.preFlight(request("/test", requestHeaders),
                                                         response);

        assertThat("Should not match path", r.matched(), is(false));
        assertThat("There should be no configured response headers", responseHeaders.size(), is(0));
        verify(response, never()).status(statusCaptor.capture());
    }

    @Test
    public void testPreFlightDefaults() {
        CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                       .pathPattern("/greet")
                                                                       .build());

        String origin = "http://example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        verify(response, never()).status(statusCaptor.capture());

        CorsPathValidator.Result r = validator.preFlight(request("/greet", requestHeaders),
                                                         response);

        assertThat("Should match path", r.matched(), is(true));
        assertThat(r.shouldContinue(), is(true));

        /*
        A successful Pre-flight request will echo origin and method back to the caller
        Max-Age is set, as we have a default configured for it
         */

        assertThat("There should be 3 configured response headers", responseHeaders.size(), is(3));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is("*")));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_METHODS, is("*")));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_MAX_AGE, is("3600")));
    }

    @Test
    public void testPreFlightCustom() {
        CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                       .pathPattern("/greet")
                                                                       .clearAllowOrigins()
                                                                       .addAllowOrigin("http://example.com")
                                                                       .addAllowOrigin("http://foo.bar")
                                                                       .clearAllowMethods()
                                                                       .addAllowMethod(Method.PUT)
                                                                       .addAllowMethod(Method.DELETE)
                                                                       .addAllowHeader(HeaderNames.CONTENT_TYPE)
                                                                       .maxAge(Duration.ofSeconds(150))
                                                                       .build());

        String origin = "http://example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        verify(response, never()).status(statusCaptor.capture());

        CorsPathValidator.Result r = validator.preFlight(request("/greet", requestHeaders),
                                                         response);

        assertThat("Should match path", r.matched(), is(true));
        assertThat(r.shouldContinue(), is(true));

        /*
        A successful Pre-flight request will echo origin and method back to the caller
        Max-Age is set, as we have a default configured for it
         */

        // assertThat("There should be 3 configured response headers", responseHeaders.size(), is(3));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is(origin)));
        assertThat(responseHeaders, HttpHeaderMatcher.hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT", "DELETE"));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_MAX_AGE, is("150")));
    }

    @Test
    public void testPreFlightCustomPattern() {
        CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                       .pathPattern("/greet")
                                                                       .clearAllowOrigins()
                                                                       .addAllowOrigin("https?://.*\\.?example.com")
                                                                       .addAllowOrigin("http://foo.bar")
                                                                       .clearAllowMethods()
                                                                       .addAllowMethod(Method.PUT)
                                                                       .addAllowMethod(Method.DELETE)
                                                                       .addAllowHeader(HeaderNames.CONTENT_TYPE)
                                                                       .maxAge(Duration.ofSeconds(150))
                                                                       .build());

        String origin = "http://example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        verify(response, never()).status(statusCaptor.capture());

        CorsPathValidator.Result r = validator.preFlight(request("/greet", requestHeaders),
                                                         response);

        assertThat("Should match path", r.matched(), is(true));
        assertThat(r.shouldContinue(), is(true));

        /*
        A successful Pre-flight request will echo origin and method back to the caller
        Max-Age is set, as we have a default configured for it
         */

        // assertThat("There should be 3 configured response headers", responseHeaders.size(), is(3));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is(origin)));
        assertThat(responseHeaders, HttpHeaderMatcher.hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT", "DELETE"));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_MAX_AGE, is("150")));
    }

    @Test
    public void testPreFlightForbidden() {
        CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                       .pathPattern("/greet")
                                                                       .allowOrigins(Set.of("http://example.com"))
                                                                       .build());

        String origin = "http://foo.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        CorsPathValidator.Result r = validator.preFlight(request("/greet", requestHeaders),
                                                         response);

        assertThat("Should match path", r.matched(), is(true));
        assertThat(r.shouldContinue(), is(false));

        /*
        A failed pre-flight will return forbidden and send a response
         */

        assertThat("There should be 0 configured response headers", responseHeaders.size(), is(0));
        assertThat(statusCaptor.getValue(), is(Status.FORBIDDEN_403));

        verify(response, times(1)).send();
    }

    @Test
    public void testPreFlightCredentials() {
        String origin = "http://example.com";

        CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                       .pathPattern("/greet")
                                                                       .allowOrigins(Set.of(origin))
                                                                       .allowCredentials(true)
                                                                       .build());

        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);

        CorsPathValidator.Result r = validator.preFlight(request("/greet", requestHeaders),
                                                         response);

        assertThat("Should match path", r.matched(), is(true));
        assertThat(r.shouldContinue(), is(true));

        /*
        A successful Pre-flight request will echo origin and method back to the caller
        Max-Age is set, as we have a default configured for it
        Allow-Credentials is set, as it is enabled
         */

        assertThat("There should be 4 configured response headers", responseHeaders.size(), is(5));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is(origin)));
        assertThat(responseHeaders, hasHeaderValue(VARY, is("Origin")));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_METHODS, is("PUT")));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_MAX_AGE, is("3600")));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_CREDENTIALS, is("true")));

        verify(response, never()).status(statusCaptor.capture());
    }

    @Test
    public void testFlightCredentials() {
        CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                       .pathPattern("/greet")
                                                                       .allowCredentials(true)
                                                                       .build());

        String origin = "http://example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        var request = request("/greet", requestHeaders);
        when(request.prologue())
                .thenReturn(HttpPrologue.create("HTTP", "HTTP", "1.1", Method.GET, "/greet", false));

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);

        verify(response, never()).status(statusCaptor.capture());

        CorsPathValidator.Result r = validator.flight(request, response);

        assertThat("Should match path", r.matched(), is(true));
        assertThat(r.shouldContinue(), is(true));

        /*
        A successful flight request will send back our origin (as we have credentials set to true)
        In addition we will have credentials set to true, and Vary: Origin
         */

        assertThat("There should be 3 configured response headers", responseHeaders.size(), is(3));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is(origin)));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_CREDENTIALS, is("true")));
        assertThat(responseHeaders, hasHeaderValue(VARY, is("Origin")));
    }

    @Test
    public void testFlightDefaults() {
        CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                       .pathPattern("/greet")
                                                                       .build());

        String origin = "http://example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        var request = request("/greet", requestHeaders);
        when(request.prologue())
                .thenReturn(HttpPrologue.create("HTTP", "HTTP", "1.1", Method.GET, "/greet", false));

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);

        verify(response, never()).status(statusCaptor.capture());

        CorsPathValidator.Result r = validator.flight(request,
                                                      response);

        assertThat("Should match path", r.matched(), is(true));
        assertThat(r.shouldContinue(), is(true));

        /*
        A successful flight request will send back * (if we have that as the Allowed origins)
        No other header is present by default
         */

        assertThat("There should be 1 configured response header", responseHeaders.size(), is(1));
        assertThat(responseHeaders, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is(Cors.ALLOW_ALL)));
    }

    private static ServerRequest request(String path, Headers headers) {
        ServerRequestHeaders reqHeaders = ServerRequestHeaders.create(headers);
        RoutedPath routedPath = new TestRoutedPath(path);
        ServerRequest request = Mockito.mock(ServerRequest.class);
        when(request.headers())
                .thenReturn(reqHeaders);
        when(request.path())
                .thenReturn(routedPath);
        return request;
    }

    private static ServerResponse response(ServerResponseHeaders headers) {
        ServerResponse response = Mockito.mock(ServerResponse.class);

        when(response.headers()).thenReturn(headers);

        return response;
    }

    private static class TestRoutedPath implements RoutedPath {
        private final String path;

        private TestRoutedPath(String path) {
            this.path = path;
        }

        @Override
        public Parameters pathParameters() {
            return Parameters.empty("path");
        }

        @Override
        public RoutedPath absolute() {
            return this;
        }

        @Override
        public String rawPath() {
            return path;
        }

        @Override
        public String rawPathNoParams() {
            return path;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public Parameters matrixParameters() {
            return Parameters.empty("path");
        }

        @Override
        public void validate() {
        }
    }
}
