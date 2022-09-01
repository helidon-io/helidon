/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.annotation;

import java.lang.annotation.*;

/**
 * Designates a property as a generated value. Typically not used
 * directly but instead mapped to via annotation such as {@code javax.persistence.GeneratedValue}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Documented
public @interface GeneratedValue {

    /**
     * The generation type.
     * @return The generation type.
     */
    Type value() default Type.AUTO;

    /**
     * In the case of sequence generators if you wish to define statement that creates the sequence,
     * you can do so here.
     *
     * @return The sequence definition
     */
    String definition() default "";

    /**
     * In the case of sequence generators if you wish to alter the name of the sequence generator do so with this member.
     * @return The name to use to reference the sequence generator
     */
    String ref() default "";

    /**
     * The type of generation.
     */
    enum Type {
        /**
         * Automatic selection.
         */
        AUTO,
        /**
         * Use a sequence.
         */
        SEQUENCE,
        /**
         * Use identity generation.
         */
        IDENTITY,
        /**
         * UUID generation strategy.
         */
        UUID
    }
}
