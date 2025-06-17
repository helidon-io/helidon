/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.tests.inheritance;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

@RuntimeType.PrototypedBy(InheritanceConfig.class)
class Inheritance extends InheritanceBase<InheritanceConfig> {
    private final InheritanceConfig config;

    Inheritance(InheritanceConfig config) {
        this.config = config;
    }

    static Inheritance create(InheritanceConfig config) {
        return new Inheritance(config);
    }

    static Inheritance create(Consumer<InheritanceConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    static InheritanceConfig.Builder builder() {
        return InheritanceConfig.builder();
    }

    @Override
    public InheritanceConfig prototype() {
        return config;
    }
}
