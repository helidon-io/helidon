/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilter;

/**
 * Abstract superclass for making sure simple priority works.
 * <p>
 * Concrete subclasses implement {@link #getExpectedValue()} to return that
 * filter's expected value so the test can make sure the expected value from the
 * higher-priority filter is the value returned.
 */
public abstract class AutoLoadedConfigPriority implements ConfigFilter {

    public static final String KEY_SUBJECT_TO_AUTO_FILTERING = "key1.subjectToPrioritizedAutoloadedFilter";

    public static final int HIGH_PRIORITY_VALUE = 10;
    public static final int LOW_PRIORITY_VALUE = HIGH_PRIORITY_VALUE + 1;

    @Override
    public String apply(Config.Key key, String stringValue) {
        if (key.toString().equals(KEY_SUBJECT_TO_AUTO_FILTERING)) {
            return getExpectedValue();
        }
        return stringValue;
    }

    /**
     * Provides the filter-dependent expected value.
     *
     * @return the expected value for the
     */
    abstract String getExpectedValue();

}
