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

package io.helidon.tracing.providers.opentelemetry;

import io.helidon.tracing.TracerBuilder;

class OpenTelemetryTracerBuilder
        extends OpenTelemetryTracerConfig.BuilderBase<OpenTelemetryTracerBuilder, OpenTelemetryTracerConfig>
        implements TracerBuilder<OpenTelemetryTracerBuilder> {

    static OpenTelemetryTracerBuilder create() {
        return new OpenTelemetryTracerBuilder();
    }

    @Override
    public OpenTelemetryTracerConfig buildPrototype() {
        preBuildPrototype();
        validatePrototype();
        return new OpenTelemetryTracerConfigImpl(this);
    }

    @Override
    public OpenTelemetryTracer build() {
        return OpenTelemetryTracer.create(buildPrototype());
    }

    @Override
    public <B> B unwrap(Class<B> builderClass) {
        if (builderClass.isAssignableFrom(getClass())) {
            return builderClass.cast(this);
        }
        throw new IllegalArgumentException(builderClass.getName() + " is not assignable from " + getClass());
    }

    @Override
    protected void validatePrototype() {
        super.validatePrototype();
        validateOpenTelemetryAndDelegate();
    }

    private void validateOpenTelemetryAndDelegate() {
        /*
        Make sure both or neither of the OpenTelemetry object and the delegate (the OTel tracer) were set.
         */
        if (openTelemetry().isEmpty() && delegate().isEmpty()
                || openTelemetry().isPresent() && delegate().isPresent()) {
            return;
        }

        String wasSet = openTelemetry().isPresent()
                ? "openTelemetry"
                : "delegate";

        throw new IllegalArgumentException("The openTelemetry and delegate settings must either both be set or neither, but "
                                                   + "only " + wasSet + " was set");
    }
}
