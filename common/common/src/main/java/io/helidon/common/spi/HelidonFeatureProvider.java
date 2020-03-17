/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.spi;

/**
 * A provider of a feature that cannot use static initialization.
 * <p>
 * For modules that are statically initialized before server is started, you can
 * use methods on {@link io.helidon.common.HelidonFeatures} directly, to register
 * a feature. In case the feature is not related to startup or discovery (such as
 * an HTTP client), you can register a feature through this SPI.
 * <p>
 * This SPI is invoked before the features are printed, so you can safely register
 * your feature.
 */
public interface HelidonFeatureProvider {
    /**
     * You can register your feature in this method, using
     * {@link io.helidon.common.HelidonFeatures#register(io.helidon.common.HelidonFlavor, String...)}
     * or {@link io.helidon.common.HelidonFeatures#register(String...)}, or in a
     * static initializer of this implementation.
     */
    default void register() {
    }
}
