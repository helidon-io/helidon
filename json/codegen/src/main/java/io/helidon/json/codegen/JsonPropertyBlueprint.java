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

package io.helidon.json.codegen;

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * Blueprint JSON property information for handling in code generation.
 * This interface defines the configuration options for how a Java property
 * should be serialized and deserialized to/from JSON.
 */
@Prototype.Blueprint(isPublic = false)
@Prototype.CustomMethods(JsonPropertyCustomMethods.class)
interface JsonPropertyBlueprint {

    /**
     * The name of the field in the Java class.
     *
     * @return optional field name
     */
    Optional<String> fieldName();

    /**
     * The name of the getter method in the Java class.
     *
     * @return optional getter method name
     */
    Optional<String> getterName();

    /**
     * The name of the setter method in the Java class.
     *
     * @return optional setter method name
     */
    Optional<String> setterName();

    /**
     * The name to use for deserialization from JSON.
     *
     * @return optional deserialization name
     */
    Optional<String> deserializationName();

    /**
     * The name to use for serialization to JSON.
     *
     * @return optional serialization name
     */
    Optional<String> serializationName();

    /**
     * The type to use during deserialization.
     *
     * @return optional deserialization type
     */
    Optional<TypeName> deserializationType();

    /**
     * The type to use during serialization.
     *
     * @return optional serialization type
     */
    Optional<TypeName> serializationType();

    /**
     * Custom deserializer type name.
     *
     * @return optional deserializer type
     */
    Optional<TypeName> deserializer();

    /**
     * Custom serializer type name.
     *
     * @return optional serializer type
     */
    Optional<TypeName> serializer();

    /**
     * Date format configuration for this property.
     *
     * @return optional date format info
     */
    Optional<FormatInfo> dateFormat();

    /**
     * Number format configuration for this property.
     *
     * @return optional number format info
     */
    Optional<FormatInfo> numberFormat();

    /**
     * Whether this field should be ignored during JSON processing.
     *
     * @return true if field is ignored
     */
    boolean fieldIgnored();

    /**
     * Whether this property is required.
     *
     * @return true if property is required
     */
    boolean required();

    /**
     * Whether the getter method should be ignored.
     *
     * @return optional boolean indicating if getter is ignored
     */
    Optional<Boolean> getterIgnored();

    /**
     * Whether the setter method should be ignored.
     *
     * @return optional boolean indicating if setter is ignored
     */
    Optional<Boolean> setterIgnored();

    /**
     * Whether this property is used in the constructor/creator.
     *
     * @return true if used in creator
     */
    boolean usedInCreator();

    /**
     * Whether this property is used in the builder pattern.
     *
     * @return true if used in builder
     */
    boolean usedInBuilder();

    /**
     * Whether to write directly to the field instead of using setter.
     *
     * @return true if direct field write is enabled
     */
    boolean directFieldWrite();

    /**
     * Whether to read directly from the field instead of using getter.
     *
     * @return true if direct field read is enabled
     */
    boolean directFieldRead();

    /**
     * Whether this property can be null.
     *
     * @return true if property is nullable
     */
    boolean nullable();

}
