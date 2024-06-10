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

package io.helidon.grpc.core;

import io.grpc.MethodDescriptor;

/**
 * A supplier of {@link MethodDescriptor.Marshaller} instances for specific
 * classes.
 */
@FunctionalInterface
public interface MarshallerSupplier {

    /**
     * Obtain a {@link MethodDescriptor.Marshaller} for a type.
     *
     * @param clazz the {@link Class} of the type to obtain the {@link MethodDescriptor.Marshaller} for
     * @param <T> the type to be marshalled
     * @return a {@link MethodDescriptor.Marshaller} for a type
     */
    <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz);

    /**
     * Creates a default marshaller supplier.
     *
     * @return the default marshaller supplier
     */
    static MarshallerSupplier create() {
        return DefaultMarshallerSupplier.create();
    }
}
