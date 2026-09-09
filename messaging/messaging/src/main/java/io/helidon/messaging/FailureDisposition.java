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
 * Terminal disposition applied after delivery attempts are exhausted.
 */
@Api.Preview
public enum FailureDisposition {
    /**
     * Propagate the processing failure and leave the source delivery unsettled.
     */
    FAIL,

    /**
     * Log the processing failure and settle the source delivery without delivering it.
     */
    DROP,

    /**
     * Deliver the failed messages to another logical messaging channel before settling the source delivery.
     */
    DEAD_LETTER
}
