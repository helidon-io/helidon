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

package io.helidon.inject.configdriven.service;

import java.io.Serializable;
import java.util.Comparator;

import io.helidon.inject.service.Injection;

/**
 * Instance, that can be (possibly) named.
 *
 * @param instance instance of config bean
 * @param name     the instance may have a name, if this is the default (not named), the name is set to
 *                 {@value io.helidon.inject.service.Injection.Named#DEFAULT_NAME}
 * @param <T>      type of the instance
 */
public record NamedInstance<T>(T instance, String name) {
    private static final NameComparator COMPARATOR_INSTANCE = new NameComparator();

    /**
     * Comparator of names, {@link io.helidon.inject.service.Injection.Named#DEFAULT_NAME} name is always first.
     *
     * @return name comparator
     */
    public static Comparator<String> nameComparator() {
        return COMPARATOR_INSTANCE;
    }

    private static class NameComparator implements Comparator<String>, Serializable {
        @Override
        public int compare(String str1, String str2) {
            int result = str1.compareTo(str2);

            if (result == 0) {
                return result;
            }
            // @default is desired to be first in the list
            if (Injection.Named.DEFAULT_NAME.equals(str1)) {
                return -1;
            } else if (Injection.Named.DEFAULT_NAME.equals(str2)) {
                return 1;
            }

            return result;
        }
    }
}
