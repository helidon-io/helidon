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

import io.helidon.config.spi.ConfigFilter;

/**
 * Simple auto-loaded filter to change the value for one specific key.
 */
public class AutoLoadedConfigFilter implements ConfigFilter {

    public static final String KEY_SUBJECT_TO_AUTO_FILTERING = "key1.subjectToAutoloadedFilter";
    public static final String EXPECTED_FILTERED_VALUE = "filteredValue";
    
    
    @Override
    public String apply(Config.Key key, String stringValue) {
        if (key.toString().equals(KEY_SUBJECT_TO_AUTO_FILTERING)) {
            return EXPECTED_FILTERED_VALUE;
        }
        return stringValue;
    }
    
}
