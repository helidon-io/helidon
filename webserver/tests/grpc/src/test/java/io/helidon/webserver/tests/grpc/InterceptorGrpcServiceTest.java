/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.webserver.grpc.strings.Strings;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class InterceptorGrpcServiceTest
    extends BaseStringServiceTest {

    private static Interceptor interceptor;

    InterceptorGrpcServiceTest(WebServer server) {
        super(server);
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        interceptor = new Interceptor();
        ServerServiceDefinition definition = ServerInterceptors.intercept(new BindableStringService(), interceptor);
        router.addRouting(GrpcRouting.builder().service(definition));
    }

    @Test
    public void shouldInterceptCalls() {
        interceptor.setIntercepted(false);
        Strings.StringMessage request = Strings.StringMessage.newBuilder().setText("FOO").build();
        blockingStub.lower(request);

        assertThat(interceptor.wasIntercepted(), is(true));
    }

    public static class Interceptor
            implements ServerInterceptor {

        private boolean intercepted;

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            intercepted = true;
            return next.startCall(call, headers);
        }

        public boolean wasIntercepted() {
            return intercepted;
        }

        public void setIntercepted(boolean intercepted) {
            this.intercepted = intercepted;
        }
    }

}
