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

package io.helidon.pico.tools;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.types.TypeName;

/**
 * See {@link DefaultActivatorCreator}.
 */
@Builder
abstract class ActivatorCreatorArgs {
    abstract String template();
    abstract TypeName serviceTypeName();
    abstract TypeName activatorTypeName();
    abstract String activatorGenericDecl();
    abstract TypeName parentTypeName();
    abstract Set<String> scopeTypeNames();
    abstract List<String> description();
    abstract ServiceInfoBasics serviceInfo();
    abstract DependenciesInfo dependencies();
    abstract DependenciesInfo parentDependencies();
    abstract Collection<Object> injectionPointsSkippedInParent();
    abstract List<?> serviceTypeInjectionOrder();
    abstract String generatedSticker();
    abstract Double weightedPriority();
    abstract Integer runLevel();
    abstract String postConstructMethodName();
    abstract String preDestroyMethodName();
    abstract List<String> extraCodeGen();
    abstract boolean isConcrete();
    abstract boolean isProvider();
    abstract boolean isSupportsJsr330InStrictMode();
}
