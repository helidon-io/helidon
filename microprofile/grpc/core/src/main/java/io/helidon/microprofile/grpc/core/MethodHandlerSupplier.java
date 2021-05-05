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
package io.helidon.microprofile.grpc.core;

import java.util.function.Supplier;

import io.helidon.grpc.core.MethodHandler;

/**
 * A supplier of {@link io.helidon.grpc.core.MethodHandler}s for {@link AnnotatedMethod}s.
 * <p>
 * Implementation classes may be annotated with {@link javax.annotation.Priority}
 * to influence their priority order when determining which supplier is used if
 * more than one supplier is able to supply a handler for a method.
 * The built-in default suppliers have a priority of zero, which is also the default
 * priority for classes that are not annotated.
 */
public interface MethodHandlerSupplier {

    /**
     * Determine whether this {@link MethodHandlerSupplier} can supply
     * a {@link io.helidon.grpc.core.MethodHandler} for a given method and type.
     *
     * @param method  the {@link AnnotatedMethod} to supply a handler for
     * @return  {@code true} if this supplier can supply a handler for the method
     */
    boolean supplies(AnnotatedMethod method);

    /**
     * Supply a {@link io.helidon.grpc.core.MethodHandler} for a method.
     * @param methodName the gRPC method name
     * @param method     the method to supply a {@link io.helidon.grpc.core.MethodHandler} for
     * @param instance   the supplier to supply the actual call handler
     * @param <ReqT>     the request type
     * @param <RespT>    the response type
     * @return  a {@link io.helidon.grpc.core.MethodHandler} for the method
     * @throws java.lang.NullPointerException if the method is null
     */
    <ReqT, RespT> MethodHandler<ReqT, RespT> get(String methodName, AnnotatedMethod method, Supplier<?> instance);
}
