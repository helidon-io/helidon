/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.grpc.client;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

final class GrpcClientTypes {
    static final TypeName GRPC_CLIENT = TypeName.create("io.helidon.webclient.grpc.GrpcClient");
    static final TypeName RPC_CLIENT_ENDPOINT = TypeName.create("io.helidon.webclient.grpc.RpcClient.Endpoint");
    static final TypeName RPC_CLIENT_QUALIFIER = TypeName.create("io.helidon.webclient.grpc.RpcClient.Client");
    static final TypeName GRPC_CLIENT_METHOD_DESCRIPTOR =
            TypeName.create("io.helidon.webclient.grpc.GrpcClientMethodDescriptor");
    static final TypeName GRPC_SERVICE_CLIENT = TypeName.create("io.helidon.webclient.grpc.GrpcServiceClient");
    static final TypeName GRPC_SERVICE_DESCRIPTOR = TypeName.create("io.helidon.webclient.grpc.GrpcServiceDescriptor");
    static final TypeName GRPC_METHOD = TypeName.create("io.helidon.grpc.api.Grpc.GrpcMethod");
    static final TypeName GRPC_PROTO = TypeName.create("io.helidon.grpc.api.Grpc.Proto");
    static final TypeName GRPC_PROTO_DESCRIPTOR = TypeName.create("io.helidon.grpc.api.Grpc.ProtoDescriptor");
    static final TypeName GRPC_SERVICE = TypeName.create("io.helidon.grpc.api.Grpc.GrpcService");
    static final TypeName SERVICE_INSTANCE = TypeName.create("io.helidon.service.registry.ServiceInstance");
    static final TypeName PROTO_FILE_DESCRIPTOR = TypeName.create("com.google.protobuf.Descriptors.FileDescriptor");
    static final TypeName PROTO_MESSAGE_DESCRIPTOR = TypeName.create("com.google.protobuf.Descriptors.Descriptor");

    static final Annotation RPC_CLIENT_QUALIFIER_INSTANCE = Annotation.create(RPC_CLIENT_QUALIFIER);

    private GrpcClientTypes() {
    }
}
