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

package io.helidon.nima.faulttolerance;

import io.helidon.common.Weight;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.api.ClassNamed;
import io.helidon.inject.api.InjectionServices;

import jakarta.inject.Singleton;

@ClassNamed(FaultTolerance.Retry.class)
@Weight(FaultTolerance.WEIGHT_RETRY)
@Singleton
class RetryInterceptor extends InterceptorBase<Retry> {
    RetryInterceptor() {
        super(InjectionServices.realizedServices(), Retry.class, FaultTolerance.Retry.class);
    }

    @Override
    Retry obtainHandler(TypedElementInfo elementInfo, InterceptorBase.CacheRecord cacheRecord) {
        return super.generatedMethod(RetryMethod.class, cacheRecord)
                .map(RetryMethod::retry)
                .orElseGet(() -> services().lookup(Retry.class).get());
    }
}
