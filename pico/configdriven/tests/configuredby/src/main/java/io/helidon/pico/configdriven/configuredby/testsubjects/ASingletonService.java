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

package io.helidon.pico.configdriven.configuredby.testsubjects;

import io.helidon.pico.configdriven.ConfiguredBy;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;

@ConfiguredBy(ASingletonConfigBean.class)
@Named("jane")
class ASingletonService {
    private boolean running;

    // note: initially left w/o a ctor here!

    /**
     * For Testing.
     */
    @PostConstruct
    public void initialize() {
        assert (!running);
        running = true;
    }

    /**
     * For Testing.
     */
    @PreDestroy
    public void shutdown() {
        running = false;
    }

    /**
     * For Testing.
     */
    public boolean isRunning() {
        return running;
    }

}
