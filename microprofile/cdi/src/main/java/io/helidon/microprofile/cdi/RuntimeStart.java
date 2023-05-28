/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Run time of the application (as opposed to build time).
 * <p>This is from the point of view of ahead of time compilation, such as when using GraalVM native-image.
 * <p>There are two phases of an application lifecycle:
 * <ul>
 *     <li>{@link BuildTimeStart} - Application should not connect anywhere,
 *     this is to initialize the environment before {@code native-image} is generated. Configuration available is build-time
 *     specific and MUST not be used for runtime operation of this application</li>
 *     <li>Runtime (this annotation) - Application is starting and should set up all resources. Configuration
 *     available at runtime is intended for runtime of this application</li>
 * </ul>
 *
 * <p>Example of usage in a CDI {@link jakarta.enterprise.inject.spi.Extension}:
 * <pre>
 * void initRuntime(@Observes @RuntimeStart io.helidon.config.Config config) {}
 * </pre>
 *
 * End of runtime is equivalent to end of {@link jakarta.enterprise.context.ApplicationScoped}, so there is no need
 * to create additional annotations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
@Target({TYPE, METHOD, PARAMETER, FIELD})
public @interface RuntimeStart {
    /**
     * Annotation literal to use when an annotation instance is needed.
     */
    final class Literal extends AnnotationLiteral<RuntimeStart> implements RuntimeStart {
        /**
         * Singleton instance of a literal of this annotation.
         */
        public static final Literal INSTANCE = new Literal();

        private static final long serialVersionUID = 1L;
    }
}
