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

package io.helidon.webserver.grpc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.common.Builder;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.spi.GrpcServerService;
import io.helidon.webserver.grpc.spi.GrpcServerServiceProvider;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ServerFeature;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class GrpcRoutingRegistryTest {
    @Test
    void configuredServiceUsesOwningRegistryProvider() {
        TestProvider provider = new TestProvider();
        ServiceRegistryManager manager = registry(provider);
        try {
            ServiceRegistry serviceRegistry = manager.registry();
            Config config = routingConfig(Map.of("grpc.grpc-services.owner.enabled", "true",
                                                 "grpc.grpc-services-discover-services", "false"));

            GrpcRouting.builder()
                    .config(config)
                    .serviceRegistry(serviceRegistry)
                    .build();

            assertThat(provider.createCount, is(1));
            assertThat(provider.name, is("owner"));
            assertThat(provider.serviceRegistry, sameInstance(serviceRegistry));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void discoveredServiceUsesOwningRegistryProvider() {
        TestProvider provider = new TestProvider();
        ServiceRegistryManager manager = registry(provider);
        try {
            ServiceRegistry serviceRegistry = manager.registry();
            Config config = routingConfig(Map.of("grpc.marker", "present"));

            GrpcRouting.builder()
                    .config(config)
                    .serviceRegistry(serviceRegistry)
                    .build();

            assertThat(provider.createCount, is(1));
            assertThat(provider.name, is("owner"));
            assertThat(provider.serviceRegistry, sameInstance(serviceRegistry));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void serverFeatureConfiguresExistingRoutingBuilder() throws Descriptors.DescriptorValidationException {
        TestProvider provider = new TestProvider();
        ServiceRegistryManager manager = registry(provider);
        try {
            ServiceRegistry serviceRegistry = manager.registry();
            Config rootConfig = Config.just(ConfigSources.create(Map.of("grpc.grpc-services.owner.enabled", "true",
                                                                        "grpc.grpc-services-discover-services", "false")));
            GrpcRouting.Builder routingBuilder = GrpcRouting.builder()
                    .config(rootConfig.get("grpc"));
            AtomicBoolean meterRegistryRequested = new AtomicBoolean();
            GrpcServiceDescriptor descriptor = descriptor("feature");
            GrpcServerFeature feature = new GrpcServerFeature(rootConfig,
                                                              serviceRegistry,
                                                              () -> {
                                                                  meterRegistryRequested.set(true);
                                                                  return null;
                                                              },
                                                              () -> List.of(() -> descriptor));

            feature.setup(new TestFeatureContext(routingBuilder));
            GrpcRouting routing = routingBuilder.build();
            routing.meterRegistry();

            assertThat(provider.createCount, is(1));
            assertThat(provider.serviceRegistry, sameInstance(serviceRegistry));
            assertThat(meterRegistryRequested.get(), is(true));
        } finally {
            manager.shutdown();
        }
    }

    private static Config routingConfig(Map<String, String> values) {
        return Config.just(ConfigSources.create(values)).get("grpc");
    }

    private static ServiceRegistryManager registry(GrpcServerServiceProvider provider) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .discoverServices(false)
                                                     .discoverServicesFromServiceLoader(false)
                                                     .putContractInstance(GrpcServerServiceProvider.class, provider)
                                                     .build());
    }

    private static GrpcServiceDescriptor descriptor(String protoPackage)
            throws Descriptors.DescriptorValidationException {
        return GrpcServiceDescriptor.builder(GrpcRoutingRegistryTest.class, protoPackage + ".Greeter")
                .proto(proto(protoPackage))
                .build();
    }

    private static Descriptors.FileDescriptor proto(String protoPackage)
            throws Descriptors.DescriptorValidationException {
        DescriptorProtos.DescriptorProto request = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("GreetingRequest")
                .build();
        DescriptorProtos.DescriptorProto reply = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("GreetingReply")
                .build();
        DescriptorProtos.MethodDescriptorProto method = DescriptorProtos.MethodDescriptorProto.newBuilder()
                .setName("SayHello")
                .setInputType("." + protoPackage + ".GreetingRequest")
                .setOutputType("." + protoPackage + ".GreetingReply")
                .build();
        DescriptorProtos.ServiceDescriptorProto service = DescriptorProtos.ServiceDescriptorProto.newBuilder()
                .setName("Greeter")
                .addMethod(method)
                .build();
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(protoPackage + "_greeter.proto")
                .setPackage(protoPackage)
                .addMessageType(request)
                .addMessageType(reply)
                .addService(service)
                .build();

        return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
    }

    private static final class TestFeatureContext implements ServerFeature.ServerFeatureContext {
        private final ServerFeature.SocketBuilders socketBuilders;

        private TestFeatureContext(GrpcRouting.Builder routingBuilder) {
            this.socketBuilders = new TestSocketBuilders(routingBuilder);
        }

        @Override
        public WebServerConfig serverConfig() {
            return null;
        }

        @Override
        public Set<String> sockets() {
            return Set.of();
        }

        @Override
        public boolean socketExists(String socketName) {
            return true;
        }

        @Override
        public ServerFeature.SocketBuilders socket(String socketName) {
            return socketBuilders;
        }
    }

    private record TestSocketBuilders(ServerFeature.RoutingBuilders routingBuilders)
            implements ServerFeature.SocketBuilders {
        private TestSocketBuilders(GrpcRouting.Builder routingBuilder) {
            this(new TestRoutingBuilders(routingBuilder));
        }

        @Override
        public ListenerConfig listener() {
            return null;
        }

        @Override
        public HttpRouting.Builder httpRouting() {
            return null;
        }
    }

    private record TestRoutingBuilders(GrpcRouting.Builder routingBuilder)
            implements ServerFeature.RoutingBuilders {
        @Override
        public boolean hasRouting(Class<?> builderType) {
            return builderType == GrpcRouting.Builder.class;
        }

        @Override
        public <T extends Builder<T, ?>> T routingBuilder(Class<T> builderType) {
            return builderType.cast(routingBuilder);
        }

        @Override
        public <T extends Builder<T, ?>> T routingBuilder(Class<T> builderType, Supplier<T> builderSupplier) {
            return routingBuilder(builderType);
        }
    }

    private static final class TestProvider implements GrpcServerServiceProvider {
        private ServiceRegistry serviceRegistry;
        private String name;
        private int createCount;

        @Override
        public String configKey() {
            return "owner";
        }

        @Override
        public GrpcServerService create(Config config, String name) {
            throw new AssertionError("Managed service creation must use the owning registry");
        }

        @Override
        public GrpcServerService create(Config config, String name, ServiceRegistry serviceRegistry) {
            this.serviceRegistry = serviceRegistry;
            this.name = name;
            createCount++;
            return new TestService(name);
        }
    }

    private record TestService(String name) implements GrpcServerService {
        @Override
        public String type() {
            return "owner";
        }

        @Override
        public WeightedBag<ServerInterceptor> interceptors() {
            return WeightedBag.create();
        }
    }
}
