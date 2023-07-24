/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tracing;

import io.helidon.common.context.Contexts;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

/**
 * A producer of {@link io.opentracing.Tracer} needed for injection into {@code CDI} beans.
 */
@ApplicationScoped
public class TracerProducer {

    /**
     * Provides an instance of tracer currently configured.
     * @return a {@link Tracer} from current {@link io.helidon.common.context.Context},
     *  or {@link io.opentracing.util.GlobalTracer#get()} in case we are not within a context.
     */
    @Produces
    @Dependent // we want to inject a new instance each time a bean is created, to make sure we use the current value from context
    public Tracer tracer() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(Tracer.class))
                .orElseGet(GlobalTracer::get);
    }
}
