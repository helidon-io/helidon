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

//import io.micronaut.context.annotation.AliasFor;
import io.helidon.data.model.DataType;

import java.lang.annotation.*;

/**
 * Designates a method or field that is mapped as a persistent property. Typically not used directly
 * but as a meta-annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface MappedProperty {

    /**
     * name of the meta-annotation member to store the embedded property configuration.
     */
    String EMBEDDED_PROPERTIES = "embeddedProperties";

    /**
     * name of the meta-annotation member to store the mapped property (column) alias configuration.
     */
    String ALIAS = "alias";

    /**
     * The destination the property is persisted to. This could be the column name etc. or some external form.
     *
     * @return The destination
     */
    String value() default "";

    /**
     * @return The data type of the property.
     */
//    @AliasFor(annotation = TypeDef.class, member = "type")
    DataType type() default DataType.OBJECT;

    /**
     * @return The converter of the property.
     */
//    @AliasFor(annotation = TypeDef.class, member = "converter")
    Class<?> converter() default Object.class;

    /**
     * @return The converter of the property.
     */
    Class<?> converterPersistedType() default Object.class;

    /**
     * Used to define the mapping. For example in the case of SQL this would be the column definition. Example: BLOB NOT NULL.
     *
     * @return A string-based definition of the property type.
     */
    String definition() default "";

    /**
     * @return The column alias to use for the query
     * @since 3.8.0
     */
    String alias() default "";
}
