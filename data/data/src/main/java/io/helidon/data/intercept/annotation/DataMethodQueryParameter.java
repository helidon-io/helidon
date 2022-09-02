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
package io.helidon.data.intercept.annotation;

import io.helidon.data.model.DataType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal annotation representing query parameter binding.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Inherited
public @interface DataMethodQueryParameter {

    /**
     * The member name that holds an optional query parameter name.
     */
    String META_MEMBER_NAME = "name";

    /**
     * The member name that holds the data type.
     */
    String META_MEMBER_DATA_TYPE = "dataType";

    /**
     * The member name that holds the parameter index.
     */
    String META_MEMBER_PARAMETER_INDEX = "parameterIndex";

    /**
     * The member name that holds the parameter binding path.
     */
    String META_MEMBER_PARAMETER_BINDING_PATH = "parameterBindingPath";

    /**
     * The member name that holds the property name.
     */
    String META_MEMBER_PROPERTY = "property";

    /**
     * The member name that holds the property path.
     */
    String META_MEMBER_PROPERTY_PATH = "propertyPath";

    /**
     * The member name that holds the converter class.
     */
    String META_MEMBER_CONVERTER = "converter";

    /**
     * The member name that holds the auto-populated value.
     */
    String META_MEMBER_AUTO_POPULATED = "autoPopulated";

    /**
     * The member name that holds requiresPreviousPopulatedValue.
     */
    String META_MEMBER_REQUIRES_PREVIOUS_POPULATED_VALUES = "requiresPreviousPopulatedValue";

    /**
     * The member name that holds expandable.
     */
    String META_MEMBER_EXPANDABLE = "expandable";

    /**
     * @return The query parameter name
     */
    String name() default "";

    /**
     * @return The data type.
     */
    DataType dataType() default DataType.OBJECT;

    /**
     * @return The parameter index
     */
    int parameterIndex() default -1;

    /**
     * @return The parameter binding property path
     */
    String[] parameterBindingPath() default {};

    /**
     * The property name that this parameter is representing.
     * If property is from an association or an embedded entity the value would be empty and `propertyPath` would be set instead.
     *
     * @return The property name.
     */
    String property() default "";

    /**
     * The path to the property that this parameter is representing.
     * Only set if the property is from an association or from an embedded entity.
     *
     * @return The property path.
     */
    String[] propertyPath() default {};

    /**
     * @return The property converter class
     */
    Class[] converter() default {};

    /**
     * @return true if property is auto-populated
     */
    boolean autoPopulated() default false;

    /**
     * @return true if the value has to be previous populated value and not a newly generated.
     */
    boolean requiresPreviousPopulatedValue() default false;
}
