/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;

@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })

/**
 * Marker interface indicating that the corresponding method should have automatic metrics created for it
 * and updated when the method is invoked.
 */
@interface SyntheticRestRequest {

    /**
     * Implementation of the synthetic annotation {@link SyntheticRestRequest} denoting {@code REST.request} methods which
     * should be measured using {@code SimpleTimer} and {@code Counter} metrics.
     */
    class Literal extends AnnotationLiteral<SyntheticRestRequest> implements SyntheticRestRequest {

        private static final long serialVersionUID = 1L;

        private static final Literal INSTANCE = new Literal();

        static Literal getInstance() {
            return INSTANCE;
        }

        private Literal() {
        }
    }
}
