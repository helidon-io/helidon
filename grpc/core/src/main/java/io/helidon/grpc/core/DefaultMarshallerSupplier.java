/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.grpc.core;

import com.google.protobuf.MessageLite;
import io.grpc.MethodDescriptor;

/**
 * The default {@link MarshallerSupplier}.
 */
class DefaultMarshallerSupplier implements MarshallerSupplier {

    static DefaultMarshallerSupplier create() {
        return new DefaultMarshallerSupplier();
    }

    private DefaultMarshallerSupplier() {
    }

    private final ProtoMarshallerSupplier proto = ProtoMarshallerSupplier.create();

    @Override
    public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
        if (MessageLite.class.isAssignableFrom(clazz)) {
            return proto.get(clazz);
        }
        String msg = String.format(
                "Class %s must be a valid ProtoBuf message, or a custom marshaller for it must be specified explicitly",
                clazz.getName());
        throw new IllegalArgumentException(msg);
    }
}
