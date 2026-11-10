/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("io.helidon.CircuitBreakerStateChange")
@Label("Circuit Breaker State Change")
@Category({"Helidon", "Fault Tolerance"})
@Enabled(true)
@StackTrace(false)
final class CircuitBreakerStateChangeEvent extends Event {
    private static final EventType TYPE = EventType.getEventType(CircuitBreakerStateChangeEvent.class);

    @Label("Breaker Name")
    private String breakerName;
    @Label("Previous State")
    private String previousState;
    @Label("State")
    private String state;
    @Label("Reason")
    private String reason;

    static CircuitBreakerStateChangeEvent create() {
        if (TYPE.isEnabled()) {
            return new CircuitBreakerStateChangeEvent();
        }
        return null;
    }

    void record(String breakerName, CircuitBreaker.State previous, CircuitBreaker.State next, String reason) {
        if (shouldCommit()) {
            this.breakerName = breakerName;
            this.previousState = previous.name();
            this.state = next.name();
            this.reason = reason;
            commit();
        }
    }
}
