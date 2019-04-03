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

import io.helidon.grpc.core.InterceptorPriority;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Jonathn Knight
 */
@SuppressWarnings("unchecked")
public class WrapperPriorityServerInterceptorTest {
    @Test
    public void shouldWrapInterceptor() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);
        PriorityServerInterceptor.WrapperPriorityServerInterceptor wrapper
                = new PriorityServerInterceptor.WrapperPriorityServerInterceptor(InterceptorPriority.First, interceptor);

        assertThat(wrapper.getInterceptor(), is(sameInstance(interceptor)));
        assertThat(wrapper.getInterceptorPriority(), is(InterceptorPriority.First));
    }

    @Test
    public void shouldCallIntercept() {
        ServerCall call = mock(ServerCall.class);
        ServerCallHandler<?, ?> next = mock(ServerCallHandler.class);
        Metadata headers = new Metadata();
        ServerInterceptor interceptor = mock(ServerInterceptor.class);
        PriorityServerInterceptor.WrapperPriorityServerInterceptor wrapper
                = new PriorityServerInterceptor.WrapperPriorityServerInterceptor(InterceptorPriority.First, interceptor);

        wrapper.interceptCall(call, headers, next);

        verify(interceptor).interceptCall(call, headers, next);
    }
}
