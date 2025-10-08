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

package io.helidon.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.helidon.service.registry.Interception;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Validation annotations and related types.
 */
public final class Validation {
    private Validation() {
    }

    /**
     * Definition of a constraint.
     * A validator is discovered from the service registry - the first instance that has
     * {@link io.helidon.validation.spi.ConstraintValidator} contract, and is named as the
     * fully qualified name of the constraint annotation.
     */
    @Documented
    @Target(ANNOTATION_TYPE)
    @Retention(CLASS)
    @Interception.Intercepted
    public @interface Constraint {
    }

    /**
     * This type will contain validations on getters (or record components) that cannot be intercepted.
     * Such a type will have a generated validator that will be used by interceptors.
     * <p>
     * The generated type will be a {@link io.helidon.validation.spi.TypeValidator}
     * named with the fully qualified class name of the annotated type.
     */
    @Documented
    @Target(TYPE)
    public @interface Validated {
    }
}
