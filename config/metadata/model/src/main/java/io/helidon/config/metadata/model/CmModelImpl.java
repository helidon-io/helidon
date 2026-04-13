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
package io.helidon.config.metadata.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.helidon.metadata.hson.Hson;

/**
 * {@link CmModel} implementation.
 *
 * @param modules modules
 */
record CmModelImpl(List<CmModule> modules) implements CmModel {

    static CmModel fromJson(Hson.Array array) {
        return new CmModelImpl(
                array.getStructs().stream().map(CmModuleImpl::fromJson).toList());
    }

    @Override
    public Hson.Array toJson() {
        return Hson.Array.create(modules.stream()
                .map(CmModuleImpl::toJson)
                .toList());
    }

    /**
     * {@link CmModule} implementation.
     *
     * @param module module
     * @param types  types
     */
    record CmModuleImpl(String module, List<CmType> types) implements CmModule {

        CmModuleImpl {
            if (module == null || module.isBlank()) {
                throw new IllegalArgumentException("module is required");
            }
        }

        static CmModule fromJson(Hson.Struct struct) {
            return new CmModuleImpl(
                    struct.stringValue("module").orElseThrow(
                            () -> new IllegalStateException("module is not defined")),
                    struct.structArray("types").stream()
                            .flatMap(Collection::stream)
                            .map(CmTypeImpl::fromJson)
                            .toList());
        }

        static Hson.Struct toJson(CmModule o) {
            var that = (CmModuleImpl) o;
            return Hson.structBuilder()
                    .set("module", that.module)
                    .setStructs("types", that.types.stream()
                            .map(CmTypeImpl::toJson)
                            .toList())
                    .build();
        }
    }

    /**
     * {@link CmType} implementation.
     *
     * @param typeName        type
     * @param options     options
     * @param description description
     * @param prefix      prefix
     * @param standalone  standalone
     * @param inherits    inherits
     * @param provides    provides
     */
    record CmTypeImpl(String typeName,
                      List<CmOption> options,
                      Optional<String> description,
                      Optional<String> prefix,
                      boolean standalone,
                      List<String> inherits,
                      List<String> provides) implements CmType {

        CmTypeImpl {
            if (standalone && prefix.isEmpty()) {
                throw new IllegalArgumentException("Standalone type does not have a prefix: " + typeName);
            }
            if (!provides.isEmpty()) {
                if (prefix.isEmpty()) {
                    throw new IllegalArgumentException("Provider implementation does not have a prefix: " + typeName);
                }
            }
        }

        CmTypeImpl(Builder builder) {
            this(builder.type,
                    builder.options,
                    Optional.ofNullable(builder.description),
                    Optional.ofNullable(builder.prefix),
                    builder.standalone,
                    builder.inherits,
                    builder.provides);
        }

        static CmType fromJson(Hson.Struct struct) {
            return new CmTypeImpl(
                    struct.stringValue("type").orElseThrow(
                            () -> new IllegalStateException("type is not defined")),
                    struct.structArray("options").stream()
                            .flatMap(Collection::stream)
                            .map(CmOptionImpl::fromJson)
                            .toList(),
                    struct.stringValue("description"),
                    struct.stringValue("prefix"),
                    struct.booleanValue("standalone").orElse(false),
                    struct.stringArray("inherits").orElseGet(List::of),
                    struct.stringArray("provides").orElseGet(List::of));
        }

        static Hson.Struct toJson(CmType o) {
            var that = (CmTypeImpl) o;
            var builder = Hson.Struct.builder();
            builder.set("type", that.typeName);
            if (that.standalone) {
                builder.set("standalone", true);
            }
            that.description.ifPresent(it -> builder.set("description", it));
            that.prefix.ifPresent(it -> builder.set("prefix", it));
            if (!that.inherits.isEmpty()) {
                builder.setStrings("inherits", that.inherits);
            }
            if (!that.provides.isEmpty()) {
                builder.setStrings("provides", that.provides);
            }
            if (!that.options.isEmpty()) {
                builder.setStructs("options", that.options.stream()
                        .map(CmOptionImpl::toJson)
                        .toList());
            }
            return builder.build();
        }

        @Override
        public int compareTo(CmType o) {
            return typeName.compareTo(o.typeName());
        }

        /**
         * {@link CmType.Builder} implementation.
         */
        static final class Builder implements CmType.Builder {
            private String type;
            private String description;
            private String prefix;
            private boolean standalone;
            private List<CmOption> options = List.of();
            private List<String> inherits = List.of();
            private List<String> provides = List.of();

            @Override
            public Builder type(String type) {
                this.type = type;
                return this;
            }

            @Override
            public Builder description(String description) {
                this.description = description;
                return this;
            }

            @Override
            public Builder prefix(String prefix) {
                this.prefix = prefix;
                return this;
            }

            @Override
            public Builder standalone(boolean standalone) {
                this.standalone = standalone;
                return this;
            }

            @Override
            public Builder options(List<CmOption> options) {
                if (options != null) {
                    this.options = Collections.unmodifiableList(options);
                }
                return this;
            }

            @Override
            public Builder inherits(List<String> inherits) {
                if (inherits != null) {
                    this.inherits = Collections.unmodifiableList(inherits);
                }
                return this;
            }

            @Override
            public Builder provides(List<String> provides) {
                if (provides != null) {
                    this.provides = Collections.unmodifiableList(provides);
                }
                return this;
            }

            @Override
            public CmType build() {
                return new CmTypeImpl(this);
            }
        }
    }

    /**
     * {@link CmOption} implementation.
     *
     * @param key           key
     * @param description   description
     * @param typeName          type
     * @param defaultValue  default value
     * @param required      required
     * @param experimental  experimental
     * @param deprecated    deprecated
     * @param provider      provider
     * @param merge         merge
     * @param kind          kind
     * @param allowedValues allowed
     */
    record CmOptionImpl(Optional<String> key,
                        Optional<String> description,
                        String typeName,
                        Optional<String> defaultValue,
                        boolean required,
                        boolean experimental,
                        boolean deprecated,
                        boolean provider,
                        boolean merge,
                        Kind kind,
                        List<CmAllowedValue> allowedValues) implements CmOption {

        CmOptionImpl {
            if (required && defaultValue.isPresent()) {
                throw new IllegalArgumentException(
                        "Required option cannot have a default value: key=%s, type=%s"
                                .formatted(key, typeName));
            }
        }

        CmOptionImpl(Builder builder) {
            this(Optional.ofNullable(builder.key),
                    Optional.ofNullable(builder.description),
                    builder.type,
                    Optional.ofNullable(builder.defaultValue),
                    builder.required,
                    builder.experimental,
                    builder.deprecated,
                    builder.provider,
                    builder.merge,
                    builder.kind,
                    builder.allowedValues);
        }

        static CmOption fromJson(Hson.Struct struct) {
            return new CmOptionImpl(
                    struct.stringValue("key"),
                    struct.stringValue("description"),
                    struct.stringValue("type").orElse(DEFAULT_TYPE),
                    struct.stringValue("defaultValue"),
                    struct.booleanValue("required").orElse(false),
                    struct.booleanValue("experimental").orElse(false),
                    struct.booleanValue("deprecated").orElse(false),
                    struct.booleanValue("provider").orElse(false),
                    struct.booleanValue("merge").orElse(false),
                    struct.stringValue("kind").map(Kind::valueOf).orElse(DEFAULT_KIND),
                    struct.structArray("allowedValues").stream()
                            .flatMap(Collection::stream)
                            .map(CmAllowedValueImpl::fromJson)
                            .toList());
        }

        static Hson.Struct toJson(CmOption o) {
            var that = (CmOptionImpl) o;
            var builder = Hson.Struct.builder();
            that.key.ifPresent(it -> builder.set("key", it));
            if (!that.typeName.equals(DEFAULT_TYPE)) {
                builder.set("type", that.typeName);
            }
            that.description.ifPresent(it -> builder.set("description", it));
            that.defaultValue.ifPresent(it -> builder.set("defaultValue", it));
            if (that.experimental) {
                builder.set("experimental", true);
            }
            if (that.required) {
                builder.set("required", true);
            }
            if (!that.kind.equals(DEFAULT_KIND)) {
                builder.set("kind", that.kind.name());
            }
            if (that.provider) {
                builder.set("provider", true);
            }
            if (that.deprecated) {
                builder.set("deprecated", true);
            }
            if (that.merge) {
                builder.set("merge", true);
            }
            if (!that.allowedValues.isEmpty()) {
                builder.setStructs("allowedValues", that.allowedValues.stream()
                        .map(CmAllowedValueImpl::toJson)
                        .toList());
            }
            return builder.build();
        }

        @Override
        public int compareTo(CmOption o) {
            var thisKey = key.orElse(null);
            var thatKey = o.key().orElse(null);
            if (thisKey != null) {
                if (thatKey != null) {
                    return thisKey.compareTo(thatKey);
                }
                return -1;
            }
            return 1;
        }

        /**
         * {@link CmOption.Builder} implementation.
         */
        static final class Builder implements CmOption.Builder {

            private String key;
            private String description;
            private String type = DEFAULT_TYPE;
            private String defaultValue;
            private boolean required;
            private boolean experimental;
            private boolean deprecated;
            private boolean provider;
            private boolean merge;
            private Kind kind = DEFAULT_KIND;
            private List<CmAllowedValue> allowedValues = List.of();

            @Override
            public Builder key(String key) {
                this.key = key;
                return this;
            }

            @Override
            public Builder description(String description) {
                this.description = description;
                return this;

            }

            @Override
            public Builder type(String type) {
                this.type = type;
                return this;
            }

            @Override
            public Builder defaultValue(String defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            @Override
            public Builder required(boolean required) {
                this.required = required;
                return this;
            }

            @Override
            public Builder experimental(boolean experimental) {
                this.experimental = experimental;
                return this;
            }

            @Override
            public Builder deprecated(boolean deprecated) {
                this.deprecated = deprecated;
                return this;
            }

            @Override
            public Builder provider(boolean provider) {
                this.provider = provider;
                return this;
            }

            @Override
            public Builder merge(boolean merge) {
                this.merge = merge;
                return this;
            }

            @Override
            public Builder kind(Kind kind) {
                if (kind != null) {
                    this.kind = kind;
                }
                return this;
            }

            @Override
            public Builder allowedValues(List<CmAllowedValue> allowedValues) {
                if (allowedValues != null) {
                    this.allowedValues = Collections.unmodifiableList(allowedValues);
                }
                return this;
            }

            @Override
            public CmOption build() {
                return new CmOptionImpl(this);
            }
        }
    }

    /**
     * {@link CmAllowedValue} implementation.
     *
     * @param value       value
     * @param description description
     */
    record CmAllowedValueImpl(String value, String description) implements CmAllowedValue {

        static CmAllowedValue fromJson(Hson.Struct struct) {
            return new CmAllowedValueImpl(
                    struct.stringValue("value").orElseThrow(
                            () -> new IllegalStateException("value is not defined")),
                    struct.stringValue("description").orElseThrow(
                            () -> new IllegalStateException("description is not defined")));
        }

        static Hson.Struct toJson(CmAllowedValue o) {
            var that = (CmAllowedValueImpl) o;
            return Hson.Struct.builder()
                    .set("value", that.value)
                    .set("description", that.description)
                    .build();
        }

        @Override
        public int compareTo(CmAllowedValue o) {
            return value.compareTo(o.value());
        }
    }

    /**
     * {@link CmEnum} implementation.
     *
     * @param typeName   type
     * @param values values
     */
    record CmEnumImpl(String typeName, List<CmAllowedValue> values) implements CmEnum {

        @Override
        public int compareTo(CmEnum o) {
            return typeName.compareTo(o.typeName());
        }
    }
}
