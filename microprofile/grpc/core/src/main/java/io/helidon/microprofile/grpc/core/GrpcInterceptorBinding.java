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
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies that an annotation type is a gRPC interceptor binding type. A gRPC Interceptor binding is
 * used to specify the binding of a gRPC client or server interceptor to target gRPC service and methods.
 * <p>
 * The annotation type that is marked as a binding must be applied to a client of server gRPC interceptor
 * implementation class (marked with the {@code javax.interceptor.Interceptor @Interceptor} annotation to associate that annotation with an interceptor.  The annotation
 * may then be applied instead of, or in addition to, the {@code javax.interceptor.Interceptors @Interceptors} annotation to specify
 * what interceptors are attached to the class or method.
 * <p>
 * The associated annotation type must be associated only with {@link java.lang.annotation.ElementType#TYPE TYPE}s and/or
 * {@link java.lang.annotation.ElementType#METHOD METHOD}s.
 */
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
@Documented
public @interface GrpcInterceptorBinding {
}
