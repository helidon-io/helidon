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

package io.helidon.grpc.server;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.grpc.core.PriorityBag;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ContextSettingServerInterceptorRaceTest {
    private static final String METHOD = "invoke";
    private static final Context.Key<String> SERVICE_MARKER = Context.key("service-marker");

    @Test
    void concurrentServicesKeepTheirOwnContext() throws Exception {
        AtomicReference<String> adminMarker = new AtomicReference<>();
        AtomicReference<ServiceDescriptor> adminDescriptor = new AtomicReference<>();
        ServiceDescriptor adminService = descriptor("AdminService", "admin", adminMarker, adminDescriptor);
        ServiceDescriptor publicService = descriptor("PublicService", "public", new AtomicReference<>(),
                                                     new AtomicReference<>());
        PriorityBag<ServerInterceptor> interceptors = PriorityBag.create();
        interceptors.add(ContextSettingServerInterceptor.create());

        ServerCallHandler<String, String> adminHandler = handlerFor(adminService, interceptors);
        ServerCallHandler<String, String> publicHandler = handlerFor(publicService, interceptors);
        CountDownLatch adminPaused = new CountDownLatch(1);
        CountDownLatch releaseAdmin = new CountDownLatch(1);
        AtomicReference<Throwable> adminFailure = new AtomicReference<>();
        ServerCall<String, String> adminCall = mock(ServerCall.class);
        when(adminCall.getMethodDescriptor()).thenAnswer(invocation -> {
            adminPaused.countDown();
            assertThat(releaseAdmin.await(5, TimeUnit.SECONDS), is(true));
            return adminService.method(METHOD).descriptor();
        });

        Thread adminThread = new Thread(() -> invoke(adminHandler, adminCall, adminFailure));
        adminThread.start();
        assertThat(adminPaused.await(5, TimeUnit.SECONDS), is(true));

        ServerCall<String, String> publicCall = mock(ServerCall.class);
        when(publicCall.getMethodDescriptor()).thenReturn(publicService.method(METHOD).descriptor());
        publicHandler.startCall(publicCall, new Metadata());
        releaseAdmin.countDown();
        adminThread.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(adminThread.isAlive(), is(false));
        assertThat(adminFailure.get(), is((Throwable) null));
        assertThat(adminMarker.get(), is("admin"));
        assertThat(adminDescriptor.get(), is(adminService));
    }

    private static ServiceDescriptor descriptor(String name,
                                                String marker,
                                                AtomicReference<String> markerSeen,
                                                AtomicReference<ServiceDescriptor> descriptorSeen) {
        return ServiceDescriptor.builder(RaceService.class, name)
                .marshallerSupplier(new StubMarshaller.Supplier())
                .addContextValue(SERVICE_MARKER, marker)
                .unary(METHOD, (String request, StreamObserver<String> observer) -> {
                    markerSeen.set(SERVICE_MARKER.get());
                    descriptorSeen.set(ServiceDescriptor.SERVICE_DESCRIPTOR_KEY.get());
                    observer.onNext("ok");
                    observer.onCompleted();
                }, rules -> rules.requestType(String.class).responseType(String.class))
                .build();
    }

    private static ServerCallHandler<String, String> handlerFor(ServiceDescriptor descriptor,
                                                                  PriorityBag<ServerInterceptor> interceptors) {
        ServerServiceDefinition definition = descriptor.bindableService(interceptors).bindService();
        ServerMethodDefinition<String, String> method =
                (ServerMethodDefinition<String, String>) definition.getMethod(descriptor.name() + "/" + METHOD);
        return method.getServerCallHandler();
    }

    private static void invoke(ServerCallHandler<String, String> handler,
                               ServerCall<String, String> call,
                               AtomicReference<Throwable> failure) {
        try {
            ServerCall.Listener<String> listener = handler.startCall(call, new Metadata());
            listener.onMessage("admin request");
            listener.onHalfClose();
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    private static class RaceService {
    }
}
