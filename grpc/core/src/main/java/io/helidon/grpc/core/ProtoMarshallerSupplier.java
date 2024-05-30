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

import com.google.protobuf.Message;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;

/**
 * A {@link MarshallerSupplier} implementation that supplies Protocol Buffer
 * marshaller instances.
 */
class ProtoMarshallerSupplier implements MarshallerSupplier {

    private ProtoMarshallerSupplier() {
    }

    static ProtoMarshallerSupplier create() {
        return new ProtoMarshallerSupplier();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
        try {
            java.lang.reflect.Method getDefaultInstance = clazz.getDeclaredMethod("getDefaultInstance");
            Message instance = (Message) getDefaultInstance.invoke(clazz);
            return (MethodDescriptor.Marshaller<T>) ProtoUtils.marshaller(instance);
        } catch (Exception e) {
            String msg = String.format(
                    "Attempting to use class %s, which is not a valid Protocol buffer message, with a default marshaller",
                    clazz.getName());
            throw new IllegalArgumentException(msg);
        }
    }
}

