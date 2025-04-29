/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;

/**
 * A {@link BindableService} implementation that creates {@link ServerServiceDefinition}
 * from a {@link GrpcServiceDescriptor}.
 */
class BindableServiceImpl implements BindableService {

    /**
     * The descriptor of this service.
     */
    private final GrpcServiceDescriptor descriptor;

    private BindableServiceImpl(GrpcServiceDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Create a {@link BindableServiceImpl} for a gRPC service.
     *
     * @param descriptor   the service descriptor
     * @return a {@link BindableServiceImpl} for the gRPC service
     */
    static BindableServiceImpl create(GrpcServiceDescriptor descriptor) {
        return new BindableServiceImpl(descriptor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ServerServiceDefinition bindService() {
        ServiceDescriptor.Builder serviceDescriptorBuilder = ServiceDescriptor.newBuilder(descriptor.fullName());
        if (descriptor.proto() != null) {
            serviceDescriptorBuilder.setSchemaDescriptor((ProtoFileDescriptorSupplier) descriptor::proto);
        }
        descriptor.methods().forEach(method -> serviceDescriptorBuilder.addMethod(method.descriptor()));

        ServerServiceDefinition.Builder builder = ServerServiceDefinition.builder(serviceDescriptorBuilder.build());
        descriptor.methods().forEach(method -> builder.addMethod(
                (MethodDescriptor) method.descriptor(), method.callHandler()));
        return builder.build();
    }
}
