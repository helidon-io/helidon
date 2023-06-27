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

package io.helidon.nima.faulttolerance.processor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.TypeName;

abstract class FtMethodCreatorBase {
    private static final Map<CacheRecord, AtomicInteger> COUNTERS = new ConcurrentHashMap<>();

    protected String className(TypeName annotationType, TypeName enclosingType, String methodName) {
        AtomicInteger counter = COUNTERS.computeIfAbsent(new CacheRecord(annotationType, enclosingType, methodName),
                                                          it -> new AtomicInteger());

        // package.TypeName_AnnotationName_Counter
        return enclosingType.className().replace('.', '_') + "_"
                + annotationType.className().replace('.', '_') + "_"
                + methodName + "_"
                + counter.incrementAndGet();
    }

    private record CacheRecord(TypeName annotation, TypeName enclosingType, String meethodName) {
    }
}
