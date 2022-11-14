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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import io.helidon.pico.tools.creator.ModuleDetail;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.ToString;

/**
 * The default implementation for {@link io.helidon.pico.tools.creator.ModuleDetail}.
 */
//@AllArgsConstructor
//@Builder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultModuleDetail implements ModuleDetail {
    /*@Singular("serviceProviderActivatorTypeName")*/ private final Set<TypeName> serviceProviderActivatorTypeNames;
    private final String moduleName;
    private final TypeName moduleTypeName;
    private final String moduleBody;
    private final String moduleInfoBody;
    private final SimpleModuleDescriptor descriptor;

    protected DefaultModuleDetail(DefaultModuleDetailBuilder builder) {
        this.serviceProviderActivatorTypeNames = Objects.isNull(builder.serviceProviderActivatorTypeNames)
                ? Collections.emptySet() : Collections.unmodifiableSet(builder.serviceProviderActivatorTypeNames);
        this.moduleName = builder.moduleName;
        this.moduleTypeName = builder.moduleTypeName;
        this.moduleBody = builder.moduleBody;
        this.moduleInfoBody = builder.moduleInfoBody;
        this.descriptor = builder.descriptor;
    }

    public static DefaultModuleDetailBuilder builder() {
        return new DefaultModuleDetailBuilder() {};
    }


    public abstract static class DefaultModuleDetailBuilder {
        private Set<TypeName> serviceProviderActivatorTypeNames;
        private String moduleName;
        private TypeName moduleTypeName;
        private String moduleBody;
        private String moduleInfoBody;
        private SimpleModuleDescriptor descriptor;

        public DefaultModuleDetail build() {
            return new DefaultModuleDetail(this);
        }

        public DefaultModuleDetailBuilder serviceProviderActivatorTypeNames(Collection<TypeName> val) {
            this.serviceProviderActivatorTypeNames = Objects.isNull(val) ? null : new LinkedHashSet<>(val);
            return this;
        }

        public DefaultModuleDetailBuilder moduleName(String val) {
            this.moduleName = val;
            return this;
        }

        public DefaultModuleDetailBuilder moduleTypeName(TypeName val) {
            this.moduleTypeName = val;
            return this;
        }

        public DefaultModuleDetailBuilder moduleBody(String val) {
            this.moduleBody = val;
            return this;
        }

        public DefaultModuleDetailBuilder moduleInfoBody(String val) {
            this.moduleInfoBody = val;
            return this;
        }

        public DefaultModuleDetailBuilder descriptor(SimpleModuleDescriptor val) {
            this.descriptor = val;
            return this;
        }
    }

}
