/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.tests;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.hocon.HoconConfigParser;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigValues.simpleValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

/**
 * This test shows Client API use-cases.
 */
public class DemoTest {

    @Test
    public void testCreateConfig() {
        // looks for: application .yaml | .conf | .json | .properties on classpath
        Config config = Config.create();

        assertThat( // STRING
                    config.get("app.greeting").asString().get(),
                    is("Hello"));
    }

    @Test
    public void testTypedAccessors() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParser.create())
                .build();

        // ACCESSORS:
        // String, int, boolean, double, long,
        // OptionalInt, OptionalDouble, OptionalLong, Optional<T>,
        // List<String>, List<T>, Optional<List<String>>

        assertThat( // INT
                    config.get("app.page-size").asInt().get(),
                    is(20));

        assertThat( // boolean + DEFAULT
                    config.get("app.storageEnabled").asBoolean().orElse(false),
                    is(false));

        assertThat( // LIST <Integer>
                    config.get("app.basic-range").asList(Integer.class).get(),
                    contains(-20, 20));

        assertThat( // BUILT-IN mapper for PATH
                    config.get("logging.outputs.file.name").as(Path.class).get(),
                    is(Paths.get("target/root.log")));

        // BUILT-IN MAPPERS:
        // Class, BigDecimal, BigInteger, Duration, LocalDate, LocalDateTime,
        // LocalTime, ZonedDateTime, ZoneId, Instant, OffsetTime, OffsetDateTime, File,
        // Path, Charset, URI, URL, Pattern, UUID
    }

    @Test
    public void testListOfConfigs() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParser.create())
                .build();

        // list of objects
        List<Config> securityProviders = config.get("security.providers")
                .asList(Config.class).get();

        assertThat(securityProviders.size(),
                   is(2)); // with 2 items

        assertThat(securityProviders.get(0).get("name").asString(),
                   is(simpleValue("BMCS"))); // name of 1st provider

        assertThat(securityProviders.get(1).get("name").asString(),
                   is(simpleValue("ForEndUsers"))); // name of 2nd provider
    }

    @Test
    public void testNodeChildrenAndTraverse() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParser.create())
                .build();

        System.out.println("Handlers:");

        // find out all configured logging outputs
        config.get("logging.outputs")
                .asNodeList() // DIRECT CHILDREN NODES
                .get()
                .forEach(node -> System.out.println("\t\t" + node.key()));
        //i.e. setup Logging Handler ...

        System.out.println("Levels:");

        // find out all logging level configurations:
        config.get("logging")
                .traverse() // DEPTH-FIRST SEARCH
                .filter(Config::isLeaf)
                .filter(node -> node.key().name().equals("level"))
                .forEach(node -> System.out.println("\t\t" + node.key() + " = " + node.asString()));
        //i.e. setup Logger ...
    }

    @Test
    public void testFallbackConfigSource() {
        Config config = Config.builder()
                .sources(// PROPERTIES first
                        ConfigSources.classpath("application.properties"),
                        // with fallback to HOCON
                        ConfigSources.classpath("application.conf"))
                .addParser(ConfigParsers.properties())
                .addParser(HoconConfigParser.create())
                .build();

        assertThat( // value from HOCON
                    config.get("app.greeting").asString(),
                    is(simpleValue("Hello")));

        assertThat( // value from PROPERTIES
                    config.get("app.page-size").asInt(),
                    is(simpleValue(10)));

        assertThat( // value from PROPERTIES
                    config.get("app.storageEnabled").asBoolean().orElse(false),
                    is(true));
    }

    // ADVANCED USE-CASES

    /**
     * My App Config bean.
     */
    public static class AppConfig {
        private String greeting;
        private String name;
        private int pageSize;
        private List<Integer> basicRange;
        private boolean storageEnabled;
        private String storagePassphrase;

        private AppConfig(String greeting,
                          String name,
                          int pageSize,
                          List<Integer> basicRange,
                          boolean storageEnabled,
                          String storagePassphrase) {
            this.greeting = greeting;
            this.name = name;
            this.pageSize = pageSize;
            this.basicRange = basicRange;
            this.storageEnabled = storageEnabled;
            this.storagePassphrase = storagePassphrase;
        }

        public String getGreeting() {
            return greeting;
        }

        public String getName() {
            return name;
        }

        public int getPageSize() {
            return pageSize;
        }

        public List<Integer> getBasicRange() {
            return basicRange;
        }

        public boolean isStorageEnabled() {
            return storageEnabled;
        }

        public String getStoragePassphrase() {
            return storagePassphrase;
        }
    }

    /**
     * Custom config mapper for {@link AppConfig} type.
     */
    public static class AppConfigMapper implements Function<Config, AppConfig> {
        @Override
        public AppConfig apply(Config node) throws ConfigMappingException, MissingValueException {
            String greeting = node.get("greeting").asString().get();
            String name = node.get("name").asString().get();
            int pageSize = node.get("page-size").asInt().get();
            List<Integer> basicRange = node.get("basic-range").asList(Integer.class).get();
            boolean storageEnabled = node.get("storageEnabled").asBoolean().orElse(false);
            String storagePassphrase = node.get("storagePassphrase").asString().get();

            return new AppConfig(greeting, name, pageSize, basicRange, storageEnabled, storagePassphrase);
        }
    }

    @Test
    public void testUseAppConfigMapper() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParser.create())
                .disableValueResolving()
                .build();

        AppConfig appConfig = config.get("app")
                .as(new AppConfigMapper()) // MAP using provided Mapper
                .get();

        assertThat(appConfig.getGreeting(), is("Hello"));
        assertThat(appConfig.getName(), is("Demo"));
        assertThat(appConfig.getPageSize(), is(20));
        assertThat(appConfig.getBasicRange(), contains(-20, 20));
        assertThat(appConfig.isStorageEnabled(), is(false));
        assertThat(appConfig.getStoragePassphrase(), is("${AES=thisIsEncriptedPassphrase}"));
    }

    @Test
    public void testRegisterAppConfigMapper() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParser.create())
                .addMapper(AppConfig.class, new AppConfigMapper())
                .disableValueResolving()
                .build();

        AppConfig appConfig = config.get("app")
                .as(AppConfig.class) // get AS type
                .get();

        assertThat(appConfig.getGreeting(), is("Hello"));
        assertThat(appConfig.getName(), is("Demo"));
        assertThat(appConfig.getPageSize(), is(20));
        assertThat(appConfig.getBasicRange(), contains(-20, 20));
        assertThat(appConfig.isStorageEnabled(), is(false));
        assertThat(appConfig.getStoragePassphrase(), is("${AES=thisIsEncriptedPassphrase}"));
    }

    @Test
    public void testSecurityFilter() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParser.create())
                .addFilter(new SecurityConfigFilter()) // custom config filter
                .disableValueResolving()
                .build();

        assertThat( // decrypted passphrase
                    config.get("app.storagePassphrase").asString(),
                    is(simpleValue("Password1.")));
    }

}
