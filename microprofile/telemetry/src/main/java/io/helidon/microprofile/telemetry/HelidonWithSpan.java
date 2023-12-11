/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import java.io.Serial;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;


/**
 * Annotation to trigger interceptor for {@link io.opentelemetry.instrumentation.annotations.WithSpan}
 * annotations.
 */

@InterceptorBinding
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface HelidonWithSpan {

    // Literal to create HelidonWithSpan annotation.
    class Literal extends AnnotationLiteral<HelidonWithSpan> implements HelidonWithSpan {
        static final Literal INSTANCE = new Literal();
        @Serial
        private static final long serialVersionUID = 5910339603347723544L;

        private Literal() {
        }
    }
}
