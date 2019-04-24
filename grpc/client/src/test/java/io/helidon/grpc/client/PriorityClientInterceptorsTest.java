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

package io.helidon.grpc.client;

import io.helidon.grpc.core.InterceptorPriority;

import io.grpc.ClientInterceptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;


public class PriorityClientInterceptorsTest {
    @Test
    public void shouldBeEmpty() {
        PriorityClientInterceptors interceptors = new PriorityClientInterceptors();
        assertThat(interceptors.getInterceptors(), is(emptyIterable()));
    }

    @Test
    public void shouldAddPriorityClientInterceptor() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        PriorityClientInterceptor priority = PriorityClientInterceptor.create(InterceptorPriority.Context, interceptor);
        PriorityClientInterceptors interceptors = new PriorityClientInterceptors();

        interceptors.add(priority);
        
        assertThat(interceptors.getInterceptors(), contains(priority));
    }

    @Test
    public void shouldAddPriorityClientInterceptors() {
        ClientInterceptor interceptor1 = mock(ClientInterceptor.class, "1");
        ClientInterceptor interceptor2 = mock(ClientInterceptor.class, "2");
        ClientInterceptor interceptor3 = mock(ClientInterceptor.class, "3");
        ClientInterceptor interceptor4 = mock(ClientInterceptor.class, "4");
        ClientInterceptor interceptor5 = mock(ClientInterceptor.class, "5");
        ClientInterceptor interceptor6 = mock(ClientInterceptor.class, "6");
        ClientInterceptor interceptor7 = mock(ClientInterceptor.class, "7");
        ClientInterceptor interceptor8 = mock(ClientInterceptor.class, "8");
        ClientInterceptor interceptor9 = mock(ClientInterceptor.class, "9");
        ClientInterceptor interceptor10 = mock(ClientInterceptor.class, "10");
        PriorityClientInterceptor priority1 = PriorityClientInterceptor.create(InterceptorPriority.Context, interceptor1);
        PriorityClientInterceptor priority2 = PriorityClientInterceptor.create(InterceptorPriority.Security, interceptor2);
        PriorityClientInterceptor priority3 = PriorityClientInterceptor.create(InterceptorPriority.First, interceptor3);
        PriorityClientInterceptor priority4 = PriorityClientInterceptor.create(InterceptorPriority.Normal, interceptor4);
        PriorityClientInterceptor priority5 = PriorityClientInterceptor.create(InterceptorPriority.Last, interceptor5);
        PriorityClientInterceptor priority6 = PriorityClientInterceptor.create(InterceptorPriority.Context, interceptor6);
        PriorityClientInterceptor priority7 = PriorityClientInterceptor.create(InterceptorPriority.Security, interceptor7);
        PriorityClientInterceptor priority8 = PriorityClientInterceptor.create(InterceptorPriority.First, interceptor8);
        PriorityClientInterceptor priority9 = PriorityClientInterceptor.create(InterceptorPriority.Normal, interceptor9);
        PriorityClientInterceptor priority10 = PriorityClientInterceptor.create(InterceptorPriority.Last, interceptor10);

        PriorityClientInterceptors interceptors = new PriorityClientInterceptors();

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
    public void shouldAddClientInterceptorAsNormalPriority() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        PriorityClientInterceptor priority1 = PriorityClientInterceptor.create(InterceptorPriority.First, interceptor);
        PriorityClientInterceptor priority2 = PriorityClientInterceptor.create(InterceptorPriority.Normal, interceptor);
        PriorityClientInterceptor priority3 = PriorityClientInterceptor.create(InterceptorPriority.Last, interceptor);

        PriorityClientInterceptors interceptors = new PriorityClientInterceptors();

        interceptors.add(priority1, priority3);
        interceptors.add(interceptor);

        assertThat(interceptors.getInterceptors(), contains(priority1, priority2, priority3));
    }

}
