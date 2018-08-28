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

package io.helidon.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config.Type;
import io.helidon.config.GenericConfigMapper.SingleValueConfigImpl;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ConfigMappers} with focus on generic config mapping support, aka deserialization, see {@link GenericConfigMapper}.
 */
public class GenericConfigMapperTest {

    private static final boolean DEBUG = false;

    @Test
    public void testLoadWholeBean() {
        Config config = Config.builder()
                .sources(ConfigSources.from(prepareConfigApp(false, //uid
                                                             true, //greeting
                                                             true, //pageSize
                                                             true, //basicRange
                                                             true, //logging
                                                             true, //security
                                                             true) //names
                                                    .build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        print(config);

        assertThat(config.get("app.uid").type(), is(Type.MISSING));
        assertThat(config.get("app.security.providers.0.uid").type(), is(Type.MISSING));
        assertThat(config.get("app.security.providers.1.uid").type(), is(Type.MISSING));

        AppConfig appConfig = config.get("app").as(AppConfig.class);

        assertThat(appConfig.getUid(), is(nullValue()));
        assertThat(appConfig.getGreeting(), is("Hello"));
        assertThat(appConfig.isGreetingSetterCalled(), is(true));
        assertThat(appConfig.getPageSize(), is(20));
        assertThat(appConfig.getBasicRange(), contains(-10, 10));
        assertThat(appConfig.getLogging().getLevel(""), is(Optional.of("WARN")));
        assertThat(appConfig.getLogging().getLevel("io.helidon.config"), is(Optional.of("CONFIG")));
        assertThat(appConfig.getLogging().getLevel("my.app"), is(Optional.of("FINER")));
        assertThat(appConfig.getSecurity().getProviders().get(0).getUid(), is(nullValue()));
        assertThat(appConfig.getSecurity().getProviders().get(0).getName(), is("Provider1"));
        assertThat(appConfig.getSecurity().getProviders().get(0).getClazz(), equalTo(TestProvider1.class));
        assertThat(appConfig.getSecurity().getProviders().get(1).getUid(), is(nullValue()));
        assertThat(appConfig.getSecurity().getProviders().get(1).getName(), is("Provider2"));
        assertThat(appConfig.getSecurity().getProviders().get(1).getClazz(), equalTo(TestProvider2.class));
        assertThat(appConfig.getNames().entrySet(), hasSize(3));
        assertThat(appConfig.getNames(), hasEntry("app.names.pavel", "machr"));
        assertThat(appConfig.getNames(), hasEntry("app.names.ondrej", "hipsta"));
        assertThat(appConfig.getNames(), hasEntry("app.names.marek", "lebigboss"));
    }

    @Test
    public void testTransient() {
        Config config = Config.builder()
                .sources(ConfigSources.from(prepareConfigApp(true, //UID
                                                             true, //greeting
                                                             true, //pageSize
                                                             true, //basicRange
                                                             true, //logging
                                                             true, //security
                                                             true) //names
                                                    .build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        print(config);

        assertThat(config.get("app.uid").type(), is(Type.VALUE));
        assertThat(config.get("app.security.providers.0.uid").type(), is(Type.VALUE));
        assertThat(config.get("app.security.providers.1.uid").type(), is(Type.VALUE));

        AppConfig appConfig = config.get("app").as(AppConfig.class);
        assertThat(appConfig.getUid(), is(nullValue()));
        assertThat(appConfig.getSecurity().getProviders().get(0).getUid(), is(nullValue()));
        assertThat(appConfig.getSecurity().getProviders().get(1).getUid(), is(nullValue()));
    }

    @Test
    public void testNotSetValues() {
        Config config = Config.builder()
                .sources(ConfigSources.from(prepareConfigApp(false, //uid
                                                             true, //greeting
                                                             true, //pageSize
                                                             true, //basicRange
                                                             false, //LOGGING
                                                             false, //SECURITY
                                                             false) //NAMES
                                                    .build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        print(config);

        AppConfig appConfig = config.get("app").as(AppConfig.class);

        assertThat(appConfig.getLogging().getLevel(""), is(Optional.empty()));
        assertThat(appConfig.getLogging().getLevel("io.helidon.config"), is(Optional.empty()));
        assertThat(appConfig.getLogging().getLevel("my.app"), is(Optional.empty()));
        assertThat(appConfig.getSecurity(), is(nullValue()));
        assertThat(appConfig.getNames().entrySet(), hasSize(0));
    }

    @Test
    public void testWithDefault() {
        Config config = Config.builder()
                .sources(ConfigSources.from(prepareConfigApp(false, //uid
                                                             true, //greeting
                                                             false, //PAGESIZE
                                                             true, //basicRange
                                                             true, //logging
                                                             true, //security
                                                             true) //names
                                                    .build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        print(config);

        AppConfig appConfig = config.get("app").as(AppConfig.class);
        assertThat(appConfig.getPageSize(), is(10));
    }

    @Test
    public void testWithDefaultSupplier() {
        Config config = Config.builder()
                .sources(ConfigSources.from(prepareConfigApp(false, //uid
                                                             true, //greeting
                                                             true, //pageSize
                                                             false, //BASICRANGE
                                                             true, //logging
                                                             true, //security
                                                             true) //names
                                                    .build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        print(config);

        AppConfig appConfig = config.get("app").as(AppConfig.class);
        assertThat(appConfig.getBasicRange(), contains(-5, 5));
    }

    @Test
    public void testWithDefaultWrongFormat() {
        Config config = Config.withSources(ConfigSources.from(CollectionsHelper.mapOf("numberWithDefaultSupplier", "42")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Assertions.assertThrows(ConfigMappingException.class, () -> {
            config.as(WrongDefaultBean.class);
        });
    }

    @Test
    public void testWithDefaultSupplierWrongReturnType() {
        Config config = Config.withSources(ConfigSources.from(CollectionsHelper.mapOf("numberWithDefault", "23")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Assertions.assertThrows(ConfigMappingException.class, () -> {
            config.as(WrongDefaultBean.class);
        });
    }

    @Test
    public void testWrongDefaultsNotUsed() {
        Config config = Config.withSources(ConfigSources.from(CollectionsHelper.mapOf("numberWithDefault", "23",
                                                                     "numberWithDefaultSupplier", "42")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        WrongDefaultBean wrongDefaultBean = config.as(WrongDefaultBean.class);

        assertThat(wrongDefaultBean.numberWithDefault, is(23));
        assertThat(wrongDefaultBean.numberWithDefaultSupplier, is(42));
    }

    private static void print(Config config) {
        if (DEBUG) {
            config.asMap()
                    .forEach((key, value) -> System.out.println("    " + key + " = " + value));
        }
    }

    /**
     * Initializes Config to be loaded into {@link AppConfig}.
     */
    private static ObjectNode.Builder prepareConfigApp(boolean uid,
                                                       boolean greeting,
                                                       boolean pageSize,
                                                       boolean basicRange,
                                                       boolean logging,
                                                       boolean security,
                                                       boolean names) {
        ObjectNode.Builder app = ObjectNode.builder();
        if (uid) {
            app.addValue("uid", UUID.randomUUID().toString());
        }
        if (greeting) {
            app.addValue("greeting", "Hello");
        }
        if (pageSize) {
            app.addValue("page_size", "20");
        }
        if (basicRange) {
            app.addList("basic-range", ListNode.builder()
                    .addValue("-10")
                    .addValue("10")
                    .build());
        }
        if (logging) {
            app.addObject("logging", ObjectNode.builder()
                    .addValue("level", "WARN")
                    .addValue("io.helidon.config.level", "CONFIG")
                    .addValue("my.app.level", "FINER")
                    .build());
        }
        if (security) {
            app.addObject("security", ObjectNode.builder()
                    .addList("providers", ListNode.builder()
                            .addObject(ObjectNode.builder()
                                               .addValue("name", "Provider1")
                                               .addValue("class", "io.helidon.config.GenericConfigMapperTest$TestProvider1")
                                               .build())
                            .addObject(ObjectNode.builder()
                                               .addValue("name", "Provider2")
                                               .addValue("class", "io.helidon.config.GenericConfigMapperTest$TestProvider2")
                                               .build())
                            .build()
                    ).build());
            if (uid) {
                app.addValue("security.providers.0.uid", UUID.randomUUID().toString());
                app.addValue("security.providers.1.uid", UUID.randomUUID().toString());
            }
        }
        if (names) {
            app.addObject("names", ObjectNode.builder()
                    .addValue("pavel", "machr")
                    .addValue("ondrej", "hipsta")
                    .addValue("marek", "lebigboss")
                    .build());
        }

        return ObjectNode.builder().addObject("app", app.build());
    }

    @Test
    public void testSingleValueConfigImpl() {
        ConfigMapperManager mapperManager = new ConfigMapperManager(ConfigMappers.builtInMappers());

        SingleValueConfigImpl config = new SingleValueConfigImpl(mapperManager, "key1", "42");

        assertThat(config.key(), is(Config.Key.of("key1")));
        assertThat(config.value(), is(Optional.of("42")));
        assertThat(config.type(), is(Type.VALUE));
        assertThat(config.timestamp(), not(nullValue()));
        {
            Config sub = config.get("sub");
            assertThat(sub.key(), is(Config.Key.of("key1.sub")));
            assertThat(sub.value(), is(Optional.empty()));
            assertThat(sub.type(), is(Type.MISSING));
        }
        {
            Config detached = config.detach();
            assertThat(detached.key(), is(Config.Key.of("")));
            assertThat(detached.value(), is(Optional.of("42")));
            assertThat(detached.type(), is(Type.VALUE));
        }
        assertThat(config.traverse().collect(Collectors.toList()), empty());
        {
            Map<String, String> map = config.asMap();
            assertThat(map.entrySet(), hasSize(1));
            assertThat(map, hasEntry("key1", "42"));
        }
        assertThat(config.as(Integer.class), is(42));
        try {
            config.nodeList();
            Assertions.fail("Did not detect expected ConfigMappingException");
        } catch (ConfigMappingException ex) {
            //expected
        }
        try {
            config.asOptionalList(String.class);
        } catch (ConfigMappingException ex) {
            //expected
        }
    }

    /**
     * Represents JavaBean that can be initialized by generic mapping support.
     * <p>
     * It accepts following config structure:
     * <pre>
     * app {
     *     greeting= Hello
     *     page_size= 20
     *     basic-range= [ -10, 10 ]
     *     logging {
     *         level= WARN
     *         io.helidon.config.level= CONFIG
     *         my.app.level= FINER
     *     }
     *     security {
     *         providers: [
     *             {
     *                 name= Provider1
     *                 class= io.helidon.config.GenericConfigMapperTest$TestProvider1
     *             },
     *             {
     *                 name= Provider2
     *                 class= io.helidon.config.GenericConfigMapperTest$TestProvider2
     *             }
     *         ]
     *     names {
     *         pavel= machr
     *         ondrej= hipsta
     *         marek= lebigboss
     *     }
     * }
     * </pre>
     * Following JavaBean fields are marked as {@link Config.Transient}, i.e. will not be loaded from configuration:
     * <pre>
     * app.uid
     * app.security.providers.*.uid
     * </pre>
     */
    public static class AppConfig {
        public Long uid;
        public String greeting;
        private Boolean greetingSetterCalled;
        @Config.Value(key = "page-size", withDefault = "42") //ignored, used on setter
        private Integer pageSize;
        private List<Integer> basicRange;
        private LoggingConfig logging = new LoggingConfig(Config.empty());
        @Config.Value(withDefaultSupplier = DefaultSecurityConfigSupplier.class) //ignored, used on setter
        public SecurityConfig security;
        public Map<String, String> names = CollectionsHelper.mapOf();

        public AppConfig() {
            greetingSetterCalled = false;
            names = CollectionsHelper.mapOf();
        }

        public Long getUid() {
            return uid;
        }

        @Config.Transient
        public void setUid(long uid) {
            this.uid = uid;
        }

        public String getGreeting() {
            return greeting;
        }

        public void setGreeting(String greeting) {
            this.greeting = greeting;
            greetingSetterCalled = true;
        }

        public boolean isGreetingSetterCalled() {
            return greetingSetterCalled;
        }

        public int getPageSize() {
            return pageSize;
        }

        @Config.Value(withDefault = "10")
        public void setPage_size(int pageSize) {
            this.pageSize = pageSize;
        }

        public List<Integer> getBasicRange() {
            return basicRange;
        }

        @Config.Value(key = "basic-range",
                      withDefault = "ignored value",
                      withDefaultSupplier = DefaultBasicRangeSupplier.class)
        public void setBasicRange(List<Integer> basicRange) {
            this.basicRange = basicRange;
        }

        public void setLogging(LoggingConfig loggingConfig) {
            this.logging = loggingConfig;
        }

        @Config.Value
        public void setSecurity(SecurityConfig securityConfig) {
            this.security = securityConfig;
        }

        public Boolean getGreetingSetterCalled() {
            return greetingSetterCalled;
        }

        public LoggingConfig getLogging() {
            return logging;
        }

        public SecurityConfig getSecurity() {
            return security;
        }

        public Map<String, String> getNames() {
            return names;
        }

        /**
         * To test implicit {@link LoggingConfig#from(Config)} mapping support for custom objects.
         */
        public static class LoggingConfig {
            private Config config;

            private LoggingConfig(Config config) {
                this.config = config;
            }

            public static LoggingConfig from(Config config) {
                return new LoggingConfig(config);
            }

            public Optional<String> getLevel(String name) {
                return config.get(name).get("level").asOptionalString();
            }
        }

        /**
         * Contains list of other objects.
         */
        public static class SecurityConfig {
            public List<ProviderConfig> providers;

            public SecurityConfig() {
                providers = CollectionsHelper.listOf();
            }

            public List<ProviderConfig> getProviders() {
                return providers;
            }

            public void setProviders(List<ProviderConfig> providers) {
                this.providers = providers;
            }
        }

        public static class ProviderConfig {
            @Config.Transient
            public Long uid;
            public String name;
            @Config.Value(key = "class")
            public Class<?> clazz;

            public Long getUid() {
                return uid;
            }

            public String getName() {
                return name;
            }

            public Class<?> getClazz() {
                return clazz;
            }
        }

        public static class DefaultBasicRangeSupplier implements Supplier<List<Integer>> {
            @Override
            public List<Integer> get() {
                return CollectionsHelper.listOf(-5, 5);
            }
        }

        public static class DefaultSecurityConfigSupplier implements Supplier<SecurityConfig> {
            @Override
            public SecurityConfig get() {
                return new SecurityConfig();
            }
        }
    }

    /**
     * @see AppConfig.ProviderConfig
     */
    public static class TestProvider1 {
    }

    /**
     * @see AppConfig.ProviderConfig
     */
    public static class TestProvider2 {
    }

    public static class WrongDefaultBean {
        @Config.Value(withDefault = "wrong value")
        public int numberWithDefault;
        @Config.Value(withDefaultSupplier = UuidStringSupplier.class)
        public int numberWithDefaultSupplier;
    }

    public static class UuidStringSupplier implements Supplier<String> {
        @Override
        public String get() {
            return UUID.randomUUID().toString();
        }
    }

}
