/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import io.helidon.common.Weight;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

@Service.NamedByType(FaultTolerance.Bulkhead.class)
@Weight(FaultTolerance.WEIGHT_BULKHEAD)
@Service.Singleton
class BulkheadInterceptor extends InterceptorBase<Bulkhead> {
    BulkheadInterceptor(ServiceRegistry registry) {
        super(registry, Bulkhead.class, FaultTolerance.Bulkhead.class);
    }

    @Override
    Bulkhead obtainHandler(TypedElementInfo elementInfo, InterceptorBase.CacheRecord cacheRecord) {
        return namedHandler(elementInfo, this::fromAnnotation);
    }

    private Bulkhead fromAnnotation(Annotation annotation) {
        int limit = annotation.getValue("limit").map(Integer::parseInt).orElse(BulkheadConfigBlueprint.DEFAULT_LIMIT);
        int queueLength = annotation.getValue("queueLength").map(Integer::parseInt)
                .orElse(BulkheadConfigBlueprint.DEFAULT_QUEUE_LENGTH);
        String name = annotation.getValue("name").orElse("bulkhead-") + System.identityHashCode(annotation);

        return BulkheadConfig.builder()
                .name(name)
                .queueLength(queueLength)
                .limit(limit)
                .build();
    }
}
