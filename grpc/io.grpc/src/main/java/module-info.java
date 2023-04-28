/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

/**
 * Module info for re-packaged gRPC Java module.
 */
open module io.grpc {
    requires com.google.gson;
    requires java.logging;
    requires java.naming;
    requires io.perfmark;
    requires com.google.common;

    exports io.grpc;
    exports io.grpc.inprocess;
    exports io.grpc.internal;
    exports io.grpc.util;

    provides io.grpc.LoadBalancerProvider
            with io.grpc.internal.PickFirstLoadBalancerProvider,
                 io.grpc.util.SecretRoundRobinLoadBalancerProvider.Provider,
                 io.grpc.util.OutlierDetectionLoadBalancerProvider;

    provides io.grpc.NameResolverProvider
            with io.grpc.internal.DnsNameResolverProvider;

    uses io.grpc.ServerProvider;
    uses io.grpc.NameResolverProvider;
    uses io.grpc.LoadBalancerProvider;
    uses io.grpc.ManagedChannelProvider;
    uses io.grpc.internal.BinaryLogProvider;
}
