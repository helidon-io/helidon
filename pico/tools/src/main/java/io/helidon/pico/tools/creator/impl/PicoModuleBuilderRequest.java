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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Provides a builder for {@link AbstractCreator#createModuleInfo(PicoModuleBuilderRequest)}.
 */
@SuperBuilder
@Getter
public class PicoModuleBuilderRequest {
    private final String moduleName;
    private final TypeName moduleTypeName;
    private final TypeName applicationTypeName;
    /*@Singular("moduleRequired")*/ private final Collection<String> modulesRequired;
    /*@Singular("contract")*/ private final Map<TypeName, Set<TypeName>> serviceTypeContracts;
    /*@Singular("externalContract")*/ private final Map<TypeName, Set<TypeName>> externalContracts;
    private final String generator;
    private final String moduleInfoPath;
    private final String classPrefixName;
    private final boolean isApplicationCreated;
    private final boolean isModuleCreated;

    /**
     * Returns the generator from the builder, defaulting to the default class provided if generator was not passed.
     *
     * @param defaultGeneratorClassType the default class generator name to use
     * @return the generator name
     */
    public String getGenerator(Class<?> defaultGeneratorClassType) {
        String generator = getGenerator();
        return Objects.isNull(generator) ? defaultGeneratorClassType.getName() : generator;
    }

}
