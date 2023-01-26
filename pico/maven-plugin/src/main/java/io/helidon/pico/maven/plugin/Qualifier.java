/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.maven.plugin;

import java.util.Map;
import java.util.Optional;

import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

/**
 * Used by {@link ExternalModuleCreatorMojo}, and here in this package due to maven
 * requirements to be in the same package as the mojo.
 * See https://maven.apache.org/guides/mini/guide-configuring-plugins.html#Mapping_Complex_Objects
 */
public class Qualifier implements QualifierAndValue {
    private String qualifierTypeName;
    private String value;

    /**
     * Default constructor.
     */
    public Qualifier() {
    }

    @Override
    public String qualifierTypeName() {
        return qualifierTypeName;
    }

    /**
     * Sets the qualifier type name.
     *
     * @param val the qualifier type name
     */
    public void setQualifierTypeName(
            String val) {
        this.qualifierTypeName = val;
    }

    @Override
    public TypeName typeName() {
        return DefaultTypeName.createFromTypeName(qualifierTypeName);
    }

    @Override
    public Optional<String> value() {
        return Optional.ofNullable(value);
    }

    @Override
    public Optional<String> value(
            String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> values() {
        if (value == null) {
            return Map.of();
        }
        return Map.of("value", value);
    }

    /**
     * Sets the qualifier value.
     *
     * @param val the qualifer value
     */
    @SuppressWarnings("unused")
    public void setValue(
            String val) {
        this.value = val;
    }

}
