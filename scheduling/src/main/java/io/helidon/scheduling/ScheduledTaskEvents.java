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

final class ScheduledTaskEvents {
    private static final int MAX_ID_LENGTH = 256;

    private ScheduledTaskEvents() {
    }

    static String taskId(String id) {
        return id == null || id.length() <= MAX_ID_LENGTH ? id : id.substring(0, MAX_ID_LENGTH);
    }

    static <I extends Invocation> void run(ScheduledConsumer<I> task,
                                          I invocation,
                                          String taskId,
                                          String scheduleKind) throws Throwable {
        ScheduledTaskExecutionEvent execution = ScheduledTaskExecutionEvent.create();
        String outcome = "SUCCESS";
        if (execution != null) {
            execution.begin();
        }
        try {
            task.run(invocation);
        } catch (Throwable failure) {
            outcome = "FAILURE";
            if (execution != null) {
                execution.end();
            }
            var event = ScheduledTaskFailureEvent.create();
            if (event != null) {
                event.record(taskId, scheduleKind, invocation, failure);
            }
            throw failure;
        } finally {
            if (execution != null) {
                if ("SUCCESS".equals(outcome)) {
                    execution.end();
                }
                execution.complete(taskId, scheduleKind, invocation, outcome);
            }
        }
    }
}
