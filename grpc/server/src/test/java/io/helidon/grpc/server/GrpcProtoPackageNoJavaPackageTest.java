/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.grpc.server;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import io.grpc.util.MutableHandlerRegistry;
import io.helidon.grpc.core.PriorityBag;
import helidon.test.Echopackagenojavapackage;
import helidon.test.EchoPackageNoJavaPackageServiceGrpc;
import org.junit.jupiter.api.Test;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for gRPC server support for package directive in proto file
 */
@SuppressWarnings("unchecked")
public class GrpcProtoPackageNoJavaPackageTest {
    final static String PACKAGE_NAME = "helidon.test";

    @Test
    public void shouldContainPackage() {
        Descriptors.FileDescriptor protoDescriptor = Echopackagenojavapackage.getDescriptor();
        String packageName = protoDescriptor.getPackage();
        assertThat(packageName.isBlank(), is(false));
        assertThat(protoDescriptor.getPackage(), is(PACKAGE_NAME));

        BindableService service = new EchoPackageStub();
        ServiceDescriptor descriptor = ServiceDescriptor.builder(service)
                .name("Foo")
                .build();
        assertThat(protoDescriptor.getPackage(), is(descriptor.packageName()));
    }

    @Test
    public void shouldHaveNoJavaPackage() {
        Descriptors.FileDescriptor protoDescriptor = Echopackagenojavapackage.getDescriptor();
        String javaPackageName = protoDescriptor.getOptions().getJavaPackage();
        assertThat(javaPackageName.isBlank(), is(true));
    }

    @Test
    public void shouldCreateMethodDescriptor() {
        ServerCallHandler handler = mock(ServerCallHandler.class);
        String fullServiceName = getServiceFullName(PACKAGE_NAME, "EchoPackageNoJavaPackageService");
        String fullMethodName = io.grpc.MethodDescriptor.generateFullMethodName(fullServiceName, "Echo");
        io.grpc.MethodDescriptor grpcDescriptor = EchoPackageNoJavaPackageServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals(fullMethodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        MethodDescriptor<?, ?> descriptor =
                MethodDescriptor.create(fullServiceName,"foo", grpcDescriptor.toBuilder(), handler);

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.callHandler(), is(sameInstance(handler)));
        assertThat(descriptor.context(), is(notNullValue()));
        assertThat(descriptor.context().size(), is(0));

        io.grpc.MethodDescriptor methodDescriptor = descriptor.descriptor();
        assertThat(methodDescriptor.getFullMethodName(), is(io.grpc.MethodDescriptor.generateFullMethodName(fullServiceName, "foo")));
    }

    @Test
    public void shouldBuildFromBindableService() {
        BindableService service = new EchoPackageStub();
        ServerServiceDefinition definition = service.bindService();
        io.grpc.ServiceDescriptor grpcDescriptor = definition.getServiceDescriptor();

        ServiceDescriptor descriptor = ServiceDescriptor.builder(service).build();

        assertThat(descriptor.fullName(), is(grpcDescriptor.getName()));

        BindableService bindableService = descriptor.bindableService(PriorityBag.create());
        assertThat(bindableService, is(notNullValue()));

        ServerServiceDefinition ssd = bindableService.bindService();
        assertThat(ssd, is(notNullValue()));

        io.grpc.ServiceDescriptor actualDescriptor = ssd.getServiceDescriptor();
        assertThat(actualDescriptor, is(notNullValue()));
        assertThat(actualDescriptor.getName(), is(grpcDescriptor.getName()));

        Map<String, io.grpc.MethodDescriptor<?, ?>> methods = grpcDescriptor.getMethods()
                .stream()
                .collect(Collectors.toMap(io.grpc.MethodDescriptor::getFullMethodName, m -> m));

        Collection<io.grpc.MethodDescriptor<?, ?>> methodsActual = actualDescriptor.getMethods();

        for (io.grpc.MethodDescriptor<?, ?> method : methodsActual) {
            assertThat(method.toString(), is(methods.get(method.getFullMethodName()).toString()));
        }
    }

    @Test
    public void shouldOverrideServiceName() {
        BindableService service = new EchoPackageStub();

        Descriptors.FileDescriptor protoDescriptor = Echopackagenojavapackage.getDescriptor();
        ServiceDescriptor descriptor = ServiceDescriptor.builder(service)
                .name("Foo")
                .proto(protoDescriptor)
                .build();

        assertThat(descriptor.name(), is("Foo"));

        BindableService bindableService = descriptor.bindableService(PriorityBag.create());
        assertThat(bindableService, is(notNullValue()));

        ServerServiceDefinition ssd = bindableService.bindService();
        assertThat(ssd, is(notNullValue()));

        io.grpc.ServiceDescriptor actualDescriptor = ssd.getServiceDescriptor();
        assertThat(actualDescriptor, is(notNullValue()));
        String fullServiceName = getServiceFullName(PACKAGE_NAME, "Foo");
        assertThat(actualDescriptor.getName(), is(fullServiceName));

        Collection<io.grpc.MethodDescriptor<?, ?>> methods = actualDescriptor.getMethods();

        for (io.grpc.MethodDescriptor<?, ?> method : methods) {
            assertThat(method.getFullMethodName().startsWith(fullServiceName + "/"), is(true));
        }
    }

    @Test
    public void shouldBeTheSameServiceDescriptors() {
        BindableService service = new EchoPackageStub();

        ServiceDescriptor descriptor1 = ServiceDescriptor.builder(service)
                .name("Foo")
                .build();

        ServiceDescriptor descriptor2 = ServiceDescriptor.builder(service)
                .name("Foo")
                .build();
        assertThat(descriptor1.equals(descriptor2), is(true));
        assertThat(descriptor1.hashCode(), is(descriptor2.hashCode()));
        assertThat(descriptor1.toString().contains(descriptor2.fullName()), is(true));
        assertThat(descriptor1.toString(), is(descriptor2.toString()));
    }

    @Test
    public void shouldBuildFromProtoFile() {
        ServiceDescriptor descriptor = createServiceDescriptor();

        assertThat(descriptor.name(), is("EchoPackageNoJavaPackageService"));

        BindableService bindableService = descriptor.bindableService(PriorityBag.create());
        assertThat(bindableService, is(notNullValue()));

        ServerServiceDefinition ssd = bindableService.bindService();
        assertThat(ssd, is(notNullValue()));

        io.grpc.ServiceDescriptor serviceDescriptor = ssd.getServiceDescriptor();
        assertThat(serviceDescriptor, is(notNullValue()));
        String fullServiceName = getServiceFullName(PACKAGE_NAME, "EchoPackageNoJavaPackageService");
        assertThat(serviceDescriptor.getName(), is(fullServiceName));
    }

    /**
     * Ensure that io.grpc.MethodDescriptor service name is same as io.grpc.ServiceDescriptor service name as this
     * is being validated by the grpc-java library.
     */
    @Test
    public void shouldHaveSimilarServiceNamesForServiceAndMethodDescriptors() {
        ServiceDescriptor descriptor = createServiceDescriptor();
        BindableService bindableService = descriptor.bindableService(PriorityBag.create());
        ServerServiceDefinition ssd = bindableService.bindService();
        io.grpc.ServiceDescriptor serviceDescriptor = ssd.getServiceDescriptor();

        io.grpc.MethodDescriptor methodDescriptor = serviceDescriptor.getMethods().stream().findFirst().get();
        assertThat(methodDescriptor.getServiceName(), is(serviceDescriptor.getName()));
    }

    @Test
    public void shouldLookupFromHandlerRegistry() {
        ServiceDescriptor descriptor = createServiceDescriptor();
        BindableService bindableService = descriptor.bindableService(PriorityBag.create());
        ServerServiceDefinition ssd = bindableService.bindService();

        MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();
        handlerRegistry.addService(ssd);
        String fullMethodName = io.grpc.MethodDescriptor.generateFullMethodName(
                getServiceFullName(PACKAGE_NAME, "EchoPackageNoJavaPackageService"), "Echo");
        System.out.println("Full Method Name=" + fullMethodName);
        assertThat(handlerRegistry.lookupMethod(fullMethodName).getMethodDescriptor().getFullMethodName(), is(fullMethodName));
    }

    @Test
    public void shouldCompleteGrpcRequest() throws Exception {
        // Add the EchoService
        GrpcRouting routing = GrpcRouting.builder()
                .register(new EchoPackageService())
                .build();

        // Run the server on port 0 so that it picks a free ephemeral port
        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder()
                .port(0)
                .build();

        GrpcServer grpcServer = GrpcServer.create(serverConfig, routing)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        Channel channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port())
                .usePlaintext()
                .build();

        Echopackagenojavapackage.EchoPackageNoJavaPackageResponse response = EchoPackageNoJavaPackageServiceGrpc.newBlockingStub(channel)
                .echo(Echopackagenojavapackage.EchoPackageNoJavaPackageRequest.newBuilder().setMessage("foo").build());

        assertThat(response.getMessage(), is("foo"));
        grpcServer.shutdown();
    }

    private String getServiceFullName(String packageName, String serviceName) {
        return  packageName + "." + serviceName;
    }

    private ServiceDescriptor createServiceDescriptor() {
        GrpcService service = mock(GrpcService.class);

        when(service.name()).thenReturn("EchoPackageNoJavaPackageService");

        Descriptors.FileDescriptor protoDescriptor = Echopackagenojavapackage.getDescriptor();

        ServiceDescriptor descriptor = ServiceDescriptor.builder(service)
                .proto(protoDescriptor)
                .unary("Echo", this::dummyUnary)
                .build();
        return descriptor;
    }

    private void dummyUnary(String request, StreamObserver<String> observer) {
    }

    private class EchoPackageStub
            extends EchoPackageNoJavaPackageServiceGrpc.EchoPackageNoJavaPackageServiceImplBase {
    }

    public static class EchoPackageService
            implements GrpcService {

        @Override
        public void update(ServiceDescriptor.Rules rules) {
            rules.proto(Echopackagenojavapackage.getDescriptor())
                 .name("EchoPackageNoJavaPackageService")
                 .unary("Echo", this::echo);
        }

        public void echo(Echopackagenojavapackage.EchoPackageNoJavaPackageRequest request, StreamObserver<Echopackagenojavapackage.EchoPackageNoJavaPackageResponse> observer)  {
            String message = request.getMessage();
            Echopackagenojavapackage.EchoPackageNoJavaPackageResponse response = Echopackagenojavapackage.EchoPackageNoJavaPackageResponse.newBuilder().setMessage(message).build();
            complete(observer, response);
        }
    }
}
