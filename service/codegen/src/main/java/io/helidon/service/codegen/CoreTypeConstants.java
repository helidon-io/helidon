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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

// each service descriptor has unique types generated (no duplication) for injected contracts (both
// the contract type, and the generic type)
class CoreTypeConstants {
    private final AtomicInteger genericTypeCounter = new AtomicInteger();
    private final Map<ResolvedType, String> genericConstants = new LinkedHashMap<>();
    private final AtomicInteger typeNameCounter = new AtomicInteger();
    private final Map<ResolvedType, String> typeNameConstants = new LinkedHashMap<>();

    String genericTypeConstant(ResolvedType type) {
        return genericConstants.computeIfAbsent(type, it -> "GTYPE_" + genericTypeCounter.getAndIncrement());
    }

    String typeNameConstant(ResolvedType type) {
        return typeNameConstants.computeIfAbsent(type, it -> "TYPE_" + typeNameCounter.getAndIncrement());
    }

    List<Constant> genericConstants() {
        List<Constant> result = new ArrayList<>();

        genericConstants.forEach((type, constantName) -> result.add(new Constant(type, constantName)));

        return result;
    }

    List<Constant> typeNameConstants() {
        List<Constant> result = new ArrayList<>();

        typeNameConstants.forEach((type, constantName) -> result.add(new Constant(type, constantName)));

        return result;
    }

    record Constant(TypeName type, String constantName) {
    }
}
