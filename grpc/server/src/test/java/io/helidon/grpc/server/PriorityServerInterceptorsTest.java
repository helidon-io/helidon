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

import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;

/**
 * @author Jonathan Knight
 */
public class PriorityServerInterceptorsTest {
    @Test
    public void shouldBeEmpty() {
        PriorityServerInterceptors interceptors = new PriorityServerInterceptors();
        assertThat(interceptors.getInterceptors(), is(emptyIterable()));
    }

    @Test
    public void shouldAddPriorityServerInterceptor() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);
        PriorityServerInterceptor priority = PriorityServerInterceptor.create(InterceptorPriority.Context, interceptor);
        PriorityServerInterceptors interceptors = new PriorityServerInterceptors();

        interceptors.add(priority);
        
        assertThat(interceptors.getInterceptors(), contains(priority));
    }

    @Test
    public void shouldAddPriorityServerInterceptors() {
        ServerInterceptor interceptor1 = mock(ServerInterceptor.class, "1");
        ServerInterceptor interceptor2 = mock(ServerInterceptor.class, "2");
        ServerInterceptor interceptor3 = mock(ServerInterceptor.class, "3");
        ServerInterceptor interceptor4 = mock(ServerInterceptor.class, "4");
        ServerInterceptor interceptor5 = mock(ServerInterceptor.class, "5");
        ServerInterceptor interceptor6 = mock(ServerInterceptor.class, "6");
        ServerInterceptor interceptor7 = mock(ServerInterceptor.class, "7");
        ServerInterceptor interceptor8 = mock(ServerInterceptor.class, "8");
        ServerInterceptor interceptor9 = mock(ServerInterceptor.class, "9");
        ServerInterceptor interceptor10 = mock(ServerInterceptor.class, "10");
        PriorityServerInterceptor priority1 = PriorityServerInterceptor.create(InterceptorPriority.Context, interceptor1);
        PriorityServerInterceptor priority2 = PriorityServerInterceptor.create(InterceptorPriority.Security, interceptor2);
        PriorityServerInterceptor priority3 = PriorityServerInterceptor.create(InterceptorPriority.First, interceptor3);
        PriorityServerInterceptor priority4 = PriorityServerInterceptor.create(InterceptorPriority.Normal, interceptor4);
        PriorityServerInterceptor priority5 = PriorityServerInterceptor.create(InterceptorPriority.Last, interceptor5);
        PriorityServerInterceptor priority6 = PriorityServerInterceptor.create(InterceptorPriority.Context, interceptor6);
        PriorityServerInterceptor priority7 = PriorityServerInterceptor.create(InterceptorPriority.Security, interceptor7);
        PriorityServerInterceptor priority8 = PriorityServerInterceptor.create(InterceptorPriority.First, interceptor8);
        PriorityServerInterceptor priority9 = PriorityServerInterceptor.create(InterceptorPriority.Normal, interceptor9);
        PriorityServerInterceptor priority10 = PriorityServerInterceptor.create(InterceptorPriority.Last, interceptor10);

        PriorityServerInterceptors interceptors = new PriorityServerInterceptors();

        interceptors.add(priority5);
        interceptors.add(priority4);
        interceptors.add(priority3);
        interceptors.add(priority2);
        interceptors.add(priority1);
        interceptors.add(priority10);
        interceptors.add(priority9);
        interceptors.add(priority8);
        interceptors.add(priority7);
        interceptors.add(priority6);

        assertThat(interceptors.getInterceptors(), contains(priority1, priority6, priority2, priority7, priority3,
                                                            priority8, priority4, priority9, priority5, priority10));
    }

    @Test
    public void shouldAddServerInterceptorAsNormalPriority() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);
        PriorityServerInterceptor priority1 = PriorityServerInterceptor.create(InterceptorPriority.First, interceptor);
        PriorityServerInterceptor priority2 = PriorityServerInterceptor.create(InterceptorPriority.Normal, interceptor);
        PriorityServerInterceptor priority3 = PriorityServerInterceptor.create(InterceptorPriority.Last, interceptor);

        PriorityServerInterceptors interceptors = new PriorityServerInterceptors();

        interceptors.add(priority1, priority3);
        interceptors.add(interceptor);

        assertThat(interceptors.getInterceptors(), contains(priority1, priority2, priority3));
    }

}
