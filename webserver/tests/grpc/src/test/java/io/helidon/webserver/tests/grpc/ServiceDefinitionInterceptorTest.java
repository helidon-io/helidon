/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.grpc;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.strings.Strings;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Interceptor test that creates a {@link io.grpc.ServerServiceDefinition} and
 * intercepts its calls. Because the interception is done by non-Helidon code,
 * the {@link io.helidon.common.Weight} annotations are ignored and ordering
 * cannot be guaranteed.
 */
@ServerTest
class ServiceDefinitionInterceptorTest extends BaseInterceptorTest {

    static Interceptor1 INTERCEPTOR1 = new Interceptor1();
    static Interceptor2 INTERCEPTOR2 = new Interceptor2();
    static Interceptor3 INTERCEPTOR3 = new Interceptor3();

    ServiceDefinitionInterceptorTest(WebServer server) {
        super(server);
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        // using ServerInterceptors.intercept() the @Weight annotations are ignored
        ServerServiceDefinition definition = ServerInterceptors.intercept(new BindableStringService(),
                                                                          INTERCEPTOR1, INTERCEPTOR3);
        router.addRouting(GrpcRouting.builder()
                                  .intercept(INTERCEPTOR2)      // proper way to add interceptors
                                  .service(definition));
    }

    @Test
    public void checkInterceptors() {
        Strings.StringMessage request = Strings.StringMessage.newBuilder().setText("FOO").build();
        Strings.StringMessage res = stub.lower(request);
        assertThat(res.getText(), is("foo"));
        assertThat(INTERCEPTORS, hasItems(INTERCEPTOR1, INTERCEPTOR2, INTERCEPTOR3));     // cannot guarantee order
    }
}
