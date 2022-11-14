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

import io.helidon.pico.ServiceInfo;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.tools.creator.ActivatorCodeGenDetail;
import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.ActivatorCodeGenDetail}.
 */
//@AllArgsConstructor
//@Builder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultActivatorCodeGenDetail implements ActivatorCodeGenDetail {
    private final TypeName serviceTypeName;
    private final String body;
    private final ServiceInfo serviceInfo;
    private final Dependencies dependencies;

    protected DefaultActivatorCodeGenDetail(DefaultActivatorCodeGenDetailBuilder builder) {
        this.serviceTypeName = builder.serviceTypeName;
        this.body = builder.body;
        this.serviceInfo = builder.serviceInfo;
        this.dependencies = builder.dependencies;
    }

    public static DefaultActivatorCodeGenDetailBuilder builder() {
        return new DefaultActivatorCodeGenDetailBuilder() {};
    }


    public abstract static class DefaultActivatorCodeGenDetailBuilder {
        private TypeName serviceTypeName;
        private String body;
        private ServiceInfo serviceInfo;
        private Dependencies dependencies;

        public DefaultActivatorCodeGenDetail build() {
            return new DefaultActivatorCodeGenDetail(this);
        }

        public DefaultActivatorCodeGenDetailBuilder serviceTypeName(TypeName val) {
            this.serviceTypeName = val;
            return this;
        }

        public DefaultActivatorCodeGenDetailBuilder body(String val) {
            this.body = val;
            return this;
        }

        public DefaultActivatorCodeGenDetailBuilder serviceInfo(ServiceInfo val) {
            this.serviceInfo = val;
            return this;
        }

        public DefaultActivatorCodeGenDetailBuilder dependencies(Dependencies val) {
            this.dependencies = val;
            return this;
        }
    }

}
