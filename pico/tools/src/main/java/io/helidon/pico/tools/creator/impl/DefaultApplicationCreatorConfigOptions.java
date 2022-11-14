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

import io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions;
import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions}.
 */
//@AllArgsConstructor
//@Builder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultApplicationCreatorConfigOptions implements ApplicationCreatorConfigOptions {
    /*@Builder.Default*/ private final PermittedProviderType permittedProviderTypes/* = PermittedProviderType.NONE*/;
    /*@Singular("permittedProviderName")*/ private final Set<String> permittedProviderNames;
    /*@Singular("permittedProviderName")*/ private final Set<TypeName> permittedProviderQualifierTypeNames;

    protected DefaultApplicationCreatorConfigOptions(DefaultApplicationCreatorConfigOptionsBuilder builder) {
        this.permittedProviderTypes = builder.permittedProviderTypes;
        this.permittedProviderNames = Objects.isNull(builder.permittedProviderNames)
                ? Collections.emptySet() : Collections.unmodifiableSet(builder.permittedProviderNames);
        this.permittedProviderQualifierTypeNames = Objects.isNull(builder.permittedProviderQualifierTypeNames)
                ? Collections.emptySet() : Collections.unmodifiableSet(builder.permittedProviderQualifierTypeNames);
    }

    public static DefaultApplicationCreatorConfigOptionsBuilder builder() {
        return new DefaultApplicationCreatorConfigOptionsBuilder() {};
    }


    public abstract static class DefaultApplicationCreatorConfigOptionsBuilder {
        private PermittedProviderType permittedProviderTypes = PermittedProviderType.NONE;
        private Set<String> permittedProviderNames;
        private Set<TypeName> permittedProviderQualifierTypeNames;

        public DefaultApplicationCreatorConfigOptions build() {
            return new DefaultApplicationCreatorConfigOptions(this);
        }

        public DefaultApplicationCreatorConfigOptionsBuilder permittedProviderTypes(PermittedProviderType val) {
            this.permittedProviderTypes = val;
            return this;
        }

        public DefaultApplicationCreatorConfigOptionsBuilder permittedProviderNames(Collection<String> val) {
            this.permittedProviderNames = Objects.isNull(val) ? null : new LinkedHashSet<>(val);
            return this;
        }

        public DefaultApplicationCreatorConfigOptionsBuilder permittedProviderQualifierTypeNames(Collection<TypeName> val) {
            this.permittedProviderQualifierTypeNames = Objects.isNull(val) ? null : new LinkedHashSet<>(val);
            return this;
        }
    }

}
