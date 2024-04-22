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

import java.util.List;

/**
 * Provide a means to query the activation log.
 *
 * @see ActivationLog
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface ActivationLogQuery extends Resettable {

    /**
     * Clears the activation log.
     *
     * @param deep ignored
     * @return true if the log was cleared, false if the log was previously empty
     */
    @Override
    boolean reset(boolean deep);

    /**
     * The full transcript of all services phase transitions being managed.
     *
     * @return the activation log if log capture is enabled
     */
    List<ActivationLogEntry> fullActivationLog();

}
