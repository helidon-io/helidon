/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.configdriven.runtime;

import java.util.Comparator;

import io.helidon.inject.configdriven.api.NamedInstance;

/**
 * Comparator of config bean names, {@value NamedInstance#DEFAULT_NAME} is always first.
 */
class NameComparator implements Comparator<String> {
    private static final NameComparator INSTANCE = new NameComparator();

    static Comparator<String> instance() {
        return INSTANCE;
    }

    @Override
    public int compare(String str1, String str2) {
        int result = str1.compareTo(str2);

        if (result == 0) {
            return result;
        }
        // @default is desired to be first in the list
        if (NamedInstance.DEFAULT_NAME.equals(str1)) {
            return -1;
        } else if (NamedInstance.DEFAULT_NAME.equals(str2)) {
            return 1;
        }

        return result;
    }
}
