/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import io.grpc.MethodDescriptor;

/**
 * An implementation of a gRPC {@link MethodDescriptor.Marshaller} that
 * uses JSONB for serialization.
 *
 * @param <T>  the type of value to be marshalled
 */
public class JsonbMarshaller<T>
        implements MethodDescriptor.Marshaller<T> {

    private static final Jsonb JSONB = JsonbBuilder.create();

    private final Class<T> clazz;

    /**
     * Construct {@code JsonbMarshaller} instance.
     *
     * @param clazz  the type of object to marshall
     */
    public JsonbMarshaller(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public InputStream stream(T obj) {
        return new ByteArrayInputStream(JSONB.toJson(obj).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public T parse(InputStream in) {
        return JSONB.fromJson(in, clazz);
    }

    /**
     * A {@link MarshallerSupplier} implementation that supplies
     * instances of {@link JsonbMarshaller}.
     */
    @Named("jsonb")
    @ApplicationScoped
    public static class Supplier implements MarshallerSupplier {
        @Override
        public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
            return new JsonbMarshaller<>(clazz);
        }
    }
}
