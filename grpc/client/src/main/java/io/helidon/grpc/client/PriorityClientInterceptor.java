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

import java.util.Objects;

import io.helidon.grpc.core.InterceptorPriority;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

/**
 * A {@link io.grpc.ClientInterceptor} that provides a
 * {@link InterceptorPriority} to indicate where it should
 * be applied in a list of interceptors.
 *
 * @author Jonathan Knight
 */
public interface PriorityClientInterceptor
        extends ClientInterceptor {

    /**
     * Obtain the {@link InterceptorPriority} for this interceptor.
     *
     * @return  the {@link InterceptorPriority} for this interceptor
     */
    InterceptorPriority getInterceptorPriority();

    /**
     * Create a {@link PriorityClientInterceptor} that wraps a normal
     * {@link io.grpc.ClientInterceptor}.
     *
     * @param priority     the priority for the interceptor
     * @param interceptor  the {@link io.grpc.ClientInterceptor} to wrap
     *
     * @return  a {@link PriorityClientInterceptor} that wraps a normal
     *          {@link io.grpc.ClientInterceptor}
     */
    static PriorityClientInterceptor create(InterceptorPriority priority, ClientInterceptor interceptor) {
        return new WrapperPriorityClientInterceptor(priority, interceptor);
    }

    /**
     * Ensure that a {@link io.grpc.ClientInterceptor} is an instance
     * of a {@link PriorityClientInterceptor} and if not wrap it in an
     * instance of a {@link PriorityClientInterceptor} with a priority
     * of {@link InterceptorPriority#Normal}.
     *
     * @param interceptor  the {@link io.grpc.ClientInterceptor} to wrap
     *
     * @return  a {@link PriorityClientInterceptor} that wraps a normal
     *          {@link io.grpc.ClientInterceptor}
     */
    static PriorityClientInterceptor ensure(ClientInterceptor interceptor) {
        if (interceptor instanceof PriorityClientInterceptor) {
            return (PriorityClientInterceptor) interceptor;
        } else {
            return create(InterceptorPriority.Normal, interceptor);
        }
    }

    /**
     * A {@link PriorityClientInterceptor} wrapper around a {@link io.grpc.ClientInterceptor}.
     */
    class WrapperPriorityClientInterceptor
            implements PriorityClientInterceptor {
        private ClientInterceptor interceptor;
        private InterceptorPriority priority;

        /**
         * Wrap a {@link io.grpc.ClientInterceptor}.
         *
         * @param priority     the priority to use
         * @param interceptor  the {@link io.grpc.ClientInterceptor} to wrap
         */
        WrapperPriorityClientInterceptor(InterceptorPriority priority, ClientInterceptor interceptor) {
            this.interceptor = interceptor;
            this.priority = priority;
        }

        @Override
        public InterceptorPriority getInterceptorPriority() {
            return priority;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                   CallOptions callOptions,
                                                                   Channel next) {
            return interceptor.interceptCall(method, callOptions, next);
        }

        /**
         * Obtain the wrapped {@link io.grpc.ClientInterceptor}.
         *
         * @return  the wrapped {@link io.grpc.ClientInterceptor}
         */
        public ClientInterceptor getInterceptor() {
            return interceptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            WrapperPriorityClientInterceptor that = (WrapperPriorityClientInterceptor) o;
            return Objects.equals(interceptor, that.interceptor)
                    && priority == that.priority;
        }

        @Override
        public int hashCode() {
            return Objects.hash(interceptor, priority);
        }

        @Override
        public String toString() {
            return "WrapperPriorityClientInterceptor("
                   + "priority=" + priority
                   + ", interceptor=" + interceptor
                   + ')';
        }
    }
}
