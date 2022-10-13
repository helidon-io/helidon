/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * Default implementation for {@link TypeInfo}.
 */
public class DefaultTypeInfo implements TypeInfo {
    private final TypeName typeName;
    private final String typeKind;
    private final List<AnnotationAndValue> annotations;
    private final List<TypedElementName> elementInfo;
    private final TypeInfo superTypeInfo;

    protected DefaultTypeInfo(Builder builder) {
        this.typeName = builder.typeName;
        this.typeKind = builder.typeKind;
        this.annotations = Objects.isNull(builder.annotations)
                ? Collections.emptyList() : Collections.unmodifiableList(new LinkedList<>(builder.annotations));
        this.elementInfo = Objects.isNull(builder.elementInfo)
                ? Collections.emptyList() : Collections.unmodifiableList(new LinkedList<>(builder.elementInfo));
        this.superTypeInfo = builder.superTypeInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeName typeName() {
        return typeName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String typeKind() {
        return typeKind;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AnnotationAndValue> annotations() {
        return annotations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TypedElementName> elementInfo() {
        return elementInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TypeInfo> superTypeInfo() {
        return Optional.ofNullable(superTypeInfo);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + toStringInner() + ")";
    }

    protected String toStringInner() {
        return "typeName=" + typeName()
                + ", elementInfo=" + elementInfo()
                + ", superTypeInfo=" + superTypeInfo();
    }


    /**
     * Creates a new builder for this type.
     *
     * @return the fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * Builder for this type.
     */
    public static class Builder {
        private TypeName typeName;
        private String typeKind;
        private List<AnnotationAndValue> annotations;
        private List<TypedElementName> elementInfo;
        private Map<TypedElementName, String> defaultValueMap;
        private TypeInfo superTypeInfo;

        /**
         * Sets the typeName to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder typeName(TypeName val) {
            this.typeName = val;
            return this;
        }

        /**
         * Sets the typeKind to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder typeKind(String val) {
            this.typeKind = val;
            return this;
        }

        /**
         * Sets the annotations to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder annotations(Collection<AnnotationAndValue> val) {
            this.annotations = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        /**
         * Adds a single annotation val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addAnnotation(AnnotationAndValue val) {
            if (Objects.isNull(annotations)) {
                annotations = new LinkedList<>();
            }
            annotations.add(Objects.requireNonNull(val));
            return this;
        }

        /**
         * Sets the elementInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder elementInfo(Collection<TypedElementName> val) {
            this.elementInfo = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        /**
         * Adds a single elementInfo val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addElementInfo(TypedElementName val) {
            if (Objects.isNull(elementInfo)) {
                elementInfo = new LinkedList<>();
            }
            elementInfo.add(val);
            return this;
        }

        /**
         * Sets the defaultValueMap to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder defaultValueMap(Map<TypedElementName, String> val) {
            this.defaultValueMap = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return this;
        }

        /**
         * Adds a singular defaultValue val.
         *
         * @param key the key
         * @param val the value
         * @return this fluent builder
         */
        public Builder addDefaultValue(TypedElementName key, String val) {
            if (Objects.isNull(defaultValueMap)) {
                defaultValueMap = new LinkedHashMap<>();
            }
            defaultValueMap.put(key, val);
            return this;
        }

        /**
         * Sets the superTypeInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder superTypeInfo(TypeInfo val) {
            this.superTypeInfo = val;
            return this;
        }

        /**
         * Builds the instance.
         *
         * @return the built instance
         */
        public DefaultTypeInfo build() {
            return new DefaultTypeInfo(this);
        }
    }

}
