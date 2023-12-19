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

package io.helidon.inject.maven.plugin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Qualifier;

/**
 * Used by {@link ExternalModuleCreatorMojo}, and here in this package due to maven
 * requirements to be in the same package as the mojo.
 * See https://maven.apache.org/guides/mini/guide-configuring-plugins.html#Mapping_Complex_Objects
 */
public class QualifierConfig implements Qualifier {
    private String qualifierTypeName;
    private String value;

    /**
     * Default constructor.
     */
    public QualifierConfig() {
    }

    /**
     * Sets the qualifier type name.
     *
     * @param val the qualifier type name
     */
    public void setQualifierTypeName(String val) {
        this.qualifierTypeName = val;
    }

    @Override
    public TypeName typeName() {
        return TypeName.create(qualifierTypeName);
    }

    @Override
    public Optional<String> value() {
        return Optional.ofNullable(value);
    }

    @Override
    public Optional<String> getValue(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> values() {
        if (value == null) {
            return Map.of();
        }
        return Map.of("value", value);
    }

    @Override
    public int compareTo(Annotation o) {
        return this.typeName().compareTo(o.typeName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName(), values());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Qualifier other)) {
            return false;
        }
        return Objects.equals(typeName(), other.typeName()) && Objects.equals(values(), other.values());
    }

    /**
     * Sets the qualifier value.
     *
     * @param val the qualifer value
     */
    @SuppressWarnings("unused")
    public void setValue(String val) {
        this.value = val;
    }

}
