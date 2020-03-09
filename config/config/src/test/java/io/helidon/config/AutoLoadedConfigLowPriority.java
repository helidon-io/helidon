/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.annotation.Priority;

/**
 * Lower-priority of two auto-loaded filters identical except for their priorities
 * and their expected filtered values.
 */
@Priority(AutoLoadedConfigPriority.LOW_PRIORITY_VALUE)
public class AutoLoadedConfigLowPriority extends AutoLoadedConfigPriority {

    private static final String EXPECTED_FILTERED_VALUE = "lowerPriorityValue";

    @Override
    String getExpectedValue() {
        return EXPECTED_FILTERED_VALUE;
    }
}
