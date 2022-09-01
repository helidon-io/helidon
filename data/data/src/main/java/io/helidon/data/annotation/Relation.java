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
 * Annotation used to indicate a field or method is a relation to another type. Typically not used
 * directly but instead mapped to.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Documented
public @interface Relation {
    /**
     * @return The relation kind.
     */
    Kind value();

    /**
     * @return The inverse property that this relation is mapped by
     */
    String mappedBy() default "";

    /**
     * How to cascade insert/delete operations to the associated entity. Default is none.
     * @return The cascade handling
     */
    Cascade[] cascade() default Cascade.NONE;

    /**
     * Cascade type handling for different associations. Cascading delete is not yet supported.
     */
    enum Cascade {
        /**
         * Cascade all operations.
         */
        ALL,
        /**
         * Cascade insert operations.
         */
        PERSIST,
        /**
         * Cascade update operations.
         */
        UPDATE,
         /**
         * Don't cascade.
         */
        NONE
    }

    /**
     * The relation kind.
     */
    enum Kind {
        /**
         * One to many association.
         */
        ONE_TO_MANY(false),
        /**
         * One to one association.
         */
        ONE_TO_ONE(true),
        /**
         * Many to many association.
         */
        MANY_TO_MANY(false),
        /**
         * Embedded association.
         */
        EMBEDDED(true),

        /**
         * Many to one association.
         */
        MANY_TO_ONE(true);

        private final boolean singleEnded;

        /**
         * @param singleEnded Whether the association is single ended
         */
        Kind(boolean singleEnded) {
            this.singleEnded = singleEnded;
        }

        /**
         * @return Whether the association is single ended
         */
        public boolean isSingleEnded() {
            return singleEnded;
        }
    }
}
