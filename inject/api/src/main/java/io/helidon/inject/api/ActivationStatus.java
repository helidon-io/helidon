/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

/**
 * The activation status. This status applies to the {@link ActivationLogEntry} record.
 *
 * @see Activator
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public enum ActivationStatus {

    /**
     * The service has been activated and is fully ready to receive requests.
     */
    SUCCESS,

    /**
     * The service has been activated but is still being started asynchronously, and is not fully ready yet to receive requests.
     * Important note: This is NOT health related - Health is orthogonal to service bindings/activation and readiness.
     */
    WARNING_SUCCESS_BUT_NOT_READY,

    /**
     * A general warning during lifecycle.
     */
    WARNING_GENERAL,

    /**
     * Failed to activate to the given phase.
     */
    FAILURE

}
