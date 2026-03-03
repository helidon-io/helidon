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

package io.helidon.builder.tests.factory;

import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

public class D implements RuntimeType.Api<DConfig> {
    private final DConfig config;

    public D(DConfig config) {
        this.config = config;
    }

    static DConfig.Builder builder() {
        return DConfig.builder();
    }

    static D create(DConfig config) {
        return new D(config);
    }

    static D create(Consumer<DConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    @Override
    public DConfig prototype() {
        return config;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof D d)) {
            return false;
        }
        return Objects.equals(config, d.config);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(config);
    }
}
