/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.server;

import io.helidon.webserver.grpc.GrpcServiceDescriptor;

/**
 * A class that may apply modifications to a {@link GrpcServiceDescriptor.Builder}
 * for an annotated gRPC service class.
 * <p>
 * Implementations of this class are called by the {@link GrpcServiceBuilder} when
 * it builds a {@link GrpcServiceDescriptor} from an annotated class. Instances of
 * {@link AnnotatedServiceConfigurer} are discovered using the
 * {@link io.helidon.common.HelidonServiceLoader}. This service
 * loader supports ordering of configurers.
 */
@FunctionalInterface
public interface AnnotatedServiceConfigurer {
    /**
     * Apply modifications to a {@link GrpcServiceDescriptor.Builder}.
     *
     * @param serviceClass the annotated gRPC service class
     * @param annotatedClass the  class with the {@link io.helidon.microprofile.grpc.api.Grpc} annotation
     * @param builder the builder to modify
     */
    void accept(Class<?> serviceClass, Class<?> annotatedClass, GrpcServiceDescriptor.Builder builder);
}
