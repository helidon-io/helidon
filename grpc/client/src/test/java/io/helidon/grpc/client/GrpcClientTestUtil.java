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

import java.util.function.Consumer;

import io.helidon.grpc.client.test.StringServiceGrpc;
import io.helidon.grpc.core.InterceptorPriority;
import io.helidon.grpc.server.GrpcServer;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import org.eclipse.microprofile.metrics.MetricType;
import services.StringService;

import static io.helidon.grpc.client.test.Strings.StringMessage;

public class GrpcClientTestUtil {

    public static volatile GrpcServer grpcServer;

    public static volatile int grpcPort;

    static ClientServiceDescriptor protoStringSvcDesc;

    static MethodDescriptor<StringMessage, StringMessage> _toLower;
    static MethodDescriptor<StringMessage, StringMessage> _toUpper;
    static MethodDescriptor<StringMessage, StringMessage> _split;
    static MethodDescriptor<StringMessage, StringMessage> _join;
    static MethodDescriptor<StringMessage, StringMessage> _echo;

    public static void initProtoBasedStringServiceDesc() {
        protoStringSvcDesc = ClientServiceDescriptor.builder(StringService.class,
                                                             StringServiceGrpc.getServiceDescriptor()).build();

        _toLower = protoStringSvcDesc.<StringMessage, StringMessage>method("Lower").descriptor();
        _toUpper = protoStringSvcDesc.<StringMessage, StringMessage>method("Upper").descriptor();
        _split = protoStringSvcDesc.<StringMessage, StringMessage>method("Split").descriptor();
        _join = protoStringSvcDesc.<StringMessage, StringMessage>method("Join").descriptor();
        _echo = protoStringSvcDesc.<StringMessage, StringMessage>method("Echo").descriptor();
    }

   public static <ReqT, ResT> Consumer<ClientMethodDescriptor.Config<ReqT, ResT>> getMetricConfigurer(MetricType metricType) {
        Consumer<ClientMethodDescriptor.Config<ReqT, ResT>> metricConfigurer;

        switch (metricType) {
        case COUNTER:
            metricConfigurer = ClientMethodDescriptor.Config::counted;
            break;
        case GAUGE:
            metricConfigurer = ClientMethodDescriptor.Config::gauged;
            break;
        case HISTOGRAM:
            metricConfigurer = ClientMethodDescriptor.Config::histogram;
            break;
        case METERED:
            metricConfigurer = ClientMethodDescriptor.Config::metered;
            break;
        case TIMER:
            metricConfigurer = ClientMethodDescriptor.Config::timed;
            break;
        default:
            metricConfigurer = ClientMethodDescriptor.Config::disableMetrics;
            break;
        }

        return metricConfigurer;
    }

    public static BaseInterceptor lowPriorityInterceptor() {
        return new LowPriorityInterceptor();
    }

    public static BaseInterceptor mediumPriorityInterceptor() {
        return new MediumPriorityInterceptor();
    }

    public static BaseInterceptor highPriorityInterceptor() {
        return new HighPriorityInterceptor();
    }

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

    public static ClientServiceDescriptor getProtoStringSvcDesc() {
        return protoStringSvcDesc;
    }

}

