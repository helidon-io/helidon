/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigFilter;

import org.hamcrest.CoreMatchers;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Asserts input values.
 */
class AssertingFilter implements ConfigFilter {
    private Config rootConfig;
    private String key;
    private String oldValue;
    private Supplier<String> newValue;
    private String expectedValueDuringInit;

    public AssertingFilter(Config rootConfig) {
        this.rootConfig = rootConfig;
    }

    public void set(String key, String oldValue, Supplier<String> newValue,
            String expectedValueDuringInit) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.expectedValueDuringInit = expectedValueDuringInit;
    }

    private void test() {
        if (expectedValueDuringInit != null) {
            assertThat(String.format("AssertingFilter.test failed for key %s, old value %s, and new value %s",
                    key, oldValue, newValue.get()),
                       rootConfig.get(key).asString().get(), CoreMatchers.is(expectedValueDuringInit));
        }
    }

    @Override
    public String apply(Config.Key key1, String stringValue) {
        if (key1.toString().equals(key)) {
            assertThat(stringValue, CoreMatchers.is(oldValue));
            return newValue.get();
        }
        return stringValue;
    }

    @Override
    public void init(Config config) {
        test();
    }

    static class Provider implements Function<Config, ConfigFilter> {
        private String key;
        private String oldValue;
        private String expectedValueDuringInit = null;

        private Supplier<String> newValue;

        Provider(String key, String oldValue, Supplier<String> newValue) {
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        void setExpectedValueDuringInit(String expectedValueDuringInit) {
            this.expectedValueDuringInit = expectedValueDuringInit;
        }

        String newValue() {
            return newValue.get();
        }

        @Override
        public ConfigFilter apply(Config config) {
            AssertingFilter assertingFilter = new AssertingFilter(config);
            assertingFilter.set(key, oldValue, newValue, expectedValueDuringInit);
            return assertingFilter;
        }

    }

}
