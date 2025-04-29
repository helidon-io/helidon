/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.strings.Strings;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Shows interception of {@link io.helidon.webserver.grpc.GrpcService} methods.
 * Should take into account any {@link io.helidon.common.Weight}s associated
 * with the interceptors.
 */
@ServerTest
class GrpcServiceInterceptorTest extends BaseInterceptorTest {

    static Interceptor1 INTERCEPTOR1 = new Interceptor1();
    static Interceptor2 INTERCEPTOR2 = new Interceptor2();
    static Interceptor3 INTERCEPTOR3 = new Interceptor3();

    GrpcServiceInterceptorTest(WebServer server) {
        super(server);
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder()
                                  .intercept(INTERCEPTOR3, INTERCEPTOR1, INTERCEPTOR2)
                                  .service(new StringService()));
    }

    @Test
    public void checkInterceptors() {
        Strings.StringMessage request = Strings.StringMessage.newBuilder().setText("FOO").build();
        Strings.StringMessage res = stub.lower(request);
        assertThat(res.getText(), is("foo"));
        assertThat(INTERCEPTORS.size(), is(3));
        assertThat(INTERCEPTORS.get(0), is(INTERCEPTOR1));
        assertThat(INTERCEPTORS.get(1), is(INTERCEPTOR2));
        assertThat(INTERCEPTORS.get(2), is(INTERCEPTOR3));
    }
}
