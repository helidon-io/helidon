/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc.tests;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.helidon.common.Weight;
import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Tests client interceptors using low-level API.
 */
@ServerTest
class GrpcInterceptorTest extends GrpcBaseTest {

    private final GrpcClient grpcClient;
    private final GrpcServiceDescriptor serviceDescriptor;
    private final List<Class<?>> calledInterceptors = new CopyOnWriteArrayList<>();

    private GrpcInterceptorTest(WebServer server) {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        this.grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .baseUri("https://localhost:" + server.port())
                .build();
        this.serviceDescriptor = GrpcServiceDescriptor.builder()
                .serviceName("StringService")
                .putMethod("Upper",
                        GrpcClientMethodDescriptor.unary("StringService", "Upper")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .intercept(new Weight50Interceptor())
                                .intercept(new Weight500Interceptor())
                                .build())
                .addInterceptor(new Weight100Interceptor())
                .addInterceptor(new Weight1000Interceptor())
                .addInterceptor(new Weight10Interceptor())
                .build();
    }

    @Test
    void testUnaryUpper() {
        Strings.StringMessage res = grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper", newStringMessage("hello"));
        assertThat(res.getText(), is("HELLO"));
        assertThat(calledInterceptors, contains(Weight1000Interceptor.class,
                                                Weight500Interceptor.class,
                                                Weight100Interceptor.class,
                                                Weight50Interceptor.class,
                                                Weight10Interceptor.class));
    }

    class BaseInterceptor implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                   CallOptions callOptions,
                                                                   Channel next) {
            calledInterceptors.add(getClass());
            return next.newCall(method, callOptions);
        }
    }

    @Weight(10.0)
    class Weight10Interceptor extends BaseInterceptor {
    }

    @Weight(50.0)
    class Weight50Interceptor extends BaseInterceptor {
    }

    @Weight(100.0)
    class Weight100Interceptor extends BaseInterceptor {
    }

    @Weight(500.0)
    class Weight500Interceptor extends BaseInterceptor {
    }

    @Weight(1000.0)
    class Weight1000Interceptor extends BaseInterceptor {
    }
}
