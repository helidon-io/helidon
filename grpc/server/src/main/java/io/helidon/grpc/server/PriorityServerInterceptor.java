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

import java.util.Objects;

import io.helidon.grpc.core.InterceptorPriority;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * A {@link io.grpc.ServerInterceptor} that provides a
 * {@link io.helidon.grpc.core.InterceptorPriority} to indicate where it should
 * be applied in a list of interceptors.
 */
public interface PriorityServerInterceptor
        extends ServerInterceptor {

    /**
     * Obtain the {@link io.helidon.grpc.core.InterceptorPriority} for this interceptor.
     *
     * @return  the {@link io.helidon.grpc.core.InterceptorPriority} for this interceptor
     */
    InterceptorPriority getInterceptorPriority();

    /**
     * Create a {@link PriorityServerInterceptor} that wraps a normal
     * {@link io.grpc.ServerInterceptor}.
     *
     * @param priority     the priority for the interceptor
     * @param interceptor  the {@link io.grpc.ServerInterceptor} to wrap
     *
     * @return  a {@link PriorityServerInterceptor} that wraps a normal
     *          {@link io.grpc.ServerInterceptor}
     */
    static PriorityServerInterceptor create(InterceptorPriority priority, ServerInterceptor interceptor) {
        return new WrapperPriorityServerInterceptor(priority, interceptor);
    }

    /**
     * Ensure that a {@link io.grpc.ServerInterceptor} is an instance
     * of a {@link PriorityServerInterceptor} and if not wrap it in an
     * instance of a {@link PriorityServerInterceptor} with a priority
     * of {@link InterceptorPriority#Normal}.
     *
     * @param interceptor  the {@link io.grpc.ServerInterceptor} to wrap
     *
     * @return  a {@link PriorityServerInterceptor} that wraps a normal
     *          {@link io.grpc.ServerInterceptor}
     */
    static PriorityServerInterceptor ensure(ServerInterceptor interceptor) {
        if (interceptor instanceof PriorityServerInterceptor) {
            return (PriorityServerInterceptor) interceptor;
        } else {
            return create(InterceptorPriority.Normal, interceptor);
        }
    }

    /**
     * A {@link PriorityServerInterceptor} wrapper around a {@link ServerInterceptor}.
     */
    class WrapperPriorityServerInterceptor
            implements PriorityServerInterceptor {
        private ServerInterceptor interceptor;
        private InterceptorPriority priority;

        /**
         * Wrap a {@link io.grpc.ServerInterceptor}.
         *
         * @param priority     the priority to use
         * @param interceptor  the {@link io.grpc.ServerInterceptor} to wrap
         */
        WrapperPriorityServerInterceptor(InterceptorPriority priority, ServerInterceptor interceptor) {
            this.interceptor = interceptor;
            this.priority = priority;
        }

        @Override
        public InterceptorPriority getInterceptorPriority() {
            return priority;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            return interceptor.interceptCall(call, headers, next);
        }

        /**
         * Obtain the wrapped {@link io.grpc.ServerInterceptor}.
         *
         * @return  the wrapped {@link io.grpc.ServerInterceptor}
         */
        public ServerInterceptor getInterceptor() {
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
            WrapperPriorityServerInterceptor that = (WrapperPriorityServerInterceptor) o;
            return Objects.equals(interceptor, that.interceptor)
                    && priority == that.priority;
        }

        @Override
        public int hashCode() {
            return Objects.hash(interceptor, priority);
        }

        @Override
        public String toString() {
            return "WrapperPriorityServerInterceptor("
                   + "priority=" + priority
                   + ", interceptor=" + interceptor
                   + ')';
        }
    }
}
