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

package io.helidon.declarative.codegen.http.webserver;

import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.mapper.Mappers;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.types.Annotation;
import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.http.media.ReadableEntity;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.RestServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class CookieParamCodegenTest {
    @Test
    void invalidCookieParameterNameIsRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
                        Dependency.class,
                        Generated.class,
                        GenericType.class,
                        Handler.class,
                        Http.class,
                        HttpEntryPoint.class,
                        HttpFeature.class,
                        HttpRoute.class,
                        HttpRouting.class,
                        HttpRules.class,
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-invalid-cookie-param-name"))
                .addSource("CookieEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/cookies")
                        class CookieEndpoint {
                            @Http.GET
                            String invalid(@Http.CookieParam("bad;name") String value) {
                                return value;
                            }
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("@Http.CookieParam value must be a valid cookie name."));
    }

    @Test
    void invalidRequestParamsCookieParameterNameIsRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
                        Dependency.class,
                        Generated.class,
                        GenericType.class,
                        Handler.class,
                        Http.class,
                        HttpEntryPoint.class,
                        HttpFeature.class,
                        HttpRoute.class,
                        HttpRouting.class,
                        HttpRules.class,
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-invalid-cookie-param-name"))
                .addSource("CookieParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record CookieParams(@Http.CookieParam("bad;name") String value) {
                        }
                        """)
                .addSource("CookieEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/cookies")
                        class CookieEndpoint {
                            @Http.GET
                            String invalid(@Http.RequestParams CookieParams params) {
                                return params.value();
                            }
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("@Http.CookieParam value must be a valid cookie name."));
    }
}
