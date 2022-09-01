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

import io.helidon.data.model.naming.NamingStrategies;
import io.helidon.data.model.naming.NamingStrategy;
import java.lang.annotation.*;

/**
 * Designates a class as being persisted. This is a generic annotation to identify a persistent type
 * and is typically not used directly but rather mapped to.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface MappedEntity {

    /**
     * The destination the type is persisted to. This could be the table name, document name,
     * column name etc. or some external form.
     *
     * @return The destination
     */
    String value() default "";

    /**
     * @return The naming strategy to use.
     */
// Micronaut io.micronaut.context.annotation.AliasFor
//    @AliasFor(annotation = io.helidon.data.annotation.NamingStrategy.class, member = "value")
    Class<? extends NamingStrategy> namingStrategy() default NamingStrategies.UnderScoreSeparatedLowerCase.class;

    /**
     * @return Whether to escape identifiers in generated queries. Defaults to true.
     */
    boolean escape() default true;

    /**
     * @return The alias to use for the query
     */
    String alias() default "";
}
