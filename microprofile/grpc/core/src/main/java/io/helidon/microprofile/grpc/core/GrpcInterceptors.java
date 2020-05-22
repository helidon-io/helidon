/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.grpc.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface GrpcInterceptors {
    /**
     * An ordered list of interceptors.
     *
     * @return the ordered list of interceptors
     */
    Class[] value();
}
