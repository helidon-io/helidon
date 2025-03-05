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
package io.helidon.data.codegen.query;

import java.util.Objects;

import io.helidon.builder.api.Prototype;

// PropertyBlueprint custom methods
class PropertySupport {

    private PropertySupport() {
        throw new UnsupportedOperationException("No instances of PropertySupport are allowed");
    }

    /**
     * Property name.
     * Builds new {@link String} from stored property name elements.
     *
     * @return the property name
     */
    @Prototype.PrototypeMethod
    static String toString(Property property) {
        if (property.nameParts().isEmpty()) {
            return "";
        }
        return property.name().toString();
    }

    /**
     * Create entity property from single name element.
     *
     * @param namePart the name element
     * @return new instance of entity property
     */
    @Prototype.FactoryMethod
    static Property create(CharSequence namePart) {
        Objects.requireNonNull(namePart, "Value of namePart is null");
        return Property.builder()
                .addNamePart(namePart)
                .build();
    }

    /**
     * Create entity property from an array of name elements.
     *
     * @param nameParts the name elements
     * @return new instance of entity property
     */
    @Prototype.FactoryMethod
    static Property create(CharSequence[] nameParts) {
        Objects.requireNonNull(nameParts, "Value of nameParts is null");
        if (nameParts.length < 1) {
            throw new IllegalArgumentException("Array nameParts must contain at least one element");
        }
        Property.Builder builder = Property.builder();
        for (CharSequence namePart : nameParts) {
            if (namePart.isEmpty()) {
                throw new IllegalArgumentException("Array nameParts contains empty element");
            }
            builder.addNamePart(namePart);
        }
        return builder.build();
    }

}
