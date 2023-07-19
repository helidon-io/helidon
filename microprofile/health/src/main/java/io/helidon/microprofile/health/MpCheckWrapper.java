/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.health;

import java.util.Locale;

import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

class MpCheckWrapper implements MpHealthCheck {
    private final String name;
    private final String path;
    private final HealthCheckType type;
    private final org.eclipse.microprofile.health.HealthCheck delegate;

    MpCheckWrapper(String name, String path, HealthCheckType type, org.eclipse.microprofile.health.HealthCheck delegate) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.delegate = delegate;
    }

    static MpCheckWrapper create(HealthCheckType type, org.eclipse.microprofile.health.HealthCheck delegate) {
        String name;
        try {
            name = delegate.call().getName();
        } catch (Throwable e) {
            name = delegate.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        }
        return new MpCheckWrapper(name,
                                  name,
                                  type,
                                  delegate);
    }

    @Override
    public HealthCheckType type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public HealthCheckResponse call() {
        org.eclipse.microprofile.health.HealthCheckResponse response = delegate.call();

        // map to Helidon health check response
        return HealthCheckResponse.builder()
                .status(response.getStatus() == org.eclipse.microprofile.health.HealthCheckResponse.Status.UP)
                .update(it -> response.getData().ifPresent(details -> details.forEach(it::detail)))
                .build();
    }

    public Class<?> checkClass() {
        return delegate.getClass();
    }
}
