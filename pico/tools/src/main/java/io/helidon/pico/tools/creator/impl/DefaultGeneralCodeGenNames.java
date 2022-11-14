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

import java.util.Objects;

import io.helidon.pico.tools.creator.GeneralCodeGenNames;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.TemplateHelper;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.GeneralCodeGenNames}.
 */
//@SuperBuilder(toBuilder = true)
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultGeneralCodeGenNames implements GeneralCodeGenNames {
    /*@Builder.Default*/ private final String templateName/* = TemplateHelper.DEFAULT_TEMPLATE_NAME*/;
    /*@Builder.Default*/ private final String moduleName/* = SimpleModuleDescriptor.DEFAULT_MODULE_NAME*/;
    private final String packageName;

    protected DefaultGeneralCodeGenNames(DefaultGeneralCodeGenNamesBuilder builder) {
        this.templateName = Objects.nonNull(builder.templateName) ? builder.templateName : TemplateHelper.DEFAULT_TEMPLATE_NAME;
        this.moduleName = Objects.nonNull(builder.moduleName) ? builder.moduleName : SimpleModuleDescriptor.DEFAULT_MODULE_NAME;
        this.packageName = builder.packageName;
    }

    public static DefaultGeneralCodeGenNamesBuilder<? extends DefaultGeneralCodeGenNames, ? extends DefaultGeneralCodeGenNamesBuilder<?, ?>>
                builder() {
        return new DefaultGeneralCodeGenNamesBuilder() { };
    }

    public abstract static class DefaultGeneralCodeGenNamesBuilder
                                    <C extends DefaultGeneralCodeGenNames, B extends DefaultGeneralCodeGenNamesBuilder<C, B>> {
        private String templateName = TemplateHelper.DEFAULT_TEMPLATE_NAME;
        private String moduleName = SimpleModuleDescriptor.DEFAULT_MODULE_NAME;
        private String packageName;

        public C build() {
            return (C) new DefaultGeneralCodeGenNames(this);
        }

        public B templateName(String val) {
            this.templateName = val;
            return (B) this;
        }

        public B moduleName(String val) {
            this.moduleName = val;
            return (B) this;
        }

        public B packageName(String val) {
            this.packageName = val;
            return (B) this;
        }
    }

}
