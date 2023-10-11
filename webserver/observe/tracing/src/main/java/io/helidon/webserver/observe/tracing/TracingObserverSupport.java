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

package io.helidon.webserver.observe.tracing;

import io.helidon.builder.api.Prototype;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.TracingConfig;

class TracingObserverSupport {
    private TracingObserverSupport() {
    }

    static class TracingObserverDecorator implements Prototype.BuilderDecorator<TracingObserverConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(TracingObserverConfig.BuilderBase<?, ?> target) {
            TracingConfig env = target.envConfig();
            if (target.enabled()) {
                target.enabled(env.enabled());
            }
            if (target.tracer().isEmpty()) {
                target.tracer(Tracer.global());
            }
        }
    }
}
