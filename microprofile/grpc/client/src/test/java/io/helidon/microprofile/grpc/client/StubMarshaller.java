/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.client;

import java.io.InputStream;

import javax.inject.Named;

import io.helidon.grpc.core.MarshallerSupplier;

import io.grpc.MethodDescriptor;

/**
 * A stub {@link io.grpc.MethodDescriptor.Marshaller}.
 * <p>
 * This marshaller will not actually work and should not
 * be used as a real marshaller.
 */
public class StubMarshaller<T>
        implements MethodDescriptor.Marshaller<T> {

    @Override
    public InputStream stream(T value) {
        return null;
    }

    @Override
    public T parse(InputStream stream) {
        return null;
    }

    @Named("stub")
    public static class Supplier
            implements MarshallerSupplier {
        @Override
        public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
            return new StubMarshaller<>();
        }
    }
}
