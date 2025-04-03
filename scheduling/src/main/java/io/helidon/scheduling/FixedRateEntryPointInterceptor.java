/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.scheduling;

import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;

/**
 * A contract used from generated code to invoke HTTP entry point interceptors.
 */
@Service.NamedByType(Schedule.FixedRate.class)
@Service.Singleton
class FixedRateEntryPointInterceptor implements Interception.Interceptor {
    private final SchedulingEntryPoints entryPoints;

    @Service.Inject
    FixedRateEntryPointInterceptor(SchedulingEntryPoints entryPoints) {
        this.entryPoints = entryPoints;
    }

    @Override
    public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
        return entryPoints.proceed(ctx, chain, args);
    }
}
