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

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;

public class GrpcClientTestUtil {

    public static class BaseInterceptor
            implements PriorityClientInterceptor {

        private int invocationCount;

        @Override
        public InterceptorPriority getInterceptorPriority() {
            return InterceptorPriority.Last;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                   CallOptions callOptions,
                                                                   Channel next) {
            invocationCount++;
            return next.newCall(method, callOptions);
        }

        public int getInvocationCount() {
            return invocationCount;
        }

        public void reset() {
            this.invocationCount = 0;
        }

    }

    public static class LowPriorityInterceptor
            extends BaseInterceptor {
        @Override
        public InterceptorPriority getInterceptorPriority() {
            return InterceptorPriority.Last;
        }
    }

    public static class MediumPriorityInterceptor
            extends BaseInterceptor {
        @Override
        public InterceptorPriority getInterceptorPriority() {
            return InterceptorPriority.First;
        }
    }

    public static class HighPriorityInterceptor
            extends BaseInterceptor {
        @Override
        public InterceptorPriority getInterceptorPriority() {
            return InterceptorPriority.Context;
        }
    }
}

