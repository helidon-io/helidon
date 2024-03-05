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

import java.util.Optional;

interface ConfigAccessor {

    <T> Optional<T> get(String name, Class<T> type);

    default Optional<String> get(String name) {
        return this.get(name, String.class);
    }

    default ConfigAccessor thenTry(ConfigAccessor ca) {
        return new ConfigAccessor() {
            @Override
            public <T> Optional<T> get(String name, Class<T> type) {
                return ConfigAccessor.this.get(name, type).or(() -> ca.get(name, type));
            }
        };
    }

}
