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

import io.helidon.data.annotation.repeatable.JoinSpecifications;

import java.lang.annotation.*;

/**
 * A @Join defines how a join for a particular association path should be generated.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Repeatable(JoinSpecifications.class)
public @interface Join {

    /**
     * @return The path to join.
     */
    String value();

    /**
     * @return The join type. For JPA this is JOIN FETCH.
     */
    Type type() default Type.FETCH;

    /**
     * @return The alias prefix to use for the join
     */
    String alias() default "";

    /**
     * The type of join.
     */
    enum Type {
        DEFAULT,
        LEFT,
        LEFT_FETCH,
        RIGHT,
        RIGHT_FETCH,
        FETCH,
        INNER,
        OUTER
    }
}
