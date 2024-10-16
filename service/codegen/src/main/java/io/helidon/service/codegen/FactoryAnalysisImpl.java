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

package io.helidon.service.codegen;

import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

record FactoryAnalysisImpl(boolean valid,
                           TypeName factoryType,
                           TypeName providedType,
                           TypeInfo providedTypeInfo,
                           Set<ResolvedType> providedContracts)
        implements ServiceContracts.FactoryAnalysis {

    FactoryAnalysisImpl() {
        this(false, null, null, null, null);
    }

    FactoryAnalysisImpl(TypeName factoryType,
                        TypeName providedType,
                        TypeInfo providedTypeInfo,
                        Set<ResolvedType> providedContracts) {
        this(true, factoryType, providedType, providedTypeInfo, providedContracts);
    }
}
