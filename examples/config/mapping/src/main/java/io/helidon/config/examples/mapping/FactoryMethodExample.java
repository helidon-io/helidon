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
 * using factory method.
 */
public class FactoryMethodExample {

    private FactoryMethodExample() {
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
     * During deserialization {@link #create(String, int, List) factory method} is invoked.
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
         * Creates new {@link AppConfig} instances.
         * <p>
         * {@link Value} is used to specify config keys
         * and default values in case configuration does not contain appropriate value.
         *
         * @param greeting   greeting
         * @param pageSize   page size
         * @param basicRange basic range
         * @return new instance of {@link AppConfig}.
         */
        public static AppConfig create(@Value(key = "greeting", withDefault = "Hi")
                                             String greeting,
                                       @Value(key = "page-size", withDefault = "10")
                                             int pageSize,
                                       @Value(key = "basic-range", withDefaultSupplier = DefaultBasicRangeSupplier.class)
                                             List<Integer> basicRange) {
            return new AppConfig(greeting, pageSize, basicRange);
        }

        /**
         * Supplier of default value for {@code basic-range} property, see {@link #create(String, int, List)}.
         */
        public static class DefaultBasicRangeSupplier implements Supplier<List<Integer>> {
            @Override
            public List<Integer> get() {
                return List.of(-10, 10);
            }
        }
    }
}
