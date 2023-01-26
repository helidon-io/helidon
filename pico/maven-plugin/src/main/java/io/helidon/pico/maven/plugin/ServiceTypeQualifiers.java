/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.maven.plugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.pico.QualifierAndValue;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Used in {@link ExternalModuleCreatorMojo}.
 */
public class ServiceTypeQualifiers {
    /**
     * The service type name these qualifiers apply to.
     */
    @Parameter(name = "serviceTypeName")
    private String serviceTypeName;

    @Parameter(name = "qualifiers")
    private List<Qualifier> qualifiers;

    /**
     * Default constructor.
     */
    public ServiceTypeQualifiers() {
    }

    /**
     * @return the map representation for this instance.
     */
    Map<String, Set<QualifierAndValue>> toMap() {
        return Map.of(Objects.requireNonNull(serviceTypeName), new LinkedHashSet<>(Objects.requireNonNull(qualifiers)));
    }

}
