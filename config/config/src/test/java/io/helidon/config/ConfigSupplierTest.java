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

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigTest.waitForAssert;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/**
 * Tests related to {@link ConfigValue#optionalSupplier()} ()} and other {@code *Supplier()} methods.
 */
public class ConfigSupplierTest {

    private static final int TEST_DELAY_MS = 1;

    @Test
    public void testSupplierFromMissingToObjectNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .optional()
                .testingPollingStrategy()
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Supplier<Optional<Config>> supplier = config.get("key1").asNode().optionalSupplier();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure time changes to trigger notification.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addObject("key1", ObjectNode.builder()
                                .addValue("sub1", "string value")
                                .build())
                        .build());

        // new: key exists
        waitForAssert(() -> supplier.get().isPresent(), is(true));
        waitForAssert(() -> supplier.get().get().type(), is(Config.Type.OBJECT));
        waitForAssert(() -> supplier.get().get().get("sub1").asString(), is(ConfigValues.simpleValue("string value")));
    }

    @Test
    public void testSupplierSubscribeOnLeafNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .optional()
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Supplier<Optional<String>> supplier = config.get("key1.sub1").asString().optionalSupplier();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure time changes to trigger notification.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addObject("key1", ObjectNode.builder()
                                .addValue("sub1", "string value")
                                .build())
                        .build());

        waitForAssert(() -> supplier.get().isPresent(), is(true));
        waitForAssert(() -> supplier.get().get(), is("string value"));

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure time changes to trigger notification.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addObject("key1", ObjectNode.builder()
                                .addValue("sub1", "new value")
                                .build())
                        .build());

        // new: key exists
        waitForAssert(() -> supplier.get().isPresent(), is(true));
        waitForAssert(() -> supplier.get().get(), is("new value"));
    }

    @Test
    public void testSupplierSubscribeOnParentNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .objectNode(
                        ObjectNode.builder().addValue("key-1-1.key-2-1", "item 1").build())
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // register subscriber1
        Supplier<Optional<Config>> configSupplier = config.get("key-1-1").asNode().optionalSupplier();
        // register subscriber2 on DETACHED leaf
        Supplier<Optional<Config>> detachedConfigSupplier = config.get("key-1-1")
                .detach()
                .asNode()
                .optionalSupplier();

        // wait for event
        assertThat(configSupplier.get().isPresent(), is(true));
        assertThat(configSupplier.get().get().key().toString(), is("key-1-1"));

        assertThat(detachedConfigSupplier.get().isPresent(), is(true));
        assertThat(detachedConfigSupplier.get().get().key().toString(), is(""));

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure time changes to trigger notification.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "NEW item 1").build());

        // wait for event
        waitForAssert(() -> configSupplier.get().isPresent(), is(true));
        waitForAssert(() -> configSupplier.get().get().get("key-2-1").asString(), is(ConfigValues.simpleValue("NEW item 1")));

        waitForAssert(() -> detachedConfigSupplier.get().isPresent(), is(true));
        waitForAssert(() -> detachedConfigSupplier.get().get().get("key-2-1").asString(),
                      is(ConfigValues.simpleValue("NEW item 1")));
    }

    @Test
    public void testSupplierSubscribeOnRootNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .objectNode(
                        ObjectNode.builder().addValue("key-1-1.key-2-1", "item 1").build())
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Supplier<Optional<Config>> optionalSupplier = config.asNode().optionalSupplier();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure time changes to trigger notification.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "NEW item 1").build());

        // wait for event
        waitForAssert(() -> optionalSupplier.get().isPresent(), is(true));
        waitForAssert(() -> optionalSupplier.get().get().key().toString(), is(""));
        waitForAssert(() -> optionalSupplier.get().get().get("key-1-1.key-2-1").asString(),
                      is(ConfigValues.simpleValue("NEW item 1")));
    }

    @Disabled
    @Test
    // TODO cause of intermittent test failures:
    /*
    Tests in error:
    ConfigSupplierTest.testSupplierFromMissingToListNode:209->lambda$testSupplierFromMissingToListNode$16:216 » IllegalState
     */
    public void testSupplierFromMissingToListNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .optional()
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // key does not exist
        assertThat(config.get("key1").exists(), is(false));

        // register subscriber
        Supplier<Optional<Config>> nodeSupplier = config.get("key1").asNode().optionalSupplier();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure time changes to trigger notification.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addList("key1", ConfigNode.ListNode.builder()
                                .addValue("item 1")
                                .addValue("item 2")
                                .build())
                        .build());

        // new: key exists
        waitForAssert(() -> nodeSupplier.get().isPresent(), is(true));
        waitForAssert(() -> nodeSupplier.get().get().type(), is(Config.Type.LIST));
        waitForAssert(() -> nodeSupplier.get().get().asList(String.class).get(), contains("item 1", "item 2"));
    }

}
