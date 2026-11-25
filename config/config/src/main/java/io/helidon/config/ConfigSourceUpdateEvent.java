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
package io.helidon.config;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

/**
 * Stable diagnostic event and fields for source loading and update observations.
 * Outcomes are INITIAL, CHANGED, DELETED, UNAVAILABLE, FAILED, and RECOVERED.
 * A recovery reports the first successful observation following failure or mandatory-source absence,
 * even when the content has not changed. Repeated failures, absences, and equal content are omitted.
 * Duration covers source initialization and retry policy on initial load, and source loading/parsing on reload.
 * Poll checks that fail or establish recovery without loading have their own observation interval.
 */
@Name("io.helidon.ConfigSourceUpdate")
@Label("Configuration Source Update")
@Category({"Helidon", "Configuration"})
@Description("Initial source load, meaningful change, unavailability, failure, or recovery")
@Enabled(true)
@Threshold("0 ms")
@StackTrace(false)
final class ConfigSourceUpdateEvent extends Event {
    private static final EventType TYPE = EventType.getEventType(ConfigSourceUpdateEvent.class);

    @Label("Component Name")
    @Description("Explicit configured name; null because the current source SPI has no configured name")
    private String componentName;

    @Label("Source Class")
    @Description("Source implementation class, without its description or location")
    private String sourceClass;

    @Label("Outcome")
    @Description("INITIAL, CHANGED, DELETED, UNAVAILABLE, FAILED, or RECOVERED")
    private String outcome;

    @Label("Exception Class")
    @Description("Failure class, without an exception message; null for other outcomes")
    private String exceptionClass;

    static ConfigSourceUpdateEvent beginObservation() {
        if (!TYPE.isEnabled()) {
            return null;
        }
        var event = new ConfigSourceUpdateEvent();
        event.begin();
        return event;
    }

    void record(String sourceClass, String outcome, Throwable failure) {
        if (shouldCommit()) {
            this.sourceClass = sourceClass;
            this.outcome = outcome;
            this.exceptionClass = failure == null ? null : failure.getClass().getName();
            commit();
        }
    }
}
