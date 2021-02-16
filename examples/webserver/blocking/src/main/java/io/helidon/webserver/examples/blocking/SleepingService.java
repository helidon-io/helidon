/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.blocking;

import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.blocking.BlockingHandler;

/**
 * A service that sleeps for {@value #SLEEP_MILLIS} ms to emulate some blocking operation.
 */
class SleepingService implements Service {
    private static final int SLEEP_MILLIS = 10;

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/sleep", BlockingHandler.create(this::sleep));
    }

    private String sleep() {
        try {
            Thread.sleep(SLEEP_MILLIS);
            return "OK";
        } catch (InterruptedException e) {
            throw new IllegalStateException("Should have slept, but got interrupted", e);
        }
    }
}
