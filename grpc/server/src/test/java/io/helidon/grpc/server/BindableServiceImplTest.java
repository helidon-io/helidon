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

import java.util.List;

import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.core.PriorityBag;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class BindableServiceImplTest {

    /**
     * Assert that even though a specific interceptor may be added
     * more than once at different levels (global, service, method)
     * that it only actually gets added once.
     */
    @Test
    public void shouldNotAddDuplicateInterceptors() {
        ServerInterceptor interceptorOne = spy(new InterceptorStub());
        ServerInterceptor interceptorTwo = spy(new InterceptorStub());
        ServerInterceptor interceptorThree = spy(new InterceptorStub());
        ServerInterceptor interceptorFour = spy(new InterceptorStub());
        ServerInterceptor interceptorFive = spy(new InterceptorStub());
        ServerInterceptor interceptorSix = spy(new InterceptorStub());

        PriorityBag<ServerInterceptor> global = PriorityBag.withDefaultPriority(InterceptorPriorities.USER);
        global.addAll(List.of(interceptorOne, interceptorTwo, interceptorThree));

        ServiceDescriptor descriptor = ServiceDescriptor.builder(new Service())
                .intercept(interceptorTwo)
                .intercept(interceptorFour)
                .intercept(interceptorFive)
                .unary("foo", this::unary, rules -> rules.intercept(interceptorThree, interceptorFour, interceptorSix))
                .build();

        BindableServiceImpl bindableService = BindableServiceImpl.create(descriptor, global);
        ServerServiceDefinition definition = bindableService.bindService();
        ServerMethodDefinition<?, ?> method = definition.getMethod("Service/foo");
        ServerCallHandler<?, ?> callHandler = method.getServerCallHandler();
        Metadata headers = new Metadata();

        ServerCall call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(method.getMethodDescriptor());

        callHandler.startCall(call, headers);

        verify(interceptorOne, times(1)).interceptCall(same(call), same(headers), any(ServerCallHandler.class));
        verify(interceptorTwo, times(1)).interceptCall(same(call), same(headers), any(ServerCallHandler.class));
        verify(interceptorThree, times(1)).interceptCall(same(call), same(headers), any(ServerCallHandler.class));
        verify(interceptorFour, times(1)).interceptCall(same(call), same(headers), any(ServerCallHandler.class));
        verify(interceptorFive, times(1)).interceptCall(same(call), same(headers), any(ServerCallHandler.class));
        verify(interceptorSix, times(1)).interceptCall(same(call), same(headers), any(ServerCallHandler.class));
    }


    public void unary(String request, StreamObserver<String> response) {
    }

    public static class Service
            implements GrpcService {
        @Override
        public void update(ServiceDescriptor.Rules rules) {
        }
    }

    public static class InterceptorStub
            implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            return next == null ? null : next.startCall(call, headers);
        }
    }
}
