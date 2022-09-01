/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.model.naming;

//import io.micronaut.core.naming.NameUtils;

import java.util.Locale;

import io.helidon.core.utils.NameUtils;

/**
 * Naming strategy enum for when a class or property name has no explicit mapping.
 *
 * @author graemerocher
 * @since 1.0
 */
public class NamingStrategies {
    /**
     * Example: FOO_BAR.
     */
    public static class UnderScoreSeparatedUpperCase implements NamingStrategy {
        @Override
        public String mappedName(String name) {
            return NameUtils.environmentName(name);
        }
    }
    /**
     * Example: foo_bar.
     */
    public static class UnderScoreSeparatedLowerCase implements NamingStrategy {
        @Override
        public String mappedName(String name) {
            return NameUtils.underscoreSeparate(name).toLowerCase(Locale.ENGLISH);
        }
    }
    /**
     * Example: foo-bar.
     */
    public static class KebabCase implements NamingStrategy {
        @Override
        public String mappedName(String name) {
            return NameUtils.hyphenate(name);
        }
    }
    /**
     * Example: foobar.
     */
    public static class LowerCase implements NamingStrategy {
        @Override
        public String mappedName(String name) {
            return name.toLowerCase(Locale.ENGLISH);
        }
    }
    /**
     * Example: foobar.
     */
    public static class UpperCase implements NamingStrategy {
        @Override
        public String mappedName(String name) {
            return name.toUpperCase(Locale.ENGLISH);
        }
    }
    /**
     * No naming conversion.
     */
    public static class Raw implements NamingStrategy {
        @Override
        public String mappedName(String name) {
            return name;
        }
    }
}
