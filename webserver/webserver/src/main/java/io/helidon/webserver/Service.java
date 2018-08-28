/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
 * Encapsulates a set of {@link Routing routing} rules and related logic.
 * <p>
 * Instance can be assigned to the {@link Routing routing} using
 * {@link io.helidon.webserver.Routing.Rules#register(Service...) register(...)} methods.
 */
@FunctionalInterface
public interface Service {

    /**
     * Updates {@link Routing.Rules} with {@link Handler handlers} representing this service.
     *
     * @param rules a routing rules to update
     */
    void update(Routing.Rules rules);
}
