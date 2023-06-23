/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation for {@link TypeInfo}.
 */
public class TypeInfoDefault implements TypeInfo {
    private final TypeName typeName;
    private final String typeKind;
    private final List<AnnotationAndValue> annotations;
    private final List<TypedElementInfo> elementInfo;
    private final List<TypedElementInfo> otherElementInfo;
    private final Map<TypeName, String> referencedModuleNames;
    private final Map<TypeName, List<AnnotationAndValue>> referencedTypeNamesToAnnotations;
    private final TypeInfo superTypeInfo;
    private final List<TypeInfo> interfaceTypeInfo;
    private final Set<String> modifierNames;

    /**
     * Default constructor taking the builder as an argument.
     *
     * @param b the builder
     * @see #builder()
     */
    protected TypeInfoDefault(Builder b) {
        this.typeName = b.typeName;
        this.typeKind = b.typeKind;
        this.annotations = List.copyOf(b.annotations);
        this.elementInfo = List.copyOf(b.elementInfo);
        this.otherElementInfo = List.copyOf(b.otherElementInfo);
        this.superTypeInfo = b.superTypeInfo;
        this.modifierNames = Set.copyOf(b.modifierNames);
        this.referencedModuleNames = Map.copyOf(b.referencedModuleNames);
        this.referencedTypeNamesToAnnotations = Map.copyOf(b.referencedTypeNamesToAnnotations);
        this.interfaceTypeInfo = List.copyOf(b.interfaceTypeInfo);
    }

    /**
     * Creates a new builder for this type.
     *
     * @return the fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public TypeName typeName() {
        return typeName;
    }

    @Override
    public String typeKind() {
        return typeKind;
    }

    @Override
    public List<AnnotationAndValue> annotations() {
        return annotations;
    }

    @Override
    public List<TypedElementInfo> interestingElementInfo() {
        return elementInfo;
    }

    @Override
    public List<TypedElementInfo> otherElementInfo() {
        return otherElementInfo;
    }

    @Override
    public Map<TypeName, String> referencedModuleNames() {
        return referencedModuleNames;
    }

    @Override
    public Map<TypeName, List<AnnotationAndValue>> referencedTypeNamesToAnnotations() {
        return referencedTypeNamesToAnnotations;
    }

    @Override
    public List<TypeInfo> interfaceTypeInfo() {
        return interfaceTypeInfo;
    }

    @Override
    public Optional<TypeInfo> superTypeInfo() {
        return Optional.ofNullable(superTypeInfo);
    }

    @Override
    public Set<String> modifierNames() {
        return modifierNames;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + toStringInner() + ")";
    }

    /**
     * Calculates the inner portion of the {@link #toString()} result.
     *
     * @return the inner portion of the toString result
     */
    protected String toStringInner() {
        return "typeName=" + typeName()
                + ", elementInfo=" + allElementInfo()
                + ", annotations=" + annotations()
                + ", superTypeInfo=" + superTypeInfo()
                + ", modifierNames=" + modifierNames();
    }

    /**
     * Builder for this type.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, TypeInfoDefault> {
        private final List<AnnotationAndValue> annotations = new ArrayList<>();
        private final List<TypedElementInfo> elementInfo = new ArrayList<>();
        private final List<TypedElementInfo> otherElementInfo = new ArrayList<>();
        private final Map<TypeName, String> referencedModuleNames = new LinkedHashMap<>();
        private final Map<TypeName, List<AnnotationAndValue>> referencedTypeNamesToAnnotations = new LinkedHashMap<>();
        private final List<TypeInfo> interfaceTypeInfo = new ArrayList<>();
        private final Set<String> modifierNames = new LinkedHashSet<>();
        private TypeName typeName;
        private String typeKind;
        private TypeInfo superTypeInfo;

        /**
         * Default constructor.
         */
        protected Builder() {
        }

        /**
         * Builds the instance.
         *
         * @return the built instance
         */
        @Override
        public TypeInfoDefault build() {
            return new TypeInfoDefault(this);
        }

        /**
         * Sets the typeName to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder typeName(TypeName val) {
            this.typeName = val;
            return identity();
        }

        /**
         * Sets the typeKind to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder typeKind(String val) {
            this.typeKind = val;
            return identity();
        }

        /**
         * Sets the annotations to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder annotations(Collection<AnnotationAndValue> val) {
            Objects.requireNonNull(val);
            this.annotations.clear();
            this.annotations.addAll(val);
            return identity();
        }

        /**
         * Adds a single annotation val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addAnnotation(AnnotationAndValue val) {
            Objects.requireNonNull(val);
            annotations.add(Objects.requireNonNull(val));
            return identity();
        }

        /**
         * Sets the interestingElementInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder interestingElementInfo(Collection<TypedElementInfo> val) {
            Objects.requireNonNull(val);
            this.elementInfo.clear();
            this.elementInfo.addAll(val);
            return identity();
        }

        /**
         * Adds a single interestingElementInfo val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addInterestingElementInfo(TypedElementInfo val) {
            Objects.requireNonNull(val);
            elementInfo.add(val);
            return identity();
        }

        /**
         * Sets the otherElementInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder otherElementInfo(Collection<TypedElementInfo> val) {
            Objects.requireNonNull(val);
            this.otherElementInfo.clear();
            this.otherElementInfo.addAll(val);
            return identity();
        }

        /**
         * Adds a single otherElementInfo val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addOtherElementInfo(TypedElementInfo val) {
            Objects.requireNonNull(val);
            otherElementInfo.add(val);
            return identity();
        }

        /**
         * Adds a single referencedModuleName val.
         *
         * @param key the key
         * @param val the value
         * @return this fluent builder
         */
        public Builder addReferencedModuleName(TypeName key, String val) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(val);
            this.referencedModuleNames.put(key, val);
            return identity();
        }

        /**
         * Sets the referencedTypeNamesToAnnotations to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder referencedModuleNames(Map<TypeName, String> val) {
            Objects.requireNonNull(val);
            this.referencedModuleNames.clear();
            this.referencedModuleNames.putAll(val);
            return identity();
        }

        /**
         * Sets the referencedTypeNamesToAnnotations to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder referencedTypeNamesToAnnotations(Map<TypeName, List<AnnotationAndValue>> val) {
            Objects.requireNonNull(val);
            this.referencedTypeNamesToAnnotations.clear();
            this.referencedTypeNamesToAnnotations.putAll(val);
            return identity();
        }

        /**
         * Adds a single referencedTypeNamesToAnnotations val.
         *
         * @param key the key
         * @param val the value
         * @return this fluent builder
         */
        public Builder addReferencedTypeNamesToAnnotations(TypeName key, AnnotationAndValue val) {
            return addReferencedTypeNamesToAnnotations(key, List.of(val));
        }

        /**
         * Adds a collection of referencedTypeNamesToAnnotations values.
         *
         * @param key the key
         * @param vals the values
         * @return this fluent builder
         */
        public Builder addReferencedTypeNamesToAnnotations(TypeName key, Collection<AnnotationAndValue> vals) {
            Objects.requireNonNull(vals);
            referencedTypeNamesToAnnotations.compute(key, (k, v) -> {
                if (v == null) {
                    v = new ArrayList<>();
                }
                v.addAll(vals);
                return v;
            });
            return identity();
        }

        /**
         * Sets the interfaceTypeInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder interfaceTypeInfo(List<TypeInfo> val) {
            Objects.requireNonNull(val);
            this.interfaceTypeInfo.clear();
            this.interfaceTypeInfo.addAll(val);
            return identity();
        }

        /**
         * Adds a single interfaceTypeInfo val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addInterfaceTypeInfo(TypeInfo val) {
            Objects.requireNonNull(val);
            this.interfaceTypeInfo.add(val);
            return identity();
        }

        /**
         * Sets the modifiers to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder modifierNames(Collection<String> val) {
            Objects.requireNonNull(val);
            this.modifierNames.clear();
            this.modifierNames.addAll(val);
            return identity();
        }

        /**
         * Adds a single modifier val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addModifierName(String val) {
            Objects.requireNonNull(val);
            modifierNames.add(val);
            return identity();
        }

        /**
         * Sets the superTypeInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder superTypeInfo(TypeInfo val) {
            Objects.requireNonNull(val);
            this.superTypeInfo = val;
            return identity();
        }
    }

}
