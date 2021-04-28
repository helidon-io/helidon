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

package io.helidon.grpc.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Logger;

import javax.inject.Named;
import javax.inject.Singleton;

import io.helidon.config.Config;

import io.grpc.MethodDescriptor;

/**
 * An implementation of a gRPC {@link MethodDescriptor.Marshaller} that
 * uses Java serialization.
 * <p>
 * This marshaller is disabled by default starting with Helidon 2.3.0, and must be
 * enabled explicitly by setting {@code grpc.marshaller.java.enabled} configuration
 * property to {@code true} before it can be used.
 *
 * @param <T>  the type of value to to be marshalled
 * @deprecated Not suitable for production use, and will be removed in 3.0.
 *             Please use one of the other supported marshallers instead.
 * @see io.helidon.grpc.core.JsonbMarshaller
 * @see io.helidon.grpc.core.MarshallerSupplier.ProtoMarshallerSupplier
 */
@Singleton
@Named(JavaMarshaller.NAME)
@Deprecated(since = "2.3.0", forRemoval = true)
public class JavaMarshaller<T>
        implements MethodDescriptor.Marshaller<T> {

    private static final Logger LOG = Logger.getLogger(JavaMarshaller.class.getName());
    private static final boolean ENABLED = Config.create().get("grpc.marshaller.java.enabled").asBoolean().orElse(false);

    /**
     * The name of this marshaller.
     */
    public static final String NAME = "java";

    /**
     * A singleton instance of a {@link JavaMarshaller}.
     */
    public static final JavaMarshaller INSTANCE = new JavaMarshaller();

    /**
     * Obtain the singleton instance of a {@link JavaMarshaller}.
     * @param <T> the type the marshaller supports
     * @return an instance of a {@link JavaMarshaller}
     */
    @SuppressWarnings("unchecked")
    public static <T> JavaMarshaller<T> instance() {
        return INSTANCE;
    }

    /**
     * Construct JavaMarshaller instance.
     */
    JavaMarshaller() {
        if (ENABLED) {
            LOG.warning("JavaMarshaller is not recommended for production use. "
                                + "It has been deprecated since Helidon 2.3.0 and it will be removed in 3.0");
        } else {
            throw new UnsupportedOperationException(
                    "JavaMarshaller must be explicitly enabled via grpc.marshaller.java.enabled configuration property");
        }
    }

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
     * A {@link MarshallerSupplier} implementation that supplies
     * instances of {@link JavaMarshaller}.
     */
    @Named("java")
    public static class Supplier
            implements MarshallerSupplier {
        @Override
        public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
            return new JavaMarshaller<>();
        }
    }
}
