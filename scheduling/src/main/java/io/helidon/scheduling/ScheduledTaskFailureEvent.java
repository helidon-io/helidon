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

package io.helidon.scheduling;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("io.helidon.ScheduledTaskFailure")
@Label("Scheduled Task Failure")
@Category({"Helidon", "Scheduling"})
@Enabled(true)
@StackTrace(true)
final class ScheduledTaskFailureEvent extends Event {
    private static final EventType TYPE = EventType.getEventType(ScheduledTaskFailureEvent.class);

    @Label("Task ID")
    private String taskId;
    @Label("Schedule Kind")
    private String scheduleKind;
    @Label("Iteration")
    private long iteration;
    @Label("Exception Class")
    private String exceptionClass;

    static ScheduledTaskFailureEvent create() {
        if (TYPE.isEnabled()) {
            return new ScheduledTaskFailureEvent();
        }
        return null;
    }

    void record(String taskId, String scheduleKind, Invocation invocation, Throwable failure) {
        if (shouldCommit()) {
            this.taskId = taskId;
            this.scheduleKind = scheduleKind;
            this.iteration = invocation.iteration();
            this.exceptionClass = failure.getClass().getName();
            commit();
        }
    }
}
