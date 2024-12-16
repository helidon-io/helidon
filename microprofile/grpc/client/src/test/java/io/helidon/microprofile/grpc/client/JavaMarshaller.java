/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import io.helidon.grpc.core.MarshallerSupplier;

import io.grpc.MethodDescriptor;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

/**
 * An implementation of a gRPC {@link io.grpc.MethodDescriptor.Marshaller} that
 * uses Java serialization for testing.
 */
public class JavaMarshaller<T> implements MethodDescriptor.Marshaller<T> {

    @Override
    public InputStream stream(T obj) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(obj);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T parse(InputStream in) {
        try (ObjectInputStream ois = new ObjectInputStream(in)) {
            return (T) ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A {@link io.helidon.grpc.core.MarshallerSupplier} implementation that supplies
     * instances of {@link io.helidon.microprofile.grpc.client.JavaMarshaller}.
     */
    @Dependent
    @Named("java")
    public static class Supplier implements MarshallerSupplier {

        @Override
        public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
            return new JavaMarshaller<>();
        }
    }
}
