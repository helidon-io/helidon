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

package io.helidon.service.tests.inject.configdriven;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;

@Injection.CreateFor(IConfigBlueprint.class)
@Injection.Named("jane")
class IService implements IContract {
    private boolean running;

    // note: intentionally left w/o a ctor here!

    /**
     * For Testing.
     */
    @Service.PostConstruct
    public void initialize() {
        assert (!running);
        running = true;
    }

    /**
     * For Testing.
     */
    @Service.PreDestroy
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

    @Override
    public String name() {
        return "no-name";
    }

    @Override
    public String value() {
        return "no-value";
    }
}
