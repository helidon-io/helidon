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

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriInfo;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
A unit test for CorsValidator
This ensures that various CORS requests are handled as expected without the involvement of WebServer
 */
public class CorsValidatorTest {
    @Test
    public void testNoDefaultsNonCors() {
        CorsValidator validator = CorsValidator.create(CorsConfig.builder()
                                                               .addDefaults(false)
                                                               .buildPrototype(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);

        boolean success = validator.checkNonOptions(request(Method.PUT, "/test", WritableHeaders.create()),
                                                    response);

        assertThat("CORS should succeed, as this is not a CORS request", success, is(true));
        verify(response, never()).status(statusCaptor.capture());
    }

    @Test
    public void testNoDefaultsCorsSameOrigin() {
        CorsValidator validator = CorsValidator.create(CorsConfig.builder()
                                                               .addDefaults(false)
                                                               .buildPrototype(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.HOST, "www.example.com:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);

        boolean success = validator.checkNonOptions(request(Method.PUT, "/test", requestHeaders),
                                                    response);

        assertThat("CORS should succeed, as this is a same origin CORS request", success, is(true));
        verify(response, never()).status(statusCaptor.capture());
    }

    @Test
    public void testDefaultsCorsGet() {
        CorsValidator validator = CorsValidator.create(CorsConfig.create(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.HOST, "localhost:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        boolean success = validator.checkNonOptions(request(Method.GET, "/test", requestHeaders),
                                                    response);

        assertThat("CORS should succeed, as this is a GET request and defaults are configured", success, is(true));
        verify(response, never()).status(statusCaptor.capture());
    }

    @Test
    public void testNoDefaultsNonCorsOptions() {
        CorsValidator validator = CorsValidator.create(CorsConfig.builder()
                                                               .addDefaults(false)
                                                               .buildPrototype(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);

        boolean success = validator.checkOptions(request(Method.OPTIONS, "/test", WritableHeaders.create()),
                                                 response);

        assertThat("CORS should succeed, as this is not a CORS request", success, is(true));
        verify(response, never()).status(statusCaptor.capture());
    }

    @Test
    public void testNoDefaultsCorsSameOriginPreFlight() {
        CorsValidator validator = CorsValidator.create(CorsConfig.builder()
                                                               .addDefaults(false)
                                                               .buildPrototype(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        requestHeaders.set(HeaderNames.HOST, "www.example.com:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);

        boolean success = validator.checkOptions(request(Method.OPTIONS, "/test", requestHeaders),
                                                 response);

        assertThat("CORS should succeed, as this is a same origin CORS request", success, is(true));
        verify(response, never()).status(statusCaptor.capture());
    }


    @Test
    public void testDefaultsCorsGetPreFlight() {
        CorsValidator validator = CorsValidator.create(CorsConfig.create(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.HOST, "localhost:80");
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "GET");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        boolean success = validator.checkOptions(request(Method.OPTIONS, "/test", requestHeaders),
                                                 response);

        assertThat("CORS should succeed, as this is a GET request and defaults are configured", success, is(true));
        verify(response, never()).status(statusCaptor.capture());
    }

    // this is for backward compatibility
    // uncomment the tests below once we remove deprecated code
    // we must now delegate to the (possible) custom CORS routes
    /*
    @Test
    public void testDefaultsCorsPut() {
        CorsValidator validator = CorsValidator.create(CorsConfig.create(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.HOST, "localhost:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        boolean success = validator.checkNonOptions(request(Method.PUT, "/test", requestHeaders),
                                                    response);

        assertThat("CORS should fail, as this is a PUT request with default config", success, is(false));
        assertThat(statusCaptor.getValue(), is(Status.FORBIDDEN_403));

        verify(response, times(1)).send();
    }

     @Test
    public void testNoDefaultsCorGet() {
        CorsValidator validator = CorsValidator.create(CorsConfig.builder()
                                                               .addDefaults(false)
                                                               .buildPrototype(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.HOST, "localhost:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        // even GET method fails, as we disabled defaults
        boolean success = validator.checkNonOptions(request(Method.GET, "/test", requestHeaders),
                                                    response);

        assertThat("CORS should fail, as this is a bad origin CORS request (all should be forbidden)", success, is(false));
        assertThat(statusCaptor.getValue(), is(Status.FORBIDDEN_403));

        verify(response, times(1)).send();
    }

    @Test
    public void testDefaultsCorsPutPreFlight() {
        CorsValidator validator = CorsValidator.create(CorsConfig.create(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        requestHeaders.set(HeaderNames.HOST, "localhost:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        // even GET method fails, as we disabled defaults
        boolean success = validator.checkOptions(request(Method.OPTIONS, "/test", requestHeaders),
                                                 response);

        assertThat("CORS should fail, as this is a PUT request with default config", success, is(false));
        assertThat(statusCaptor.getValue(), is(Status.FORBIDDEN_403));

        verify(response, times(1)).send();
    }

      @Test
    public void testNoDefaultsCorsDifferentOrigin() {
        CorsValidator validator = CorsValidator.create(CorsConfig.builder()
                                                               .addDefaults(false)
                                                               .buildPrototype(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.HOST, "localhost:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        // even GET method fails, as we disabled defaults
        boolean success = validator.checkNonOptions(request(Method.PUT, "/test", requestHeaders),
                                                    response);

        assertThat("CORS should fail, as this is a PUT request and no defaults are configured", success, is(false));
        assertThat(statusCaptor.getValue(), is(Status.FORBIDDEN_403));

        verify(response, times(1)).send();
    }
     @Test
    public void testNoDefaultsCorsDifferentOriginPreFlight() {
        CorsValidator validator = CorsValidator.create(CorsConfig.builder()
                                                               .addDefaults(false)
                                                               .buildPrototype(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        requestHeaders.set(HeaderNames.HOST, "localhost:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        // even GET method fails, as we disabled defaults
        boolean success = validator.checkOptions(request(Method.OPTIONS, "/test", requestHeaders),
                                                 response);

        assertThat("CORS should fail, as this is a PUT request and no defaults are configured", success, is(false));
        assertThat(statusCaptor.getValue(), is(Status.FORBIDDEN_403));

        verify(response, times(1)).send();
    }

    @Test
    public void testNoDefaultsCorGetPreFlight() {
        CorsValidator validator = CorsValidator.create(CorsConfig.builder()
                                                               .addDefaults(false)
                                                               .buildPrototype(),
                                                       WebServer.DEFAULT_SOCKET_NAME);

        String origin = "http://www.example.com";
        // request
        var requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderNames.ORIGIN, origin);
        requestHeaders.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        requestHeaders.set(HeaderNames.HOST, "localhost:80");

        // response
        var statusCaptor = ArgumentCaptor.forClass(Status.class);
        var responseHeaders = ServerResponseHeaders.create();
        var response = response(responseHeaders);
        when(response.status(statusCaptor.capture())).thenReturn(response);

        // even GET method fails, as we disabled defaults
        boolean success = validator.checkOptions(request(Method.OPTIONS, "/test", requestHeaders),
                                                 response);

        assertThat("CORS should fail, as this is a bad origin CORS request (all should be forbidden)", success, is(false));
        assertThat(statusCaptor.getValue(), is(Status.FORBIDDEN_403));

        verify(response, times(1)).send();
    }
    */

    private static ServerRequest request(Method method, String path, Headers headers) {
        ServerRequestHeaders reqHeaders = ServerRequestHeaders.create(headers);
        RoutedPath routedPath = new TestRoutedPath(path);
        ServerRequest request = Mockito.mock(ServerRequest.class);
        when(request.headers())
                .thenReturn(reqHeaders);
        when(request.path())
                .thenReturn(routedPath);
        UriInfo.Builder uriInfo = UriInfo.builder()
                .path(path);
        if (headers.contains(HeaderNames.HOST)) {
            uriInfo.authority(headers.get(HeaderNames.HOST).get());
        }

        when(request.requestedUri())
                .thenReturn(uriInfo.build());

        when(request.prologue())
                .thenReturn(HttpPrologue.create("HTTP", "HTTP", "1.1", method, path, false));
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
