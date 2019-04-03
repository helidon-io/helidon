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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.helidon.grpc.core.InterceptorPriority;

import io.grpc.ClientInterceptor;

/**
 * An container for an ordered collection of {@link io.grpc.ClientInterceptor}s.
 * <p>
 * Interceptors added to this collection will be ordered first by priority if they
 * are {@link PriorityClientInterceptor}s and then by the order that they are added.
 * <p>
 * Interceptors added that are not {@link PriorityClientInterceptor} instances will
 * have a priority of {@link InterceptorPriority#Normal}.
 *
 * @author Jonathan Knight
 */
public class PriorityClientInterceptors {

    private final Map<InterceptorPriority, List<PriorityClientInterceptor>> interceptors = new TreeMap<>();

    /**
     * Create an empty {@link PriorityClientInterceptors}.
     */
    public PriorityClientInterceptors() {
    }

    /**
     * Create an empty {@link PriorityClientInterceptors}.
     *
     * @param interceptors  the {@link io.grpc.ClientInterceptor}s to add
     */
    public PriorityClientInterceptors(Iterable<ClientInterceptor> interceptors) {
        add(interceptors);
    }

    /**
     * Add a {@link io.grpc.ClientInterceptor}.
     *
     * @param interceptors  the {@link io.grpc.ClientInterceptor}s to add
     *
     * @return this {@link PriorityClientInterceptors} for fluent method chaining
     *
     * @throws NullPointerException if the interceptors array parameter is null
     */
    public synchronized PriorityClientInterceptors add(ClientInterceptor... interceptors) {
        return add(Arrays.asList(interceptors));
    }

    /**
     * Add a {@link PriorityClientInterceptor}.
     *
     * @param interceptors  the {@link io.grpc.ClientInterceptor}s to add
     *
     * @return this {@link PriorityClientInterceptors} for fluent method chaining
     *
     * @throws NullPointerException if the interceptors parameter is null
     */
    public synchronized PriorityClientInterceptors add(Iterable<ClientInterceptor> interceptors) {
        for (ClientInterceptor interceptor : interceptors) {
            if (interceptor != null) {
                PriorityClientInterceptor priorityInterceptor = PriorityClientInterceptor.ensure(interceptor);
                InterceptorPriority priority = ensurePriority(priorityInterceptor);

                this.interceptors.compute(priority, (key, list) -> combine(list, priorityInterceptor));
            }
        }

        return this;
    }

    /**
     * Obtain a {@link java.util.List} of the contained {@link PriorityClientInterceptor} ordered
     * by priority.
     * <p>
     * {@link PriorityClientInterceptor} with the same priority will be ordered in the
     * order that they were added to this {@link PriorityClientInterceptors}.
     *
     * @return  a {@link java.util.List} of the contained {@link PriorityClientInterceptor} ordered
     *          by priority
     */
    public List<ClientInterceptor> getInterceptors() {
        return interceptors.entrySet()
                    .stream()
                    .flatMap(e -> e.getValue().stream())
                    .collect(Collectors.toList());
    }


    private InterceptorPriority ensurePriority(PriorityClientInterceptor interceptor) {
        InterceptorPriority priority = interceptor.getInterceptorPriority();

        return priority == null ? InterceptorPriority.Normal : priority;
    }

    private List<PriorityClientInterceptor> combine(List<PriorityClientInterceptor> list,
                                                    PriorityClientInterceptor interceptor) {
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(interceptor);
        return list;
    }
}
