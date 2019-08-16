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

package io.helidon.grpc.core;

import javax.inject.Named;

import com.google.protobuf.MessageLite;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.lite.ProtoLiteUtils;

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
            if (MessageLite.class.isAssignableFrom(clazz)) {
                return proto.get(clazz);
            }
            return JavaMarshaller.instance();
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
                MessageLite instance = (MessageLite) getDefaultInstance.invoke(clazz);

                return (MethodDescriptor.Marshaller<T>) ProtoLiteUtils.marshaller(instance);
            } catch (Exception e) {
                String msg = String.format(
                        "Attempting to use class %s, which is not a valid Protocol buffer message, with a default marshaller",
                        clazz.getName());
                throw new IllegalArgumentException(msg);
            }
        }
    }
}
