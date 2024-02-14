/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;
import io.helidon.config.objectmapping.Transient;
import io.helidon.config.objectmapping.Value;

import static io.helidon.config.ConfigSources.classpath;

@SuppressWarnings("ALL")
class PropertyMappingSnippets {

    // stub
    static class AppConfig {

        AppConfig() {
        }

        AppConfig(String greeting, int pageSize, List<Integer> basicRange) {
        }

        String getGreeting() {
            return "";
        }

        int getPageSize() {
            return 0;
        }

        List<Integer> getBasicRange() {
            return List.of();
        }

        Instant getTimestamp() {
            return null;
        }
    }

    // stub
    class BasicRangeSupplier implements Supplier<List<Integer>> {
        @Override
        public List<Integer> get() {
            return null;
        }
    }

    // stub
    static class WebConfig {

        WebConfig(boolean debug, int pageSize, double ratio) {
        }

        public boolean isDebug() {
            return false;
        }

        public int getPageSize() {
            return 0;
        }

        public double getRatio() {
            return 0;
        }
    }

    // stub
    static class WebConfigMapper implements Function<Config, WebConfig> {
        static WebConfig map(Config config) {
            return null;
        }

        @Override
        public WebConfig apply(Config config) {
            return map(config);
        }
    }

    void snippet_1(Config config) {
        // tag::snippet_1[]
        Config configNode = config.get("someKey");
        ConfigValue<Boolean> value = configNode.asBoolean(); // <1>
        ConfigValue<Boolean> value2 = configNode.as(Boolean.class); // <2>
        // end::snippet_1[]
    }

    interface Snippet2<T> {

        // tag::snippet_2[]
        T as(Class<? extends T> type);

        T as(Function<Config, T> mapper);

        T as(GenericType<T> genericType);
        // end::snippet_2[]
    }

    // tag::snippet_3[]
    enum Color {RED, YELLOW, BLUE_GREEN}
    // end::snippet_3[]

    void snippet_4() {
        // tag::snippet_4[]
        Config config = Config.just(ConfigSources.create(Map.of(
                "house.tint", "blue-green",
                "car.color", "Red",
                "warning", "YELLOW"
        )));

        Color house = config.get("house.tint") // <1>
                .as(Color.class) // <2>
                .get(); // <3>
        Color car = config.get("car.color")
                .as(Color.class)
                .get(); // <4>
        Color warning = config.get("warning")
                .as(Color.class)
                .get(); // <5>
        // end::snippet_4[]
    }

    void snippet_5(Config config) {
        // tag::snippet_5[]
        Config configNode = config.get("web");
        ConfigValue<WebConfig> web = configNode.as(WebConfigMapper::map);
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        public class WebConfig {
            private boolean debug;
            private int pageSize;
            private double ratio;

            public WebConfig(boolean debug, int pageSize, double ratio) {
                this.debug = debug;
                this.pageSize = pageSize;
                this.ratio = ratio;
            }

            public boolean isDebug() {
                return debug;
            }

            public int getPageSize() {
                return pageSize;
            }

            public double getRatio() {
                return ratio;
            }
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        public class WebConfigMapper implements Function<Config, WebConfig> {

            @Override
            public WebConfig apply(Config config) {
                return new WebConfig(
                        config.get("debug").asBoolean().orElse(false),
                        config.get("page-size").asInt().orElse(10),
                        config.get("ratio").asDouble().orElse(1.0)
                );
            }
        }
        // end::snippet_7[]

        void snippet_8() {
            // tag::snippet_8[]
            Config config = Config.create(classpath("application.properties"));

            WebConfig web = config.get("web")
                    .as(new WebConfigMapper())
                    .get();
            // end::snippet_8[]
        }
    }

    void snippet_9() {
        // tag::snippet_9[]
        Config config = Config.builder(classpath("application.properties"))
                .addMapper(WebConfig.class, new WebConfigMapper())
                .build();

        WebConfig web = config.get("web")
                .as(WebConfig.class)
                .get();
        // end::snippet_9[]
    }

    class Snippet10 {

        static class WebConfigBuilder {
        }

        // tag::snippet_10[]
        public class WebConfig {
            static WebConfigBuilder builder() {
                return new WebConfigBuilder();
            }
        }
        // end::snippet_10[]
    }

    class Snippet1 {

        // tag::snippet_11[]
        public class AppConfig {
            private Instant timestamp;
            private String greeting;
            private int pageSize;
            private List<Integer> basicRange;

            public AppConfig() { // <1>
            }

            public void setGreeting(String greeting) { // <2>
                this.greeting = greeting;
            }

            public String getGreeting() {
                return greeting;
            }

            @Value(key = "page-size", // <3>
                   withDefault = "10") // <4>
            public void setPageSize(int pageSize) {
                this.pageSize = pageSize;
            }

            public int getPageSize() {
                return pageSize;
            }

            @Value(key = "basic-range", // <5>
                   withDefaultSupplier = BasicRangeSupplier.class) // <6>
            public void setBasicRange(List<Integer> basicRange) {
                this.basicRange = basicRange;
            }

            public List<Integer> getBasicRange() {
                return basicRange;
            }

            @Transient // <7>
            public void setTimestamp(Instant timestamp) {
                this.timestamp = timestamp;
            }

            public Instant getTimestamp() {
                return timestamp;
            }

            public static class BasicRangeSupplier
                    implements Supplier<List<Integer>> { // <8>
                @Override
                public List<Integer> get() {
                    return List.of(-10, 10);
                }
            }
        }
        // end::snippet_11[]
    }

    void snippet_12() {
        // tag::snippet_12[]
        Config config = Config.create(classpath("application.conf"));

        AppConfig app = config.get("app")
                .as(AppConfig.class)
                .get(); // <1>

        //assert that all values are loaded from file
        assert app.getGreeting().equals("Hello");
        assert app.getPageSize() == 20;
        assert app.getBasicRange().size() == 2
               && app.getBasicRange().get(0) == -20
               && app.getBasicRange().get(1) == 20;

        //assert that Transient property is not set
        assert app.getTimestamp() == null; // <2>
        // end::snippet_12[]
    }

    class Snippet13 {

        // stub
        static class AppConfig {
            AppConfig(Builder builder) {
            }
        }

        // tag::snippet_13[]
        public static class Builder { // <1>

            private String greeting;
            private int pageSize;
            private List<Integer> basicRange;

            private Builder() {
            }

            public Builder setGreeting(String greeting) { // <2>
                this.greeting = greeting;
                return this;
            }

            @Value(key = "page-size", withDefault = "10")
            public Builder setPageSize(int pageSize) { // <3>
                this.pageSize = pageSize;
                return this;
            }

            @Value(key = "basic-range", withDefaultSupplier = BasicRangeSupplier.class)
            public Builder setBasicRange(List<Integer> basicRange) { // <4>
                this.basicRange = basicRange;
                return this;
            }

            public AppConfig build() { // <7>
                return new AppConfig(this);
            }
        }
        // end::snippet_13[]
    }

    class Snippet14 {

        // tag::snippet_14[]
        public static AppConfig from(
                @Value(key = "greeting") String greeting, // <1>
                @Value(key = "page-size", withDefault = "10") int pageSize, // <2>
                @Value(key = "basic-range", withDefaultSupplier = BasicRangeSupplier.class) List<Integer> basicRange) {
            return new AppConfig(greeting, pageSize, basicRange);
        }
        // end::snippet_14[]
    }

    class Snippet15 {

        class AppConfig {

            String greeting;
            int pageSize;
            List<Integer> basicRange;

            // tag::snippet_15[]
            public AppConfig( // <1>
                              @Value(key = "greeting") // <2>
                              String greeting,
                              @Value(key = "page-size",
                                     withDefault = "10")
                              int pageSize,
                              @Value(key = "basic-range",
                                     withDefaultSupplier = BasicRangeSupplier.class)
                              List<Integer> basicRange) {
                this.greeting = greeting;
                this.pageSize = pageSize;
                this.basicRange = basicRange;
            }
            // end::snippet_15[]
        }
    }
}
