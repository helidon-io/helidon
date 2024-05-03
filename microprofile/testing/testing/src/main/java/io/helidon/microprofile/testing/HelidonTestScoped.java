/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.io.Serial;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.util.AnnotationLiteral;

/**
 * CDI scope used for the test class.
 */
@NormalScope
@Retention(RetentionPolicy.RUNTIME)
@interface HelidonTestScoped {

    /**
     * Annotation literal.
     */
    @SuppressWarnings("ALL")
    final class Literal extends AnnotationLiteral<HelidonTestScoped> implements HelidonTestScoped {

        static final Literal INSTANCE = new Literal();

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
