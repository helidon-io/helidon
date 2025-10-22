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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.context.Context;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.grpc.strings.StringServiceGrpc;
import io.helidon.webserver.grpc.strings.Strings;
import io.helidon.webserver.grpc.strings.Strings.StringMessage;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import com.google.protobuf.Descriptors;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class TracingServiceTest extends BaseServiceTest {

    static final AtomicBoolean TRACED = new AtomicBoolean();

    protected StringServiceGrpc.StringServiceBlockingStub blockingStub;
    protected StringServiceGrpc.StringServiceStub stub;

    TracingServiceTest(WebServer server) {
        super(server);
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new TracedStringService()));
    }

    @BeforeEach
    void beforeEach() {
        super.beforeEach();
        blockingStub = StringServiceGrpc.newBlockingStub(channel);
        stub = StringServiceGrpc.newStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        super.afterEach();
        blockingStub = null;
        stub = null;
    }

    @Test
    void testUnaryUpper() {
        String text = "lower case original";
        StringMessage request = StringMessage.newBuilder().setText(text).build();
        StringMessage response = blockingStub.upper(request);
        assertThat(response.getText(), is(text.toUpperCase(Locale.ROOT)));
        assertThat(TRACED.get(), is(true));     // confirms tracing interceptor there
    }

    static class TracedStringService implements GrpcService {
        @Override
        public Descriptors.FileDescriptor proto() {
            return Strings.getDescriptor();
        }

        @Override
        public String serviceName() {
            return "StringService";
        }

        @Override
        public void update(Routing router) {
            router.unary("Upper", this::grpcUnaryUpper);
        }

        private void grpcUnaryUpper(StringMessage request, StreamObserver<StringMessage> observer) {
            // get Helidon context from gRPC's
            io.grpc.Context grpcContext = io.grpc.Context.current();
            Context helidonContext = ContextKeys.HELIDON_CONTEXT.get(grpcContext);

            // tracing interceptor adds Tracer to Helidon context
            TRACED.set(helidonContext.get(Tracer.class).isPresent());

            // respond to call
            String requestText = request.getText();
            complete(observer, StringMessage.newBuilder()
                    .setText(requestText.toUpperCase(Locale.ROOT))
                    .build());
        }
    }
}
