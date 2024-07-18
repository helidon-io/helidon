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

package io.helidon.grpc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
 * This annotation is optional if the {@link GrpcInterceptors Interceptors}
 * annotation or is used to associate the interceptor with the target class.
 * It is required when a {@linkplain GrpcInterceptorBinding interceptor binding}
 * is used.</p>
 */
@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface GrpcInterceptor {
}
