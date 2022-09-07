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
 * Defines the query string such as SQL, JPA-QL, Cypher etc that should be executed.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
public @interface Query {

    /**
     * @return The raw query string.
     */
    String value();

    /**
     * @return The count query used for queries that return a {@link io.micronaut.data.model.Page}
     */
    String countQuery() default "";

    /**
     * @return Whether the query is a native query
     */
    boolean nativeQuery() default false;

    /**
     * @return Whether the transactional handling should by default be read-only
     */
    boolean readOnly() default true;
}
