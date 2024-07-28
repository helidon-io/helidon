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
 * JSON-B type for module in config-metadata.json.
 */
public class CmModule {
    private String module;
    private List<CmType> types;

    /**
     * Required constructor.
     */
    public CmModule() {
    }

    /**
     * Required getter.
     *
     * @return module name
     */
    public String getModule() {
        return module;
    }

    /**
     * Required setter.
     *
     * @param module module name
     */
    public void setModule(String module) {
        this.module = module;
    }

    /**
     * Required getter.
     *
     * @return configured types in this module
     */
    public List<CmType> getTypes() {
        return types;
    }

    /**
     * Required setter.
     *
     * @param types configured types in this module
     */
    public void setTypes(List<CmType> types) {
        this.types = types;
    }
}
