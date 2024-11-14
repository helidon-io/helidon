/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import jakarta.inject.Named;

/**
 * A supplier of {@link MethodDescriptor.Marshaller} instances for specific
 * classes.
 */
@FunctionalInterface
public interface MarshallerSupplier {

    /**
     * The name of the Protocol Buffer marshaller supplier.
     */
    String PROTO = "proto";

    /**
     * The name to use to specify the default marshaller supplier.
     */
    String DEFAULT = "default";

    /**
     * Obtain a {@link MethodDescriptor.Marshaller} for a type.
     *
     * @param clazz  the {@link Class} of the type to obtain the {@link MethodDescriptor.Marshaller} for
     * @param <T>    the type to be marshalled
     *
     * @return a {@link MethodDescriptor.Marshaller} for a type
     */
    <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz);

    /**
     * Obtain the default marshaller.
     *
     * @return the default marshaller
     */
    static MarshallerSupplier defaultInstance() {
        return new DefaultMarshallerSupplier();
    }

    /**
     * The default {@link MarshallerSupplier}.
     */
    @Named(MarshallerSupplier.DEFAULT)
    class DefaultMarshallerSupplier
            implements MarshallerSupplier {

        private final ProtoMarshallerSupplier proto = new ProtoMarshallerSupplier();

        @Override
        public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
            if (Message.class.isAssignableFrom(clazz)) {
                return proto.get(clazz);
            }
            String msg = String.format(
                    "Class %s must be a valid ProtoBuf message, or a custom marshaller for it must be specified explicitly",
                    clazz.getName());
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * A {@link MarshallerSupplier} implementation that
     * supplies Protocol Buffer marshaller instances.
     */
    @Named(PROTO)
    class ProtoMarshallerSupplier
            implements MarshallerSupplier {

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
}
