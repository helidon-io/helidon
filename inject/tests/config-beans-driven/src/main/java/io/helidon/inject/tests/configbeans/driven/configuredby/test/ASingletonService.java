/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.configbeans.driven.configuredby.test;

import io.helidon.inject.service.Injection;

@Injection.DrivenBy(ASingletonConfigBeanBlueprint.class)
@Injection.Eager
@Injection.Named("jane")
class ASingletonService implements ASingletonServiceContract {
    private boolean running;

    // note: initially left w/o a ctor here!

    /**
     * For Testing.
     */
    @Injection.PostConstruct
    public void initialize() {
        assert (!running);
        running = true;
    }

    /**
     * For Testing.
     */
    @Injection.PreDestroy
    public void shutdown() {
        running = false;
    }

    /**
     * For Testing.
     */
    @Override
    public boolean isRunning() {
        return running;
    }

}
