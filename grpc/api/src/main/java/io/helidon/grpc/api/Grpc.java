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

package io.helidon.grpc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.grpc.MethodDescriptor;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The Helidon gRPC API.
 */
public interface Grpc {

    /**
     * An annotation used to mark a class as representing a gRPC service.
     */
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    @interface GrpcService {
        /**
         * Obtain the service name.
         *
         * @return the service name
         */
        String value() default "";

        /**
         * Obtain the service version.
         *
         * @return the service version
         */
        int version() default 0;
    }

    /**
     * An annotation to mark a method as representing a unary gRPC method.
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @GrpcMethod(MethodDescriptor.MethodType.UNARY)
    @Documented
    @Inherited
    @interface Unary {
        /**
         * Obtain the name of the method.
         * <p>
         * If not set the name of the actual annotated method is used.
         *
         * @return  name of the method
         */
        String value() default "";
    }

    /**
     * An annotation to mark a class as representing a bi-directional streaming gRPC method.
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @GrpcMethod(MethodDescriptor.MethodType.BIDI_STREAMING)
    @Documented
    @Inherited
    @interface Bidirectional {
        /**
         * Obtain the name of the method.
         * <p>
         * If not set the name of the actual annotated method is used.
         *
         * @return name of the method
         */
        String value() default "";
    }

    /**
     * An annotation to mark a class as representing a client streaming gRPC method.
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @GrpcMethod(MethodDescriptor.MethodType.CLIENT_STREAMING)
    @Documented
    @Inherited
    @interface ClientStreaming {
        /**
         * Obtain the name of the method.
         * <p>
         * If not set the name of the actual annotated method is used.
         *
         * @return name of the method
         */
        String value() default "";
    }

    /**
     * An annotation to mark a class as representing a server streaming gRPC method.
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @GrpcMethod(MethodDescriptor.MethodType.SERVER_STREAMING)
    @Documented
    @Inherited
    @interface ServerStreaming {
        /**
         * Obtain the name of the method.
         * <p>
         * If not set the name of the actual annotated method is used.
         *
         * @return  name of the method
         */
        String value() default "";
    }

    /**
     * Specifies that a class is a gRPC interceptor.
     * <p>
     * The class should be a discoverable CDI bean, for example it could be discovered
     * by being annotated with a valid CDI scope.
     *
     * <p>
     * The annotated class can be a {@link io.grpc.ServerInterceptor}.
     * <pre>
     * &#064;Interceptor
     * &#064;ApplicationScoped
     * public class ValidationInterceptor
     *         implements io.grpc.ServerInterceptor { ... }
     * </pre>
     * <p>
     *
     * Or the annotated class can be a {@link io.grpc.ClientInterceptor}.
     * <pre>
     * &#064;Interceptor
     * &#064;ApplicationScoped
     * public class ValidationInterceptor
     *         implements io.grpc.ClientInterceptor { ... }
     * </pre>
     * <p>
     *
     * This annotation is optional if the {@link io.helidon.grpc.api.Grpc.GrpcInterceptors Interceptors}
     * annotation or is used to associate the interceptor with the target class.
     * It is required when a {@linkplain io.helidon.grpc.api.Grpc.GrpcInterceptorBinding interceptor binding}
     * is used.</p>
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    @Documented
    @interface GrpcInterceptor {
    }

    /**
     * Specifies that an annotation type is a gRPC interceptor binding type. A gRPC Interceptor binding is
     * used to specify the binding of a gRPC client or server interceptor to target gRPC service and methods.
     */
    @Target(ANNOTATION_TYPE)
    @Retention(RUNTIME)
    @Documented
    @interface GrpcInterceptorBinding {
    }

    /**
     * Declares an ordered list of gRPC interceptors for a target gRPC
     * service class or a gRPC service method of a target class.
     * <p>
     * The classes specified must be implementations of either
     * {@link io.grpc.ClientInterceptor} or {@link io.grpc.ServerInterceptor}.
     *
     * <pre>
     * &#064;GrpcService
     * &#064;GrpcInterceptors(ValidationInterceptor.class)
     * public class OrderService { ... }
     * </pre>
     *
     * <pre>
     * &#064;Unary
     * &#064;Interceptors({ValidationInterceptor.class, SecurityInterceptor.class})
     * public void updateOrder(Order order) { ... }
     * </pre>
     */
    @Target({TYPE, ElementType.METHOD})
    @Retention(RUNTIME)
    @interface GrpcInterceptors {
        /**
         * An ordered list of interceptors.
         *
         * @return the ordered list of interceptors
         */
        Class<?>[] value();
    }

    /**
     * An annotation used to annotate a type or method to specify the
     * named marshaller supplier to use for rpc method calls.
     */
    @Target({TYPE, ElementType.METHOD})
    @Retention(RUNTIME)
    @Documented
    @Inherited
    @interface GrpcMarshaller {

        /**
         * The name of the Protocol Buffer marshaller supplier.
         */
        String PROTO = "proto";

        /**
         * The name to use to specify the default marshaller supplier.
         */
        String DEFAULT = "default";

        /**
         * Obtain the type of the {@code MarshallerSupplier} to use.
         * @return  the type of the {@code MarshallerSupplier} to use
         */
        String value() default DEFAULT;
    }

    /**
     * An annotation to mark a class as representing a gRPC service
     * or a method as a gRPC service method.
     */
    @Target({ElementType.METHOD, ANNOTATION_TYPE})
    @Retention(RUNTIME)
    @Documented
    @Inherited
    @interface GrpcMethod {
        /**
         * Obtain the name of the method.
         * <p>
         * If not set the name of the actual annotated method is used.
         *
         * @return  name of the method
         */
        String name() default "";

        /**
         * Obtain the gRPC method type.
         *
         * @return the gRPC method type
         */
        MethodDescriptor.MethodType value();
    }

    /**
     * An annotation to indicate the request type of gRPC method.
     */
    @Target({ElementType.METHOD})
    @Retention(RUNTIME)
    @Documented
    @Inherited
    @interface RequestType {
        /**
         * Obtain the gRPC request type.
         *
         * @return the gRPC request type
         */
        Class<?> value();
    }

    /**
     * An annotation to indicate the response type of a gRPC method.
     */
    @Target({ElementType.METHOD})
    @Retention(RUNTIME)
    @Documented
    @Inherited
    @interface ResponseType {
        /**
         * Obtain the gRPC response type.
         *
         * @return the gRPC response type
         */
        Class<?> value();
    }

    /**
     * An annotation that can be used to specify the name of a configured gRPC channel.
     */
    @Target({TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RUNTIME)
    @interface GrpcChannel {

        /**
         * The name of the configured channel.
         *
         * @return name of the channel
         */
        String value();
    }

    /**
     * An annotation used to mark an injection point for a gRPC service client proxy.
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RUNTIME)
    @interface GrpcProxy {
    }
}
