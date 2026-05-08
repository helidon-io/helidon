/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.codegen.apt;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.codegen.spi.TypeMapperProvider;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

public class TestRecordTypeMapperProvider implements TypeMapperProvider {
    private static final TypeName RECORD_MAPPED = TypeName.create("com.example.RecordMapped");
    private static final TypeName TARGET_TYPE = TypeName.create("com.example.RecordMappedExample");
    private static final Set<TypeName> SEEN_TYPES = ConcurrentHashMap.newKeySet();
    private static final Set<TypeName> MAPPED_TYPES = ConcurrentHashMap.newKeySet();

    static void reset() {
        SEEN_TYPES.clear();
        MAPPED_TYPES.clear();
    }

    static boolean sawType(TypeName typeName) {
        return SEEN_TYPES.contains(typeName);
    }

    static boolean mappedType(TypeName typeName) {
        return MAPPED_TYPES.contains(typeName);
    }

    @Override
    public TypeMapper create(CodegenOptions options) {
        return new TypeMapper() {
            @Override
            public boolean supportsType(TypeInfo type) {
                SEEN_TYPES.add(type.typeName());
                return TARGET_TYPE.equals(type.typeName());
            }

            @Override
            public Optional<TypeInfo> map(CodegenContext ctx, TypeInfo type) {
                MAPPED_TYPES.add(type.typeName());
                return Optional.of(type);
            }
        };
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(RECORD_MAPPED);
    }
}
