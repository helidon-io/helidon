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

import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.grpc.core.ContextKeys;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.strings.Strings;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ServerContextInterceptorTest extends BaseInterceptorTest {

    static AtomicBoolean CONTEXT_FOUND = new AtomicBoolean(false);

    ServerContextInterceptorTest(WebServer server) {
        super(server);
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder()
                                  .intercept(new CheckContextInterceptor())
                                  .service(Strings.getDescriptor(), new BindableStringService()));
    }

    @Test
    public void checkInterceptors() {
        Strings.StringMessage request = Strings.StringMessage.newBuilder().setText("FOO").build();
        Strings.StringMessage res = stub.lower(request);
        assertThat(res.getText(), is("foo"));
        assertThat(CONTEXT_FOUND.get(), is(true));
    }

    static class CheckContextInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            io.helidon.common.context.Context helidonContext = ContextKeys.HELIDON_CONTEXT.get();
            CONTEXT_FOUND.set(helidonContext != null);
            return next.startCall(call, headers);
        }
    }
}
