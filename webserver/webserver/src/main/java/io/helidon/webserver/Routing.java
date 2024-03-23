/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver;

/**
 * Routing abstraction.
 */
public interface Routing extends ServerLifecycle {

    /**
     * The class used by a {@link Router} to identify this {@link Routing} type and associate a connection with it.
     *
     * @return this routing type
     */
    default Class<? extends Routing> routingType() {
        return getClass();
    }

}
