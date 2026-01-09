/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.AccessorStyle;

/**
 * Container class for JSON binding annotations.
 */
public final class Json {

    private Json() {
    }

    /**
     * Marks a class as a JSON entity that should have converter generated.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Entity {

        /**
         * Defines the accessor method style.
         *
         * @return the accessor style to use
         */
        AccessorStyle accessorStyle() default AccessorStyle.AUTO;

    }

    /**
     * Specifies a custom deserializer class for a type, field, method, or parameter.
     * The class specified by this annotation must have a public or package-private no-arg constructor.
     * Helidon uses this constructor to instantiate the deserializer.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface Deserializer {

        /**
         * The deserializer class to use.
         *
         * @return the deserializer class
         */
        Class<? extends JsonDeserializer<?>> value();

    }

    /**
     * Specifies a custom serializer class for a type, field, or method.
     * The class specified by this annotation must have a public or package-private no-arg constructor.
     * Helidon uses this constructor to instantiate the serializer.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD})
    public @interface Serializer {

        /**
         * The serializer class to use.
         *
         * @return the serializer class
         */
        Class<? extends JsonSerializer<?>> value();

    }

    /**
     * Specifies a custom converter class for a type, field, method, or parameter.
     * The class specified by this annotation must have a public or package-private no-arg constructor.
     * Helidon uses this constructor to instantiate the converter.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface Converter {

        /**
         * The converter class to use.
         *
         * @return the converter class
         */
        Class<? extends JsonConverter<?>> value();

    }

    /**
     * Customizes the JSON property name for a field, method, or parameter.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface Property {

        /**
         * The custom property name to use in JSON.
         *
         * @return the property name
         */
        String value();

    }

    /**
     * Excludes fields or methods from JSON serialization/deserialization.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface Ignore {

        /**
         * Whether to ignore this field/method.
         *
         * @return true to ignore, false to include
         */
        boolean value() default true;

    }

    /**
     * Marks properties as required during deserialization.
     * If a required property is missing, deserialization will fail.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface Required {
    }

    /**
     * Controls whether null values are included in JSON output.
     * By default, null values are omitted from JSON output.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
    public @interface SerializeNulls {

        /**
         * Whether to serialize null values.
         *
         * @return true to include nulls, false to omit them
         */
        boolean value() default true;

    }

    /**
     * Marks constructors or factory methods for object creation during deserialization.
     * Used for immutable objects or custom instantiation logic.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
    public @interface Creator {
    }

    /**
     * Controls the order of properties in JSON output.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface PropertyOrder {

        /**
         * The property ordering strategy to use.
         *
         * @return the ordering strategy
         */
        Order value();

    }

    /**
     * Provide information about a builder class for object construction.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface BuilderInfo {

        /**
         * The builder class to use.
         *
         * @return the builder class
         */
        Class<?> value();

        /**
         * The method prefix for builder methods.
         *
         * @return the method prefix
         */
        String methodPrefix() default "";

        /**
         * The build method name.
         *
         * @return the build method name
         */
        String buildMethod() default "build";

    }

    /**
     * Controls behavior when unknown properties are encountered during deserialization.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface FailOnUnknown {

        /**
         * Whether to fail on unknown properties during deserialization.
         *
         * @return true to fail, false to ignore them
         */
        boolean value() default true;

    }

}
