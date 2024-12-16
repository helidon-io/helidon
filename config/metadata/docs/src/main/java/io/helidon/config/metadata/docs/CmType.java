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
 * JSON-B type for configured type in config-metadata.json.
 */
public class CmType {
    private String type;
    private String title;
    private String annotatedType;
    private List<CmOption> options;
    private String description;
    private String prefix;
    private boolean standalone;
    private List<String> inherits;
    private List<String> producers;
    private List<String> provides;
    private String typeReference;

    /**
     * Required constructor.
     */
    public CmType() {
    }

    /**
     * Required getter.
     *
     * @return type description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Required setter.
     *
     * @param description type description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Required getter.
     *
     * @return type config prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Required setter.
     *
     * @param prefix type config prefix
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Required getter.
     *
     * @return whether this is a standalone configuration type
     */
    public boolean isStandalone() {
        return standalone;
    }

    /**
     * Required setter.
     *
     * @param standalone whether this is a standalone configuration type
     */
    public void setStandalone(boolean standalone) {
        this.standalone = standalone;
    }

    /**
     * Required getter.
     *
     * @return list of inherited types
     */
    public List<String> getInherits() {
        return inherits;
    }

    /**
     * Required setter.
     *
     * @param inherits types to inherit
     */
    public void setInherits(List<String> inherits) {
        this.inherits = inherits;
    }

    /**
     * Required getter.
     *
     * @return list of producer methods
     */
    public List<String> getProducers() {
        return producers;
    }

    /**
     * Required setter.
     *
     * @param producers list of producer methods
     */
    public void setProducers(List<String> producers) {
        this.producers = producers;
    }

    /**
     * Required getter.
     *
     * @return type name
     */
    public String getType() {
        return type;
    }

    /**
     * Required setter.
     *
     * @param type type name
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Required getter.
     *
     * @return type that is annotated
     */
    public String getAnnotatedType() {
        return annotatedType;
    }

    /**
     * Required setter.
     *
     * @param annotatedType the annotated type
     */
    public void setAnnotatedType(String annotatedType) {
        this.annotatedType = annotatedType;
    }

    /**
     * Required getter.
     *
     * @return type list of options of this type
     */
    public List<CmOption> getOptions() {
        return options;
    }

    /**
     * Required setter.
     *
     * @param options type options
     */
    public void setOptions(List<CmOption> options) {
        this.options = options;
    }

    /**
     * Required getter.
     *
     * @return type list of provided services
     */
    public List<String> getProvides() {
        return provides;
    }

    /**
     * Required setter.
     *
     * @param provides provided services
     */
    public void setProvides(List<String> provides) {
        this.provides = provides;
    }

    /**
     * Whether this type represents a service implementation.
     *
     * @return whether this type provides an implementation of a service
     */
    public boolean hasProvides() {
        return provides != null && !provides.isEmpty();
    }

    /**
     * Required getter.
     *
     * @return type reference
     */
    public String getTypeReference() {
        return typeReference;
    }

    /**
     * Required setter.
     *
     * @param typeReference type reference
     */
    public void setTypeReference(String typeReference) {
        this.typeReference = typeReference;
    }

    /**
     * Required getter.
     *
     * @return type title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Required setter.
     *
     * @param title type title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return getType();
    }
}
