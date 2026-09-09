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

package io.helidon.messaging;

import io.helidon.common.Api;

/**
 * Outcome of one message in a failed batch delivery.
 */
@Api.Preview
public enum BatchItemStatus {
    /**
     * The message reached the operation's success point.
     */
    SUCCEEDED,

    /**
     * The message is known not to have reached the operation's success point.
     */
    FAILED,

    /**
     * The message was not attempted.
     */
    NOT_ATTEMPTED,

    /**
     * Completion of the message's full operation cannot be proven. This also covers a message that completed only
     * some required outputs. Retrying can duplicate delivery to an output that already succeeded.
     */
    INDETERMINATE
}
