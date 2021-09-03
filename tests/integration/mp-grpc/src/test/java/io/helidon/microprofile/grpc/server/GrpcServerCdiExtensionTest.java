/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.server;

import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;

import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.microprofile.grpc.core.InProcessGrpcChannel;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.Unary;
import io.helidon.microprofile.grpc.server.spi.GrpcMpContext;
import io.helidon.microprofile.grpc.server.spi.GrpcMpExtension;
import io.helidon.microprofile.grpc.server.test.ServerStreamingServiceGrpc;
import io.helidon.microprofile.grpc.server.test.Services.TestRequest;
import io.helidon.microprofile.grpc.server.test.Services.TestResponse;
import io.helidon.microprofile.server.Server;

import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.hamcrest.CoreMatchers;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test to verify that {@link GrpcServerCdiExtension} starts the gRPC server.
 */
public class GrpcServerCdiExtensionTest {

    private static Server server;

    private static BeanManager beanManager;

    @BeforeAll
    public static void startServer() {
        server = Server.create().start();
        beanManager = CDI.current().getBeanManager();
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void shouldStartServerAndLoadServicesAndExtensions() {
        Instance<GrpcServerCdiExtension.ServerProducer> instance = beanManager.createInstance()
                .select(GrpcServerCdiExtension.ServerProducer.class);

        // verify that the GrpcServerCdiExtension.ServerProducer producer bean is registered
        assertThat(instance.isResolvable(), is(true));

        // obtain the started server from the producer
        GrpcServer grpcServer = instance.get().server();
        assertThat(grpcServer, is(CoreMatchers.notNullValue()));
        assertThat(grpcServer.isRunning(), is(true));

        // verify that the services are deployed
        Map<String, ServiceDescriptor> services = grpcServer.services();
        // UnaryService should have been discovered by CDI
        assertThat(services.get("UnaryService"), is(CoreMatchers.notNullValue()));
        // ServerStreamingService loaded by ExtensionOne discovered by CDI
        assertThat(services.get("ServerStreamingService"), is(CoreMatchers.notNullValue()));
        // TestService loaded by ExtensionTwo loaded by the ServiceLoader
        assertThat(services.get("TestService"), is(CoreMatchers.notNullValue()));
    }

    @Test
    public void shouldInjectGrpcServer() {
        Instance<TestBean> instance = beanManager.createInstance().select(TestBean.class);
        assertThat(instance.isResolvable(), is(true));

        TestBean bean = instance.get();
        // strangely if we try to access the server field directly it will be null!
        assertThat(bean.server(), is(CoreMatchers.notNullValue()));
    }

    @Test
    public void shouldInjectInProcessChannel() {
        Instance<TestBean> instance = beanManager.createInstance().select(TestBean.class);
        assertThat(instance.isResolvable(), is(true));

        TestBean bean = instance.get();
        // strangely if we try to access the channel field directly it will be null!
        assertThat(bean.channel(), is(CoreMatchers.notNullValue()));
    }

    @Test
    public void shouldInjectInProcessChannelBuilder() {
        Instance<TestBean> instance = beanManager.createInstance().select(TestBean.class);
        assertThat(instance.isResolvable(), is(true));

        TestBean bean = instance.get();
        // strangely if we try to access the builder field directly it will be null!
        assertThat(bean.builder(), is(CoreMatchers.notNullValue()));
    }


    /**
     * A test bean that should have various gRPC beans injected.
     */
    @ApplicationScoped
    public static class TestBean {
        @Inject
        private GrpcServer server;

        @Inject
        @InProcessGrpcChannel
        private Channel channel;

        @Inject
        @InProcessGrpcChannel
        private InProcessChannelBuilder builder;

        public GrpcServer server() {
            return server;
        }

        public Channel channel() {
            return channel;
        }

        public InProcessChannelBuilder builder() {
            return builder;
        }
    }

    /**
     * A gRPC MP extension that is discovered by CDI.
     */
    @ApplicationScoped
    public static class ExtensionOne
            implements GrpcMpExtension {
        @Override
        public void configure(GrpcMpContext context) {
            context.routing()
                    .register(new ServerStreamingService());
        }
    }
    /**
     * A gRPC MP extension that is discovered by the ServiceLoader.
     */
    public static class ExtensionTwo
            implements GrpcMpExtension {
        @Override
        public void configure(GrpcMpContext context) {
            context.routing()
                    .register(new TestService());
        }
    }

    /**
     * Annotated gRPC service bean that should be discovered and deployed.
     */
    @ApplicationScoped
    @Grpc
    public static class UnaryService {
        @Unary
        public TestResponse requestResponse(TestRequest request) {
            return TestResponse.newBuilder().setMessage(request.getMessage()).build();
        }
    }

    /**
     * A protocol buffer generated service implementation
     * manually deployed by {@link ExtensionOne}.
     */
    public static class ServerStreamingService
            extends ServerStreamingServiceGrpc.ServerStreamingServiceImplBase {
        @Override
        public void streaming(TestRequest request, StreamObserver<TestResponse> observer) {
            TestResponse response = TestResponse.newBuilder().setMessage(request.getMessage()).build();
            observer.onNext(response);
            observer.onCompleted();
        }
    }

    /**
     * A POJO protocol buffer generated service implementation
     * manually deployed by {@link ExtensionTwo}.
     */
    @Grpc
    public static class TestService
            implements GrpcService {

        @Override
        public void update(ServiceDescriptor.Rules rules) {
            rules.unary("unary", this::unary);
        }

        void unary(String request, StreamObserver<String> observer) {
            observer.onNext(request);
            observer.onCompleted();
        }
    }
}
