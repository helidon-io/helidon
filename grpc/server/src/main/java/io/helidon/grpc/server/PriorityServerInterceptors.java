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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.helidon.grpc.core.InterceptorPriority;

import io.grpc.ServerInterceptor;

/**
 * An container for an ordered collection of {@link io.grpc.ServerInterceptor}s.
 * <p>
 * Interceptors added to this collection will be ordered first by priority if they
 * are {@link PriorityServerInterceptor}s and then by the order that they are added.
 * <p>
 * Interceptors added that are not {@link PriorityServerInterceptor} instances will
 * have a priority of {@link io.helidon.grpc.core.InterceptorPriority#Normal}.
 *
 * @author Jonathan Knight
 */
public class PriorityServerInterceptors {

    private final Map<InterceptorPriority, List<PriorityServerInterceptor>> interceptors = new TreeMap<>();

    /**
     * Create an empty {@link PriorityServerInterceptors}.
     */
    public PriorityServerInterceptors() {
    }

    /**
     * Create an empty {@link PriorityServerInterceptors}.
     *
     * @param interceptors  the {@link ServerInterceptor}s to add
     */
    public PriorityServerInterceptors(Iterable<ServerInterceptor> interceptors) {
        add(interceptors);
    }

    /**
     * Add a {@link ServerInterceptor}.
     *
     * @param interceptors  the {@link ServerInterceptor}s to add
     *
     * @return this {@link PriorityServerInterceptors} for fluent method chaining
     *
     * @throws java.lang.NullPointerException if the interceptors array parameter is null
     */
    public synchronized PriorityServerInterceptors add(ServerInterceptor... interceptors) {
        return add(Arrays.asList(interceptors));
    }

    /**
     * Add a {@link PriorityServerInterceptor}.
     *
     * @param interceptors  the {@link ServerInterceptor}s to add
     *
     * @return this {@link PriorityServerInterceptors} for fluent method chaining
     *
     * @throws java.lang.NullPointerException if the interceptors parameter is null
     */
    public synchronized PriorityServerInterceptors add(Iterable<ServerInterceptor> interceptors) {
        for (ServerInterceptor interceptor : interceptors) {
            if (interceptor != null) {
                PriorityServerInterceptor priorityInterceptor = PriorityServerInterceptor.ensure(interceptor);
                InterceptorPriority priority = ensurePriority(priorityInterceptor);

                this.interceptors.compute(priority, (key, list) -> combine(list, priorityInterceptor));
            }
        }

        return this;
    }

    /**
     * Obtain a {@link List} of the contained {@link PriorityServerInterceptor} ordered
     * by priority.
     * <p>
     * {@link PriorityServerInterceptor} with the same priority will be ordered in the
     * order that they were added to this {@link PriorityServerInterceptors}.
     *
     * @return  a {@link List} of the contained {@link PriorityServerInterceptor} ordered
     *          by priority
     */
    public List<ServerInterceptor> getInterceptors() {
        return interceptors.entrySet()
                    .stream()
                    .flatMap(e -> e.getValue().stream())
                    .collect(Collectors.toList());
    }


    private InterceptorPriority ensurePriority(PriorityServerInterceptor interceptor) {
        InterceptorPriority priority = interceptor.getInterceptorPriority();

        return priority == null ? InterceptorPriority.Normal : priority;
    }

    private List<PriorityServerInterceptor> combine(List<PriorityServerInterceptor> list,
                                                    PriorityServerInterceptor interceptor) {
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(interceptor);
        return list;
    }
}
