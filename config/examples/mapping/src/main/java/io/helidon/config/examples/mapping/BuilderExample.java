/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.examples.mapping;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

/**
 * This example shows how to automatically deserialize configuration instance into POJO beans
 * using Builder pattern.
 */
public class BuilderExample {

    private BuilderExample() {
    }

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        Config config = Config.from(ConfigSources.classpath("application.conf"));

        AppConfig appConfig = config
                // get "app" sub-node
                .get("app")
                // let config automatically deserialize the node to new AppConfig instance
                .as(AppConfig.class);

        System.out.println(appConfig);

        // assert values loaded from application.conf

        assert appConfig.getGreeting().equals("Hello");

        assert appConfig.getPageSize() == 20;

        assert appConfig.getBasicRange().size() == 2;
        assert appConfig.getBasicRange().get(0) == -20;
        assert appConfig.getBasicRange().get(1) == 20;
    }

    /**
     * POJO representing an application configuration.
     * Class is initialized from {@link Config} instance.
     * During deserialization {@link #builder()} builder method} is invoked
     * and {@link Builder} is used to initialize properties from configuration.
     */
public static class AppConfig {
    private final String greeting;
    private final int pageSize;
    private final List<Integer> basicRange;

    private AppConfig(String greeting, int pageSize, List<Integer> basicRange) {
        this.greeting = greeting;
        this.pageSize = pageSize;
        this.basicRange = basicRange;
    }

        public String getGreeting() {
            return greeting;
        }

        public int getPageSize() {
            return pageSize;
        }

        public List<Integer> getBasicRange() {
            return basicRange;
        }

        @Override
        public String toString() {
            return "AppConfig:\n"
                    + "    greeting  = " + greeting + "\n"
                    + "    pageSize  = " + pageSize + "\n"
                    + "    basicRange= " + basicRange;
        }

        /**
         * Creates new Builder instance used to be initialized from configuration.
         *
         * @return new Builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * {@link AppConfig} Builder used to be initialized from configuration.
         */
        public static class Builder {
            private String greeting;
            private int pageSize;
            private List<Integer> basicRange;

            private Builder() {
            }

            /**
             * Set greeting property.
             * <p>
             * POJO property and config key are same, no need to customize it.
             * {@link Config.Value} is used just to specify default value
             * in case configuration does not contain appropriate value.
             *
             * @param greeting greeting value
             */
            @Config.Value(withDefault = "Hi")
            public void setGreeting(String greeting) {
                this.greeting = greeting;
            }

            /**
             * Set a page size.
             * <p>
             * {@link Config.Value} is used to specify correct config key and default value
             * in case configuration does not contain appropriate value.
             * Original string value is mapped to target int using appropriate
             * {@link io.helidon.config.ConfigMappers ConfigMapper}.
             *
             * @param pageSize page size
             */
            @Config.Value(key = "page-size", withDefault = "10")
            public void setPageSize(int pageSize) {
                this.pageSize = pageSize;
            }

            /**
             * Set a basic range.
             * <p>
             * {@link Config.Value} is used to specify correct config key and default value supplier
             * in case configuration does not contain appropriate value.
             * Supplier already returns default value in target type of a property.
             *
             * @param basicRange basic range
             */
            @Config.Value(key = "basic-range", withDefaultSupplier = DefaultBasicRangeSupplier.class)
            public void setBasicRange(List<Integer> basicRange) {
                this.basicRange = basicRange;
            }

            /**
             * Creates new instance of {@link AppConfig} using values provided by configuration.
             *
             * @return new instance of {@link AppConfig}.
             */
            public AppConfig build() {
                return new AppConfig(greeting, pageSize, basicRange);
            }
        }

        /**
         * Supplier of default value for {@link Builder#setBasicRange(List) basic-range} property.
         */
        public static class DefaultBasicRangeSupplier implements Supplier<List<Integer>> {
            @Override
            public List<Integer> get() {
                return CollectionsHelper.listOf(-10, 10);
            }
        }
    }
}
