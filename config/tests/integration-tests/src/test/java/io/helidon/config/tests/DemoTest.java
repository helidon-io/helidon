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

package io.helidon.config.tests;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigMapper;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.hocon.HoconConfigParserBuilder;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import org.junit.jupiter.api.Test;

/**
 * This test shows Client API use-cases.
 */
public class DemoTest {

    @Test
    public void testCreateConfig() {
        // looks for: application .yaml | .conf | .json | .properties on classpath
        Config config = Config.create();

        assertThat( // STRING
                    config.get("app.greeting").asString(),
                    is("Hello"));
    }

    @Test
    public void testTypedAccessors() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParserBuilder.buildDefault())
                .build();

        // ACCESSORS:
        // String, int, boolean, double, long,
        // OptionalInt, OptionalDouble, OptionalLong, Optional<T>,
        // List<String>, List<T>, Optional<List<String>>

        assertThat( // INT
                    config.get("app.page-size").asInt(),
                    is(20));

        assertThat( // boolean + DEFAULT
                    config.get("app.storageEnabled").asBoolean(false),
                    is(false));

        assertThat( // LIST <Integer>
                    config.get("app.basic-range").asList(Integer.class),
                    contains(-20, 20));

        assertThat( // BUILT-IN mapper for PATH
                    config.get("logging.outputs.file.name").as(Path.class),
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
                .addParser(HoconConfigParserBuilder.buildDefault())
                .build();

        // list of objects
        List<Config> securityProviders = config.get("security.providers")
                .asList(Config.class);

        assertThat(securityProviders.size(),
                   is(2)); // with 2 items

        assertThat(securityProviders.get(0).get("name").asString(),
                   is("BMCS")); // name of 1st provider

        assertThat(securityProviders.get(1).get("name").asString(),
                   is("ForEndUsers")); // name of 2nd provider
    }

    @Test
    public void testNodeChildrenAndTraverse() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParserBuilder.buildDefault())
                .build();

        System.out.println("Handlers:");

        // find out all configured logging outputs
        config.get("logging.outputs")
                .asNodeList() // DIRECT CHILDREN NODES
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
                .sources(ConfigSources.from(
                        // PROPERTIES first
                        ConfigSources.classpath("application.properties"),
                        // with fallback to HOCON
                        ConfigSources.classpath("application.conf")))
                .addParser(ConfigParsers.properties())
                .addParser(HoconConfigParserBuilder.buildDefault())
                .build();

        assertThat( // value from HOCON
                    config.get("app.greeting").asString(),
                    is("Hello"));

        assertThat( // value from PROPERTIES
                    config.get("app.page-size").asInt(),
                    is(10));

        assertThat( // value from PROPERTIES
                    config.get("app.storageEnabled").asBoolean(false),
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
     * Custom {@link ConfigMapper} for {@link AppConfig} type.
     */
    public static class AppConfigMapper implements ConfigMapper<AppConfig> {
        @Override
        public AppConfig apply(Config node) throws ConfigMappingException, MissingValueException {
            String greeting = node.get("greeting").asString();
            String name = node.get("name").asString();
            int pageSize = node.get("page-size").asInt();
            List<Integer> basicRange = node.get("basic-range").asList(Integer.class);
            boolean storageEnabled = node.get("storageEnabled").asBoolean(false);
            String storagePassphrase = node.get("storagePassphrase").asString();

            return new AppConfig(greeting, name, pageSize, basicRange, storageEnabled, storagePassphrase);
        }
    }

    @Test
    public void testUseAppConfigMapper() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.conf"))
                .addParser(HoconConfigParserBuilder.buildDefault())
                .disableValueResolving()
                .build();

        AppConfig appConfig = config.get("app")
                .map(new AppConfigMapper()); // MAP using provided Mapper

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
                .addParser(HoconConfigParserBuilder.buildDefault())
                .addMapper(AppConfig.class, new AppConfigMapper())
                .disableValueResolving()
                .build();

        AppConfig appConfig = config.get("app")
                .as(AppConfig.class); // get AS type

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
                .addParser(HoconConfigParserBuilder.buildDefault())
                .addFilter(new SecurityConfigFilter()) // custom config filter
                .disableValueResolving()
                .build();

        assertThat( // decrypted passphrase
                    config.get("app.storagePassphrase").asString(),
                    is("Password1."));
    }

}
