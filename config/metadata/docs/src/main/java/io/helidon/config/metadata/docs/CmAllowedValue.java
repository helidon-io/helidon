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

/**
 * JSON-B type for Allowed values in config-metadata.json.
 */
public class CmAllowedValue {
    private String value;
    private String description;

    /**
     * Required constructor.
     */
    public CmAllowedValue() {
    }

    /**
     * Required getter.
     *
     * @return allowed value
     */
    public String getValue() {
        return value;
    }

    /**
     * Required setter.
     *
     * @param value allowed value
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Required getter.
     *
     * @return description of the allowed value
     */
    public String getDescription() {
        return description;
    }

    /**
     * Required setter.
     *
     * @param description allowed value description
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
