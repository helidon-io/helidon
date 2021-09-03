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

package io.helidon.integrations.micronaut.cdi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;

import io.micronaut.core.annotation.Internal;

/**
 * Used to add interceptors to existing CDI beans to be intercepted by Micronaut interceptors.
 * DO NOT USE DIRECTLY. Usage is computed by this CDI extension.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Internal
public @interface MicronautIntercepted {
    /**
     * Literal used to obtain an instance of the annotation.
     */
    class Literal extends AnnotationLiteral<MicronautIntercepted> implements MicronautIntercepted {
        /**
         * Annotation literal. As this annotation does not have any properties, the same literal can be reused.
         */
        public static final MicronautIntercepted INSTANCE = new Literal();
    }
}
