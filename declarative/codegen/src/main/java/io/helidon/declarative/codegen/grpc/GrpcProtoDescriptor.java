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

package io.helidon.declarative.codegen.grpc;

import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Proto descriptor source declared by a declarative gRPC type.
 *
 * @param method method annotated with {@code @Grpc.Proto}
 * @param isStatic whether the proto method is static
 * @param descriptorType type declared by {@code @Grpc.ProtoDescriptor}
 */
public record GrpcProtoDescriptor(Optional<TypedElementInfo> method, boolean isStatic, Optional<TypeName> descriptorType) {
    /**
     * Create a proto method descriptor source.
     *
     * @param method proto method
     * @param isStatic whether the proto method is static
     * @return proto descriptor source
     */
    public static GrpcProtoDescriptor method(TypedElementInfo method, boolean isStatic) {
        return new GrpcProtoDescriptor(Optional.of(method), isStatic, Optional.empty());
    }

    /**
     * Create a proto type descriptor source.
     *
     * @param descriptorType generated proto type
     * @return proto descriptor source
     */
    public static GrpcProtoDescriptor descriptorType(TypeName descriptorType) {
        return new GrpcProtoDescriptor(Optional.empty(), true, Optional.of(descriptorType));
    }
}
