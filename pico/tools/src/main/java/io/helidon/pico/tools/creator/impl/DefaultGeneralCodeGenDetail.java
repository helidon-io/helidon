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

import io.helidon.pico.tools.creator.GeneralCodeGenDetail;
import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.GeneralCodeGenDetail}.
 */
//@AllArgsConstructor
//@Builder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultGeneralCodeGenDetail implements GeneralCodeGenDetail {

    private final TypeName serviceTypeName;
    private final String body;

    protected DefaultGeneralCodeGenDetail(DefaultGeneralCodeGenDetailBuilder builder) {
        this.serviceTypeName = builder.serviceTypeName;
        this.body = builder.body;
    }

    public static DefaultGeneralCodeGenDetailBuilder builder() {
        return new DefaultGeneralCodeGenDetailBuilder() {};
    }

    public abstract static class DefaultGeneralCodeGenDetailBuilder {
        private TypeName serviceTypeName;
        private String body;

        public DefaultGeneralCodeGenDetail build() {
            return new DefaultGeneralCodeGenDetail(this);
        }

        public DefaultGeneralCodeGenDetailBuilder serviceTypeName(TypeName val) {
            this.serviceTypeName = val;
            return this;
        }

        public DefaultGeneralCodeGenDetailBuilder body(String val) {
            this.body = val;
            return this;
        }
    }

}
