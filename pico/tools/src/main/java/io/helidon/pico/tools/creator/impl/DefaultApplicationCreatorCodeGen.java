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

package io.helidon.pico.tools.creator.impl;

import io.helidon.pico.tools.creator.ApplicationCreatorCodeGen;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.ApplicationCreatorCodeGen}.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultApplicationCreatorCodeGen implements ApplicationCreatorCodeGen {
    private final String packageName;
    private final String className;
    private final String classPrefixName;

    protected DefaultApplicationCreatorCodeGen(DefaultApplicationCreatorCodeGenBuilder builder) {
        this.packageName = builder.packageName;
        this.className = builder.className;
        this.classPrefixName = builder.classPrefixName;
    }

    public static DefaultApplicationCreatorCodeGenBuilder builder() {
        return new DefaultApplicationCreatorCodeGenBuilder() {};
    }

    public abstract static class DefaultApplicationCreatorCodeGenBuilder {
        private String packageName;
        private String className;
        private String classPrefixName;

        public DefaultApplicationCreatorCodeGen build() {
            return new DefaultApplicationCreatorCodeGen(this);
        }

        public DefaultApplicationCreatorCodeGenBuilder packageName(String val) {
            this.packageName = val;
            return this;
        }

        public DefaultApplicationCreatorCodeGenBuilder className(String val) {
            this.className = val;
            return this;
        }

        public DefaultApplicationCreatorCodeGenBuilder classPrefixName(String val) {
            this.classPrefixName = val;
            return this;
        }
    }

}
