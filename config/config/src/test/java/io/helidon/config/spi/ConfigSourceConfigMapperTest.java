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

package io.helidon.config.spi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.internal.ClasspathConfigSource;
import io.helidon.config.internal.DirectoryConfigSource;
import io.helidon.config.internal.FileConfigSource;
import io.helidon.config.internal.MapConfigSource;
import io.helidon.config.internal.PrefixedConfigSource;
import io.helidon.config.internal.UrlConfigSource;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import com.xebialabs.restito.server.StubServer;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Action.stringContent;
import static com.xebialabs.restito.semantics.Condition.get;
import io.helidon.config.test.infra.TemporaryFolderExt;
import static org.glassfish.grizzly.http.util.HttpStatus.OK_200;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests {@link ConfigSourceConfigMapper}.
 */
public class ConfigSourceConfigMapperTest {

    private static final String TEST_SYS_PROP_NAME = "this_is_my_property-ConfigSourceConfigMapperTest";
    private static final String TEST_SYS_PROP_VALUE = "This Is My SYS PROPS Value.";
    private static final String TEST_ENV_VAR_NAME = "CONFIG_SOURCE_TEST_PROPERTY";
    private static final String TEST_ENV_VAR_VALUE = "This Is My ENV VARS Value.";
    private static final String RELATIVE_PATH_TO_RESOURCE = "/src/test/resources/";

    @RegisterExtension
    public TemporaryFolderExt folder = TemporaryFolderExt.build();

    @Test
    @ExtendWith(RestoreSystemPropertiesExt.class)
    public void testSystemProperties() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "system-properties")
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MapConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get(TEST_SYS_PROP_NAME).asString(), is(TEST_SYS_PROP_VALUE));
    }

    @Test
    public void testEnvironmentVariables() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "environment-variables")
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MapConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get(TEST_ENV_VAR_NAME).asString(), is(TEST_ENV_VAR_VALUE));
    }

    @Test
    public void testClasspath() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "classpath")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("resource", "io/helidon/config/application.properties")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(ClasspathConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("app.page-size").asInt(), is(10));
    }

    @Test
    public void testFile() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "file")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("path", getDir() + "io/helidon/config/application.properties")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(FileConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("app.page-size").asInt(), is(10));
    }

    @Test
    public void testDirectory() throws IOException {
        File folder = this.folder.newFolder();
        Files.write(Files.createFile(new File(folder, "username").toPath()), "libor".getBytes());
        Files.write(Files.createFile(new File(folder, "password").toPath()), "^ery$ecretP&ssword".getBytes());

        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "directory")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("path", folder.getAbsolutePath())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(DirectoryConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("username").asString(), is("libor"));
        assertThat(config.get("password").asString(), is("^ery$ecretP&ssword"));
    }

    @Test
    public void testUrl() {
        StubServer server = new StubServer().run();
        try {
            whenHttp(server)
                    .match(get("/application.properties"))
                    .then(status(OK_200),
                          stringContent("greeting = Hello"));

            Config metaConfig = justFrom(ConfigSources.from(
                    ObjectNode.builder()
                            .addValue("type", "url")
                            .addObject("properties", ObjectNode.builder()
                                    .addValue("url", String.format("http://127.0.0.1:%d/application.properties",
                                                                   server.getPort()))
                                    .build())
                            .build()));

            ConfigSource source = metaConfig.as(ConfigSource.class);

            assertThat(source, is(instanceOf(UrlConfigSource.class)));

            Config config = justFrom(source);

            assertThat(config.get("greeting").asString(), is("Hello"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void testPrefixed() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "prefixed")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("key", "this.is.prefix.key")
                                .addValue("type", "classpath")
                                .addObject("properties", ObjectNode.builder()
                                        .addValue("resource", "io/helidon/config/application.properties")
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(PrefixedConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("this.is.prefix.key.app.page-size").asInt(), is(10));
    }

    @Test
    public void testCustomClass() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSource.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(23));
        assertThat(config.get("enabled").asBoolean(), is(true));
    }

    @Test
    public void testCustomClassBuilder() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(23));
        assertThat(config.get("enabled").asBoolean(), is(true));
    }

    @Test
    public void testCustomType() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "testing1")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(23));
        assertThat(config.get("enabled").asBoolean(), is(true));
    }

    @Test
    public void testCustomTypeBuilder() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "testing2")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(23));
        assertThat(config.get("enabled").asBoolean(), is(true));
    }

    private static String getDir() {
        return Paths.get("").toAbsolutePath().toString() + RELATIVE_PATH_TO_RESOURCE;
    }

    static Config justFrom(ConfigSource source) {
        return Config.withSources(source).disableEnvironmentVariablesSource().disableSystemPropertiesSource().build();
    }

    /**
     * Testing implementation of config source.
     */
    public static class MyConfigSource implements ConfigSource {

        private final MyEndpoint endpoint;
        private final boolean myProp3;
        private final PollingStrategy pollingStrategy;
        private final RetryPolicy retryPolicy;

        public MyConfigSource(MyEndpoint endpoint,
                              boolean myProp3,
                              PollingStrategy pollingStrategy,
                              RetryPolicy retryPolicy) {
            this.endpoint = endpoint;
            this.myProp3 = myProp3;
            this.pollingStrategy = pollingStrategy;
            this.retryPolicy = retryPolicy;
        }

        public static MyConfigSource from(@Config.Value(key = "myProp1") String myProp1,
                                          @Config.Value(key = "myProp2") int myProp2,
                                          @Config.Value(key = "myProp3") boolean myProp3) {
            return new MyConfigSource(new MyEndpoint(myProp1, myProp2), myProp3, null, null);
        }

        @Override
        public Optional<ObjectNode> load() throws ConfigException {
            return Optional.of(ObjectNode.builder()
                                       .addValue(endpoint.myProp1, Objects.toString(endpoint.myProp2))
                                       .addValue("enabled", Objects.toString(myProp3))
                                       .build());
        }

        public PollingStrategy getPollingStrategy() {
            return pollingStrategy;
        }

        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }

        @Override
        public String toString() {
            return "MyConfigSource{"
                    + "endpoint=" + endpoint
                    + ", myProp3=" + myProp3
                    + '}';
        }
    }

    /**
     * Testing implementation of config source builder.
     */
    public static class MyConfigSourceBuilder
            extends AbstractSource.Builder<MyConfigSourceBuilder, MyEndpoint, ConfigSource> {

        private final MyEndpoint endpoint;
        private boolean myProp3;

        private MyConfigSourceBuilder(MyEndpoint endpoint) {
            super(MyEndpoint.class);
            this.endpoint = endpoint;
        }

        public static MyConfigSourceBuilder from(String myProp1, int myProp2) {
            return new MyConfigSourceBuilder(new MyEndpoint(myProp1, myProp2));
        }

        public static MyConfigSourceBuilder from(Config metaConfig) throws ConfigMappingException, MissingValueException {
            return from(metaConfig.get("myProp1").asString(),
                        metaConfig.get("myProp2").asInt())
                    .init(metaConfig);
        }

        @Override
        protected MyConfigSourceBuilder init(Config metaConfig) {
            metaConfig.get("myProp3").asOptionalBoolean().ifPresent(this::myProp3);
            return super.init(metaConfig);
        }

        public MyConfigSourceBuilder myProp3(boolean myProp3) {
            this.myProp3 = myProp3;
            return this;
        }

        @Override
        protected MyEndpoint getTarget() {
            return endpoint;
        }

        public ConfigSource build() {
            return new MyConfigSource(endpoint, myProp3, getPollingStrategy(), getRetryPolicy());
        }
    }

    /**
     * Testing implementation of config source endpoint.
     */
    public static class MyEndpoint {
        private final String myProp1;
        private final int myProp2;

        public MyEndpoint(String myProp1, int myProp2) {
            this.myProp1 = myProp1;
            this.myProp2 = myProp2;
        }

        public String getMyProp1() {
            return myProp1;
        }

        public int getMyProp2() {
            return myProp2;
        }

        @Override
        public String toString() {
            return "MyEndpoint{"
                    + "myProp1='" + myProp1 + '\''
                    + ", myProp2=" + myProp2
                    + '}';
        }
    }

}
