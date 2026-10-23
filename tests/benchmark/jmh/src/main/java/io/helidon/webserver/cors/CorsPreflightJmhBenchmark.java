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

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures CORS preflight validation without network I/O or request parsing.
 * The default inputs succeed both before and after the comma-list parsing fix.
 * The optional {@code BARE_COMMA} and {@code REPEATED} inputs exercise the corrected
 * behavior; set {@code expectedAllowed=false} when running them against the old implementation.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class CorsPreflightJmhBenchmark {
    @Param({"SPACED", "SINGLE"})
    public String headerForm;

    @Param("true")
    public boolean expectedAllowed;

    private CorsPathValidator validator;
    private ServerRequest request;
    private ServerResponse response;
    private ServerResponseHeaders responseHeaders;
    private Status status;

    @Setup
    public void setup() {
        validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                    .pathPattern("/greet")
                                                    .allowOrigins(Set.of("https://example.com"))
                                                    .allowMethods(Set.of("POST"))
                                                    .allowHeaders(Set.of("authorization", "content-type", "x-request-id"))
                                                    .build());
        var headers = WritableHeaders.create();
        headers.set(HeaderNames.ORIGIN, "https://example.com");
        headers.set(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        switch (headerForm) {
        case "SPACED" -> headers.set(HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type");
        case "SINGLE" -> headers.set(HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "authorization");
        case "BARE_COMMA" -> headers.set(HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
        case "REPEATED" -> {
            headers.add(HeaderValues.create(HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"));
            headers.add(HeaderValues.create(HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "x-request-id"));
        }
        default -> throw new IllegalArgumentException("Unknown header form: " + headerForm);
        }
        ServerRequestHeaders requestHeaders = ServerRequestHeaders.create(headers);
        RoutedPath path = new BenchmarkPath();
        request = (ServerRequest) Proxy.newProxyInstance(ServerRequest.class.getClassLoader(),
                                                        new Class<?>[] {ServerRequest.class},
                                                        (proxy, method, args) -> switch (method.getName()) {
                                                        case "headers" -> requestHeaders;
                                                        case "path" -> path;
                                                        case "serverSocketId", "socketId" -> "benchmark";
                                                        default -> throw new UnsupportedOperationException(method.getName());
                                                        });
        response = (ServerResponse) Proxy.newProxyInstance(ServerResponse.class.getClassLoader(),
                                                          new Class<?>[] {ServerResponse.class},
                                                          (proxy, method, args) -> switch (method.getName()) {
                                                          case "headers" -> responseHeaders;
                                                          case "status" -> {
                                                              status = (Status) args[0];
                                                              yield proxy;
                                                          }
                                                          case "send" -> null;
                                                          default -> throw new UnsupportedOperationException(method.getName());
                                                          });
        responseHeaders = ServerResponseHeaders.create();
        status = Status.OK_200;
        CorsPathValidator.Result result = validator.preFlight(request, response);
        if (!result.matched()
                || result.shouldContinue() != expectedAllowed
                || status != (expectedAllowed ? Status.OK_200 : Status.FORBIDDEN_403)) {
            throw new IllegalStateException("Unexpected preflight result for " + headerForm + ": " + result + ", " + status);
        }
    }

    @Benchmark
    public boolean preFlight(Blackhole blackhole) {
        responseHeaders = ServerResponseHeaders.create();
        status = Status.OK_200;
        CorsPathValidator.Result result = validator.preFlight(request, response);
        blackhole.consume(responseHeaders);
        return result.shouldContinue();
    }

    private static class BenchmarkPath implements RoutedPath {
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
            return "/greet";
        }

        @Override
        public String rawPathNoParams() {
            return "/greet";
        }

        @Override
        public String path() {
            return "/greet";
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
