/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ContextSettingServerInterceptor} unit tests.
 */
@SuppressWarnings("unchecked")
public class ContextSettingServerInterceptorTest {

    @Test
    public void shouldAddServiceDescriptor() {
        ServiceDescriptor serviceDescriptor = ServiceDescriptor.builder(createMockService())
                .unary("test", this::dummyUnary)
                .build();

        ContextSettingServerInterceptor interceptor = ContextSettingServerInterceptor.create();

        Metadata headers = new Metadata();
        ServerCall<String, String> call = mock(ServerCall.class);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ListenerStub listener = new ListenerStub();

        when(call.getMethodDescriptor()).thenReturn(serviceDescriptor.method("test").descriptor());
        when(next.startCall(any(ServerCall.class), any(Metadata.class))).thenReturn(listener);

        interceptor.setServiceDescriptor(serviceDescriptor);
        ServerCall.Listener<String> result = interceptor.interceptCall(call, headers, next);

        result.onMessage("testing...");

        Context contextCall = listener.getContext();
        ServiceDescriptor descriptor = ServiceDescriptor.SERVICE_DESCRIPTOR_KEY.get(contextCall);

        assertThat(descriptor, is(sameInstance(serviceDescriptor)));
    }

    @Test
    public void shouldAddServiceContext() {
        Context.Key<String> key = Context.key("test-service-key");
        ServiceDescriptor serviceDescriptor = ServiceDescriptor.builder(createMockService())
                .addContextValue(key, "test-service-value")
                .unary("test", this::dummyUnary)
                .build();

        ContextSettingServerInterceptor interceptor = ContextSettingServerInterceptor.create();

        Metadata headers = new Metadata();
        ServerCall<String, String> call = mock(ServerCall.class);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ListenerStub listener = new ListenerStub();

        when(call.getMethodDescriptor()).thenReturn(serviceDescriptor.method("test").descriptor());
        when(next.startCall(any(ServerCall.class), any(Metadata.class))).thenReturn(listener);

        interceptor.setServiceDescriptor(serviceDescriptor);
        ServerCall.Listener<String> result = interceptor.interceptCall(call, headers, next);

        Context currentContext = Context.current();

        result.onMessage("testing...");

        Context contextCall = listener.getContext();

        assertThat(contextCall, is(not(sameInstance(currentContext))));

        assertThat(key.get(contextCall), is("test-service-value"));
    }

    @Test
    public void shouldAddServiceAndMethodContext() {
        Context.Key<String> key1 = Context.key("test-service-key");
        Context.Key<String> key2 = Context.key("test-service-key");
        ServiceDescriptor serviceDescriptor = ServiceDescriptor.builder(createMockService())
                .addContextValue(key1, "test-service-value")
                .unary("test", this::dummyUnary, cfg -> cfg.addContextValue(key2, "test-method-value"))
                .build();

        ContextSettingServerInterceptor interceptor = ContextSettingServerInterceptor.create();

        Metadata headers = new Metadata();
        ServerCall<String, String> call = mock(ServerCall.class);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ListenerStub listener = new ListenerStub();

        when(call.getMethodDescriptor()).thenReturn(serviceDescriptor.method("test").descriptor());
        when(next.startCall(any(ServerCall.class), any(Metadata.class))).thenReturn(listener);

        interceptor.setServiceDescriptor(serviceDescriptor);
        ServerCall.Listener<String> result = interceptor.interceptCall(call, headers, next);

        Context currentContext = Context.current();

        result.onMessage("testing...");

        Context contextCall = listener.getContext();

        assertThat(contextCall, is(not(sameInstance(currentContext))));

        assertThat(key1.get(contextCall), is("test-service-value"));
        assertThat(key2.get(contextCall), is("test-method-value"));
    }

    @Test
    public void shouldAddOverrideServiceContextKeyWithMethodContextKey() {
        Context.Key<String> key = Context.key("test-service-key");
        ServiceDescriptor serviceDescriptor = ServiceDescriptor.builder(createMockService())
                .addContextValue(key, "test-service-value")
                .unary("test", this::dummyUnary, cfg -> cfg.addContextValue(key, "test-method-value"))
                .build();

        ContextSettingServerInterceptor interceptor = ContextSettingServerInterceptor.create();

        Metadata headers = new Metadata();
        ServerCall<String, String> call = mock(ServerCall.class);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ListenerStub listener = new ListenerStub();

        when(call.getMethodDescriptor()).thenReturn(serviceDescriptor.method("test").descriptor());
        when(next.startCall(any(ServerCall.class), any(Metadata.class))).thenReturn(listener);

        interceptor.setServiceDescriptor(serviceDescriptor);
        ServerCall.Listener<String> result = interceptor.interceptCall(call, headers, next);

        Context currentContext = Context.current();

        result.onMessage("testing...");

        Context contextCall = listener.getContext();

        assertThat(contextCall, is(not(sameInstance(currentContext))));

        assertThat(key.get(contextCall), is("test-method-value"));
    }


    private GrpcService createMockService() {
        GrpcService service = mock(GrpcService.class);

        when(service.name()).thenReturn("foo");

        return service;
    }

    private void dummyUnary(String request, StreamObserver<String> observer) {
    }

    private class ListenerStub
            extends ServerCall.Listener<String> {

        private Context context;

        @Override
        public void onMessage(String message) {
            context = Context.current();
        }

        public Context getContext() {
            return context;
        }
    }
}
