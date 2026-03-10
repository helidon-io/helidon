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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.helidon.config.metadata.model.CmModelImpl.CmAllowedValueImpl;
import io.helidon.config.metadata.model.CmModelImpl.CmEnumImpl;
import io.helidon.config.metadata.model.CmModelImpl.CmModuleImpl;
import io.helidon.config.metadata.model.CmModelImpl.CmOptionImpl;
import io.helidon.config.metadata.model.CmModelImpl.CmTypeImpl;
import io.helidon.metadata.hson.Hson;

/**
 * Config metadata model.
 * <p>
 * <b>This class is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
public sealed interface CmModel permits CmModelImpl {

    /**
     * Classpath resource location.
     */
    String LOCATION = "META-INF/helidon/config-metadata.json";

    /**
     * Config modules.
     *
     * @return list of modules
     */
    List<CmModule> modules();

    /**
     * Convert to JSON.
     *
     * @return JSON array, never {@code null}
     */
    Hson.Array toJson();

    /**
     * Create from JSON.
     *
     * @param jsonArray JSON array
     * @return model
     */
    static CmModel fromJson(Hson.Array jsonArray) {
        return CmModelImpl.fromJson(jsonArray);
    }

    /**
     * Create from JSON.
     *
     * @param is input stream
     * @return model
     */
    static CmModel fromJson(InputStream is) {
        return CmModelImpl.fromJson(Hson.parse(is).asArray());
    }

    /**
     * Load all {@value #LOCATION} from the classpath.
     *
     * @param cl class loader
     * @return metadatas
     */
    static CmModel loadAll(ClassLoader cl) {
        try {
            var modules = new ArrayList<CmModule>();
            var files = cl.getResources(LOCATION);
            while (files.hasMoreElements()) {
                URL url = files.nextElement();
                try (InputStream is = url.openStream()) {
                    var jsonArray = Hson.parse(is).asArray();
                    for (var struct : jsonArray.getStructs()) {
                        modules.add(CmModuleImpl.fromJson(struct));
                    }
                }
            }
            return new CmModelImpl(Collections.unmodifiableList(modules));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Create a new model.
     *
     * @param modules modules
     * @return model
     */
    static CmModel of(List<CmModule> modules) {
        return new CmModelImpl(Collections.unmodifiableList(modules));
    }

    /**
     * Config metadata module.
     */
    sealed interface CmModule permits CmModuleImpl {
        /**
         * Module name.
         *
         * @return name, never {@code null}
         */
        String module();

        /**
         * Config types.
         *
         * @return types
         */
        List<CmType> types();

        /**
         * Create a new instance.
         *
         * @param module module
         * @param types  types
         * @return module
         */
        static CmModule of(String module, List<CmType> types) {
            return new CmModuleImpl(module, Collections.unmodifiableList(types));
        }
    }

    /**
     * Config metadata type.
     */
    sealed interface CmType extends Comparable<CmType> permits CmTypeImpl {

        /**
         * Type name.
         *
         * @return type name, never {@code null}
         */
        String type();

        /**
         * Options.
         *
         * @return list of options
         */
        List<CmOption> options();

        /**
         * Description.
         *
         * @return description, never {@code null}
         */
        Optional<String> description();

        /**
         * Optional prefix, if {@link #standalone()} is {@code true}.
         *
         * @return prefix
         */
        Optional<String> prefix();

        /**
         * Indicate whether this is a standalone config object.
         *
         * @return {@code true} if standalone, {@code false} otherwise
         */
        boolean standalone();

        /**
         * Get all the inherited type names.
         *
         * @return list of type names
         */
        List<String> inherits();

        /**
         * Additional types this type provides.
         *
         * @return list of provider type names
         */
        List<String> provides();

        /**
         * Create a new builder.
         *
         * @return builder
         */
        static Builder builder() {
            return new CmTypeImpl.Builder();
        }

        /**
         * {@link CmType} builder.
         */
        sealed interface Builder extends io.helidon.common.Builder<Builder, CmType> permits CmTypeImpl.Builder {

            /**
             * Set the type.
             *
             * @param type type
             * @return this builder
             */
            Builder type(String type);

            /**
             * Set the description.
             *
             * @param description description
             * @return this builder
             */
            Builder description(String description);

            /**
             * Set the prefix.
             *
             * @param prefix prefix
             * @return this builder
             */
            Builder prefix(String prefix);

            /**
             * Set the standalone flag.
             *
             * @param standalone standalone
             * @return this builder
             */
            Builder standalone(boolean standalone);

            /**
             * Set the options.
             *
             * @param options options
             * @return this builder
             */
            Builder options(List<CmOption> options);

            /**
             * Set the inherited type names.
             *
             * @param inherits list of type names
             * @return this builder
             */
            Builder inherits(List<String> inherits);

            /**
             * Set the provided type names.
             *
             * @param provides list of type names
             * @return this builder
             */
            Builder provides(List<String> provides);
        }
    }

    /**
     * Config metadata option.
     */
    sealed interface CmOption extends Comparable<CmOption> permits CmOptionImpl {

        /**
         * Default value for {@link #type()}.
         */
        String DEFAULT_TYPE = "java.lang.String";

        /**
         * Default value for {@link #kind()}.
         */
        Kind DEFAULT_KIND = Kind.VALUE;

        /**
         * Option kind.
         */
        enum Kind {
            /**
             * Leaf node.
             */
            VALUE,
            /**
             * List tree node.
             */
            LIST,
            /**
             * Map tree node.
             */
            MAP
        }

        /**
         * The key of the config option as used in config.
         *
         * @return key, empty when {@link #merge()} is true
         */
        Optional<String> key();

        /**
         * Description, derived from Javadoc.
         *
         * @return description
         */
        Optional<String> description();

        /**
         * The type of the config option.
         *
         * @return type name, never {@code null}
         */
        String type();

        /**
         * Default value.
         *
         * @return default value
         */
        Optional<String> defaultValue();

        /**
         * Indicate whether this option is required.
         * If {@code true}, {@link #defaultValue()} is always empty.
         *
         * @return {@code true} if required, {@code false} otherwise
         */
        boolean required();

        /**
         * Indicate whether this option is experimental.
         *
         * @return {@code true} if experimental, {@code false} otherwise
         */
        boolean experimental();

        /**
         * Indicate whether this option is deprecated.
         *
         * @return {@code true} if deprecated, {@code false} otherwise
         */
        boolean deprecated();

        /**
         * Indicate whether this option is abstract and provided by other module(s).
         *
         * @return {@code true} if a provider, {@code false} otherwise
         */
        boolean provider();

        /**
         * Indicate wether to merge the child nodes directly with parent node without a key.
         *
         * @return {@code true} to merge, {@code false} otherwise
         */
        boolean merge();

        /**
         * Kind of this option.
         *
         * @return Kind, never {@code null}
         */
        Kind kind();

        /**
         * Allowed values.
         *
         * @return list of allowed values
         */
        List<CmAllowedValue> allowedValues();

        /**
         * Create a new builder.
         *
         * @return builder
         */
        static Builder builder() {
            return new CmOptionImpl.Builder();
        }

        /**
         * {@link CmOption} builder.
         */
        sealed interface Builder extends io.helidon.common.Builder<Builder, CmOption>
                permits CmOptionImpl.Builder {

            /**
             * Set the key.
             *
             * @param key key
             * @return this builder
             */
            Builder key(String key);

            /**
             * Set the description.
             *
             * @param description description
             * @return this builder
             */
            Builder description(String description);

            /**
             * Set the type.
             *
             * @param type type
             * @return this builder
             */
            Builder type(String type);

            /**
             * Set the default value.
             *
             * @param defaultValue default value
             * @return this builder
             */
            Builder defaultValue(String defaultValue);

            /**
             * Set the required flag.
             *
             * @param required required flag
             * @return this builder
             */
            Builder required(boolean required);

            /**
             * Set the experimental flag.
             *
             * @param experimental experimental flag
             * @return this builder
             */
            Builder experimental(boolean experimental);

            /**
             * Set the deprecated flag.
             *
             * @param deprecated deprecated flag
             * @return this builder
             */
            Builder deprecated(boolean deprecated);

            /**
             * Set the provider flag.
             *
             * @param provider provider flag
             * @return this builder
             */
            Builder provider(boolean provider);

            /**
             * Set the merge flag.
             *
             * @param merge merge flag
             * @return this builder
             */
            Builder merge(boolean merge);

            /**
             * Set the kind.
             *
             * @param kind kind
             * @return this builder
             */
            Builder kind(Kind kind);

            /**
             * Set the allowed values.
             *
             * @param allowedValues allowed values
             * @return this builder
             */
            Builder allowedValues(List<CmAllowedValue> allowedValues);
        }
    }

    /**
     * Allowed values.
     */
    sealed interface CmAllowedValue extends Comparable<CmAllowedValue> permits CmAllowedValueImpl {
        /**
         * Value.
         *
         * @return value, never {@code null}
         */
        String value();

        /**
         * Description.
         *
         * @return description, never {@code null}
         */
        String description();

        /**
         * Create a new instance.
         *
         * @param value       value
         * @param description value
         * @return CmAllowedValue
         */
        static CmAllowedValue of(String value, String description) {
            return new CmAllowedValueImpl(value, description);
        }
    }

    /**
     * Config metadata enum type.
     */
    interface CmEnum extends Comparable<CmEnum> {

        /**
         * Type name.
         *
         * @return type name, never {@code null}
         */
        String type();

        /**
         * Values.
         *
         * @return values
         */
        List<CmAllowedValue> values();

        /**
         * Create a new instance.
         *
         * @param type   type
         * @param values values
         * @return CmEnum
         */
        static CmEnum of(String type, List<CmAllowedValue> values) {
            return new CmEnumImpl(type, values);
        }
    }
}
