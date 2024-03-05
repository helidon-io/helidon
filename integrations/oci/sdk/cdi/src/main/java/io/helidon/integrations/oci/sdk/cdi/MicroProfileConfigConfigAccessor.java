/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.sdk.cdi;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

class MicroProfileConfigConfigAccessor implements ConfigAccessor {

    private final Supplier<? extends Config> cs;

    MicroProfileConfigConfigAccessor() {
        this(ConfigProvider::getConfig);
    }

    MicroProfileConfigConfigAccessor(Config c) {
        this(supplier(c));
    }

    MicroProfileConfigConfigAccessor(Supplier<? extends Config> cs) {
        super();
        this.cs = Objects.requireNonNull(cs, "cs");
    }

    @Override
    public <T> Optional<T> get(String name, Class<T> type) {
        return this.cs.get().getOptionalValue(name, type);
    }

    private static Supplier<? extends Config> supplier(Config c) {
        Objects.requireNonNull(c, "c");
        return () -> c;
    }

}
