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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.grpc.MethodDescriptor.MethodType;

/**
 * An annotation to mark a method as representing a
 * unary gRPC method.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@GrpcMethod(type = MethodType.UNARY)
@Documented
@Inherited
public @interface Unary {
    /**
     * Obtain the name of the method.
     * <p>
     * If not set the name of the actual annotated method is used.
     *
     * @return  name of the method
     */
    String name() default "";
}
