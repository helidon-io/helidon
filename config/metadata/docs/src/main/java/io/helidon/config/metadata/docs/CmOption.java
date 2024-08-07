/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.docs;

import java.util.List;

/**
 * JSON-B type for configured option in config-metadata.json.
 */
public class CmOption {
    private String key;
    private String description;
    private String method;
    private String type = "string";
    private String defaultValue;
    private boolean required = false;
    private boolean experimental = false;
    private boolean deprecated = false;
    private boolean provider = false;
    private String providerType;
    private boolean merge = false;
    private Kind kind = Kind.VALUE;
    private String refType;
    private List<CmAllowedValue> allowedValues;

    /**
     * Required constructor.
     */
    public CmOption() {
    }

    /**
     * Required getter.
     *
     * @return list of allowed values of this option
     */
    public List<CmAllowedValue> getAllowedValues() {
        return allowedValues;
    }

    /**
     * Required setter.
     *
     * @param allowedValues allowed values
     */
    public void setAllowedValues(List<CmAllowedValue> allowedValues) {
        this.allowedValues = allowedValues;
    }

    /**
     * Required getter.
     *
     * @return option kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Required setter.
     *
     * @param kind option kind
     */
    public void setKind(Kind kind) {
        this.kind = kind;
    }

    /**
     * Required getter.
     *
     * @return option config key
     */
    public String getKey() {
        return key;
    }

    /**
     * Required setter.
     *
     * @param key config key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Required getter.
     *
     * @return option description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Required setter.
     *
     * @param description option description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Required getter.
     *
     * @return option method
     */
    public String getMethod() {
        return method;
    }

    /**
     * Required setter.
     *
     * @param method option method
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * Required getter.
     *
     * @return option type
     */
    public String getType() {
        return type;
    }

    /**
     * Required setter.
     *
     * @param type option type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Required getter.
     *
     * @return option default value
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Required setter.
     *
     * @param defaultValue option default value
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Required getter.
     *
     * @return whether this option is required
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Required setter.
     *
     * @param required option required
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Required getter.
     *
     * @return whether this option is experimental
     */
    public boolean isExperimental() {
        return experimental;
    }

    /**
     * Required setter.
     *
     * @param experimental whether this option is experimental
     */
    public void setExperimental(boolean experimental) {
        this.experimental = experimental;
    }

    /**
     * Required getter.
     *
     * @return whether this option is deprecated
     */
    public boolean isDeprecated() {
        return deprecated;
    }

    /**
     * Required setter.
     *
     * @param deprecated whether this option is deprecated
     */
    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * Required getter.
     *
     * @return option refType
     */
    public String getRefType() {
        return refType;
    }

    /**
     * Required setter.
     *
     * @param refType option refType
     */
    public void setRefType(String refType) {
        this.refType = refType;
    }

    /**
     * Required getter.
     *
     * @return whether this option type is a service, and any service implementation may satisfy it
     */
    public boolean isProvider() {
        return provider;
    }

    /**
     * Required setter.
     *
     * @param provider whether this option is a provider
     */
    public void setProvider(boolean provider) {
        this.provider = provider;
    }

    /**
     * Required getter.
     *
     * @return option provider type (service interface)
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * Required setter.
     *
     * @param providerType option provider type (service interface)
     */
    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    /**
     * Required getter.
     *
     * @return whether this option merges child type into this type without a config key
     */
    public boolean isMerge() {
        return merge;
    }

    /**
     * Required setter.
     *
     * @param merge whether to merge this option
     */
    public void setMerge(boolean merge) {
        this.merge = merge;
    }

    @Override
    public String toString() {
        return key + " (" + type + ")" + (merge ? " merged" : "");
    }

    /**
     * Option kind.
     */
    public enum Kind {
        /**
         * Option is a single value (leaf node).
         * Example: server port
         */
        VALUE,
        /**
         * Option is a list of values (either primitive, String or object nodes).
         * Example: cipher suite in SSL, server sockets
         */
        LIST,
        /**
         * Option is a map of strings to primitive type or String.
         * Example: tags in tracing, CDI configuration
         */
        MAP
    }
}
