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

/**
 * A {@linkplain FunctionalInterface functional interface} whose implementations can {@linkplain #get(String) access
 * known configuration values by name}.
 *
 * <p><strong>Note:</strong> This fa&ccedil;ade interface does <em>not</em> describe a general-purpose configuration
 * system. It is deliberately minimal, deliberately dependency-free, deliberately <strong>suited only for the minimal
 * requirements of classes and interfaces in this package</strong>, and deliberately generic only so that it can be
 * backed for its restricted purposes by a variety of actual general-purpose configuration systems and libraries.</p>
 *
 * @see #get(String)
 */
@FunctionalInterface
interface ConfigAccessor {

    /**
     * Returns an {@link Optional} {@linkplain Optional#get() housing} a {@link String}-typed configuration value
     * suitable for the supplied configuration name, or an {@linkplain Optional#isEmpty() empty <code>Optional</code>}
     * if such a value cannot be provided.
     *
     * <p>Implementations of this method are not obligated to produce a determinate value and there are no additional
     * semantics of any kind implied by this contract.</p>
     *
     * @param name the configuration name; must not be {@code null}
     *
     * @return an {@link Optional} {@linkplain Optional#get() housing} a {@link String}-typed configuration value
     * suitable for the supplied configuration name, or an {@linkplain Optional#isEmpty() empty <code>Optional</code>}
     * if such a value cannot be provided; never {@code null}
     *
     * @exception NullPointerException if {@code name} is {@code null}
     */
    Optional<String> get(String name);

    default ConfigAccessor thenTry(ConfigAccessor ca) {
        return n -> get(n).or(() -> ca.get(n));
    }

    static ConfigAccessor empty() {
        return n -> Optional.empty();
    }

    static ConfigAccessor environmentVariables() {
        return n -> Optional.ofNullable(System.getenv(n));
    }

    static ConfigAccessor systemProperties() {
        return n -> Optional.ofNullable(System.getProperty(n));
    }

}
