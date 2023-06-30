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

package io.helidon.spi;

/**
 * {@link java.util.ServiceLoader} provider interface to discover the correct startup type.
 * Only the first provider (with the highest {@link io.helidon.common.Weight}) will be used.
 */
public interface HelidonStartupProvider {
    /**
     * Start the runtime.
     *
     * @param arguments command line arguments
     */
    void start(String[] arguments);
}
