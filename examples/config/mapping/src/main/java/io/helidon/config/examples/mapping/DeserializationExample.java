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

package io.helidon.config.examples.mapping;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.objectmapping.Value;

/**
 * This example shows how to automatically deserialize configuration instance into POJO beans
 * using setters.
 */
public class DeserializationExample {

    private DeserializationExample() {
    }

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        Config config = Config.create(ConfigSources.classpath("application.conf"));

        AppConfig appConfig = config
                // get "app" sub-node
                .get("app")
                // let config automatically deserialize the node to new AppConfig instance
                .as(AppConfig.class)
                .get();

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
     * During deserialization setter methods are invoked.
     */
    public static class AppConfig {
        private String greeting;
        private int pageSize;
        private List<Integer> basicRange;

        public String getGreeting() {
            return greeting;
        }

        /**
         * Set greeting property.
         * <p>
         * POJO property and config key are same, no need to customize it.
         * {@link Value} is used just to specify default value
         * in case configuration does not contain appropriate value.
         *
         * @param greeting greeting value
         */
        @Value(withDefault = "Hi")
        public void setGreeting(String greeting) {
            this.greeting = greeting;
        }

        public int getPageSize() {
            return pageSize;
        }

        /**
         * Set a page size.
         * <p>
         * {@link Value} is used to specify correct config key and default value
         * in case configuration does not contain appropriate value.
         * Original string value is mapped to target int using appropriate
         * {@link io.helidon.config.ConfigMappers ConfigMapper}.
         *
         * @param pageSize page size
         */
        @Value(key = "page-size", withDefault = "10")
        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public List<Integer> getBasicRange() {
            return basicRange;
        }

        /**
         * Set a basic range.
         * <p>
         * {@link Value} is used to specify correct config key and default value supplier
         * in case configuration does not contain appropriate value.
         * Supplier already returns default value in target type of a property.
         *
         * @param basicRange basic range
         */
        @Value(key = "basic-range", withDefaultSupplier = DefaultBasicRangeSupplier.class)
        public void setBasicRange(List<Integer> basicRange) {
            this.basicRange = basicRange;
        }

        @Override
        public String toString() {
            return "AppConfig:\n"
                    + "    greeting  = " + greeting + "\n"
                    + "    pageSize  = " + pageSize + "\n"
                    + "    basicRange= " + basicRange;
        }

        /**
         * Supplier of default value for {@link #setBasicRange(List) basic-range} property.
         */
        public static class DefaultBasicRangeSupplier implements Supplier<List<Integer>> {
            @Override
            public List<Integer> get() {
                return List.of(-10, 10);
            }
        }
    }
}
