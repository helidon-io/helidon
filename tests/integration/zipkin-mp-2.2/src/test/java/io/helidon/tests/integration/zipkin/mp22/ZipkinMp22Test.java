/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.zipkin.mp22;

import java.net.URI;

import io.helidon.microprofile.server.Server;

import io.opentracing.ScopeManager;
import io.opentracing.util.GlobalTracer;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link ZipkinMp22Main}.
 */
class ZipkinMp22Test {
    private static Server server;
    private static int port;

    @BeforeAll
    static void initClass() {
        server = Server.create().start();
        port = server.port();
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    void testZipkin() {
        // global tracer wraps the tracer in its own instance
        ScopeManager scopeManager = GlobalTracer.get().scopeManager();
        // and this class is not public
        assertThat(scopeManager.getClass().getName(), startsWith("io.helidon.tracing.zipkin"));
    }

    @Test
    void invokeEndpoint() {
        MpResource client = RestClientBuilder.newBuilder()
                .baseUri(URI.create("http://localhost:" + port))
                .build(MpResource.class);

        String result = client.helloWorld();
        assertThat(result, is("Hello World"));
    }
}
