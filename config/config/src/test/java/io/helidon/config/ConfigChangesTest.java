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

package io.helidon.config;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests related to {@link Config#onChange(java.util.function.Consumer)}.
 */
public class ConfigChangesTest {

    private static final int TEST_DELAY_MS = 1;

    @Test
    public void testChangesFromMissingToObjectNode() throws InterruptedException {
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
        ConfigChangeListener listener = new ConfigChangeListener();
        config.get("key1").onChange(listener::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addObject("key1", ObjectNode.builder()
                                .addValue("sub1", "string value")
                                .build())
                        .build());

        // wait for event
        Config newConfig = listener.get(500, true);

        // new: key exists
        assertThat(newConfig.exists(), is(true));
        assertThat(newConfig.type(), is(Config.Type.OBJECT));
        assertThat(newConfig.get("sub1").asString().get(), is("string value"));
    }

    @Test
    public void testNoChangesComeFromSiblingNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder()
                        .addObject("key-1-1", ObjectNode.builder()
                                .addValue("key-2-1", "item 1")
                                .addValue("key-2-2", "item 2")
                                .build())
                        .build()).build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // key does not exist
        assertThat(config.get("key-1-1").exists(), is(true));

        // register subscriber
        ConfigChangeListener listener = new ConfigChangeListener();
        config.get("key-1-1.key-2-1").onChange(listener::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.        
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addObject("key-1-1", ObjectNode.builder()
                                .addValue("key-2-1", "item 1")
                                .addValue("key-2-2", "NEW item 2")
                                .build())
                        .build());

        // wait for event
        assertThat(listener.get(500, false), is(nullValue()));
    }

    @Test
    public void testChangesSubscribeOnLeafNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .objectNode(
                        ObjectNode.builder()
                                .addValue("key-1-1.key-2-1", "item 1")
                                .build())
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // register subscriber1 on original leaf
        ConfigChangeListener listener1 = new ConfigChangeListener();
        config.get("key-1-1.key-2-1")
                .onChange(listener1::onChange);

        // register subscriber2 on leaf of DETACHED parent
        ConfigChangeListener listener2 = new ConfigChangeListener();
        config.get("key-1-1")
                .detach()
                .get("key-2-1")
                .onChange(listener2::onChange);

        // register subscriber3 on DETACHED leaf
        ConfigChangeListener listener3 = new ConfigChangeListener();
        config.get("key-1-1.key-2-1")
                .detach()
                .onChange(listener3::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(ObjectNode.simple("key-1-1.key-2-1", "NEW item 1"));

        // wait for event1
        Config last1 = listener1.get(200, true);
        assertThat(last1.key().toString(), is("key-1-1.key-2-1"));
        assertThat(last1.asString().get(), is("NEW item 1"));

        // wait for event2
        Config last2 = listener2.get(200, true);
        assertThat(last2.key().toString(), is("key-2-1"));
        assertThat(last2.asString().get(), is("NEW item 1"));

        // wait for event3
        Config last3 = listener3.get(200, true);
        assertThat(last3.key().toString(), is(""));
        assertThat(last3.asString().get(), is("NEW item 1"));

        // no other events
        listener1.reset();
        listener2.reset();
        listener3.reset();

        // no need to wait for a long time, the event will not come
        assertThat(listener1.get(50, false), is(nullValue()));
        assertThat(listener2.get(50, false), is(nullValue()));
        assertThat(listener3.get(50, false), is(nullValue()));
    }

    @Test
    public void testChangesSubscribeOnParentNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .objectNode(
                        ObjectNode.builder()
                                .addValue("key-1-1.key-2-1", "item 1")
                                .build())
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // register subscriber1
        ConfigChangeListener subscriber1 = new ConfigChangeListener();
        config.get("key-1-1")
                .onChange(subscriber1::onChange);

        // register subscriber2 on DETACHED leaf
        ConfigChangeListener subscriber2 = new ConfigChangeListener();
        config.get("key-1-1")
                .detach()
                .onChange(subscriber2::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "NEW item 1").build());

        // wait for event
        assertThat(subscriber1.get(200, true).key().toString(), is("key-1-1"));

        // wait for event1
        Config last1 = subscriber1.get(200, true);
        assertThat(last1.key().toString(), is("key-1-1"));
        assertThat(last1.get("key-2-1").asString().get(), is("NEW item 1"));

        // wait for event2
        Config last2 = subscriber2.get(200, true);
        assertThat(last2.key().toString(), is(""));
        assertThat(last2.get("key-2-1").asString().get(), is("NEW item 1"));

        // no other events
        subscriber1.reset();
        subscriber2.reset();
        assertThat(subscriber1.get(50, false), is(nullValue()));
        assertThat(subscriber2.get(50, false), is(nullValue()));
    }

    @Test
    public void testChangesSubscribeOnRootNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .objectNode(
                        ObjectNode.builder()
                                .addValue("key-1-1.key-2-1", "item 1")
                                .build())
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // register subscriber1
        ConfigChangeListener subscriber1 = new ConfigChangeListener();
        config.onChange(subscriber1::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "NEW item 1").build());

        // wait for event
        Config last1 = subscriber1.get(200, true);
        assertThat(last1.key().toString(), is(""));
        assertThat(last1.get("key-1-1.key-2-1").asString().get(), is("NEW item 1"));

        // no other events
        subscriber1.reset();
        assertThat(subscriber1.get(50, false), is(nullValue()));
    }

    @Test
    public void testChangesJustSingleSubscriptionOnConfigSource() throws InterruptedException {
        //config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .optional()
                .build();

        AbstractConfigImpl config = (AbstractConfigImpl) Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .sources(configSource)
                .build();

        List<ConfigChangeListener> subscribers = new LinkedList<>();
        List.of("", "key1", "sub.key1", "", "key1").forEach(key -> {
            ConfigChangeListener subscriber = new ConfigChangeListener();
            config.get(key).onChange(subscriber::onChange);
            subscribers.add(subscriber);
        });
    }

    @Test
    public void testChangesFromMissingToListNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .optional()
                .build();

        // config
        Config config = Config.builder().sources(configSource).build();

        // key does not exist
        assertThat(config.get("key1").exists(), is(false));

        // register subscriber
        ConfigChangeListener subscriber = new ConfigChangeListener();
        config.get("key1").onChange(subscriber::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addList("key1", ConfigNode.ListNode.builder()
                                .addValue("item 1")
                                .addValue("item 2")
                                .build())
                        .build());

        // wait for event
        Config newConfig = subscriber.get(1000, true);

        // new: key exists
        assertThat(newConfig.exists(), is(true));
        assertThat(newConfig.type(), is(Config.Type.LIST));
        assertThat(newConfig.asList(String.class).get(), contains("item 1", "item 2"));
    }

    @Test
    public void testChangesFromMissingToValueNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .optional()
                .build();

        // config
        Config config = Config.builder().sources(configSource).build();

        // key does not exist
        assertThat(config.get("key1").exists(), is(false));

        // register subscriber
        ConfigChangeListener subscriber = new ConfigChangeListener();
        config.get("key1").onChange(subscriber::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addValue("key1", "string value")
                        .build());

        // wait for event
        Config newConfig = subscriber.get(1000, true);

        // new: key exists
        assertThat(newConfig.exists(), is(true));
        assertThat(newConfig.type(), is(Config.Type.VALUE));
        assertThat(newConfig.asString().get(), is("string value"));
    }

    @Test
    public void testChangesFromObjectNodeToMissing() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .optional()
                .objectNode(ObjectNode.builder()
                                    .addObject("key1", ObjectNode.builder()
                                            .addValue("sub1", "string value")
                                            .build())
                                    .build())
                .build();

        // config
        Config config = Config.builder(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // key exists
        assertThat(config.get("key1").exists(), is(true));
        assertThat(config.get("key1").type(), is(Config.Type.OBJECT));
        assertThat(config.get("key1").get("sub1").asString().get(), is("string value"));

        // register subscriber
        ConfigChangeListener subscriber = new ConfigChangeListener();
        config.get("key1").onChange(subscriber::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(null);

        // wait for event
        Config newConfig = subscriber.get(1000, true);

        // new: key does not exist
        assertThat("New config should not exist", newConfig.exists(), is(false));
    }

    @Test
    public void testChangesFromListNodeToMissing() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .optional()
                .objectNode(ObjectNode.builder()
                                    .addList("key1", ConfigNode.ListNode.builder()
                                            .addValue("item 1")
                                            .addValue("item 2")
                                            .build())
                                    .build())
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // key exists
        assertThat(config.get("key1").exists(), is(true));
        assertThat(config.get("key1").type(), is(Config.Type.LIST));
        assertThat(config.get("key1").asList(String.class).get(), contains("item 1", "item 2"));

        // register subscriber
        ConfigChangeListener subscriber = new ConfigChangeListener();
        config.get("key1").onChange(subscriber::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(null);

        // wait for event
        Config newConfig = subscriber.get(1000, true);

        // new: key does not exist
        assertThat(newConfig.exists(), is(false));
    }

    @Test
    public void testChangesFromValueNodeToMissing() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .optional()
                .objectNode(
                        ObjectNode.builder()
                                .addValue("key1", "string value")
                                .build())
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // key does not exist
        assertThat(config.get("key1").exists(), is(true));
        assertThat(config.get("key1").type(), is(Config.Type.VALUE));
        assertThat(config.get("key1").asString().get(), is("string value"));

        // register subscriber
        ConfigChangeListener subscriber = new ConfigChangeListener();
        config.get("key1").onChange(subscriber::onChange);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(null);

        // wait for event
        Config newConfig = subscriber.get(1000, true);

        // new: key does not exist
        assertThat(newConfig.exists(), is(false));
    }

    @Test
    public void testOnChangeValueChanged() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .objectNode(
                        ObjectNode.builder()
                                .addValue("key1", "string value")
                                .build())
                .build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // key does not exist
        assertThat(config.get("key1").exists(), is(true));
        assertThat(config.get("key1").type(), is(Config.Type.VALUE));
        assertThat(config.get("key1").asString().get(), is("string value"));

        //MOCK onNextFunction
        CountDownLatch onNextLatch = new CountDownLatch(1);
        AtomicReference<Config> newConfigReference = new AtomicReference<>();

        Consumer<Config> onNextFunction = aConfig -> {
            newConfigReference.set(aConfig);
            onNextLatch.countDown();
        };

        // register subscriber
        config.get("key1").onChange(onNextFunction);
        // wait for init
        TimeUnit.MILLISECONDS.sleep(20);

        // change config source
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue("key1", "string value 2").build());

        // wait for event
        int changeTimeout = 3;
        if (!onNextLatch.await(changeTimeout, TimeUnit.SECONDS)) {
            fail("Change did not come within the expected timeout of " + changeTimeout + " seconds");
        }

        // verify event
        Config newConfig = newConfigReference.get();

        assertThat(newConfig, notNullValue());
        // new: key does exist
        assertThat(newConfig.exists(), is(true));
        assertThat(newConfig.type(), is(Config.Type.VALUE));
        assertThat(newConfig.asString().get(), is("string value 2"));
    }

    @Test
    public void testChangesSendLastLoadedConfigToNewSubscribers() throws InterruptedException {
        String key1 = "key1";
        String fullKey = "parent." + key1;
        /////////////////////// subscribe before any change
        // create new config v1
        TestingConfigSource configSource = TestingConfigSource.builder()
                .testingPollingStrategy()
                .objectNode(ObjectNode.builder()
                                    .addValue(fullKey, "value")
                                    .build())
                .build();
        Config v1 = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build()
                .get("parent");

        assertThat(v1.get(key1).asString().get(), is("value"));
        // subscribe s1 on v1
        ConfigChangeListener s1 = new ConfigChangeListener();
        v1.onChange(s1::onChange);

        ///////////////////////////// FIRST change -> subscriber receives event
        // change source => config v2
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue(fullKey, "value 2").build());
        // s1 receives v2
        Config v2 = s1.get(200, true);
        assertThat(v2.get(key1).asString().get(), is("value 2"));
        s1.reset();

        ConfigChangeListener s2 = new ConfigChangeListener();
        v1.onChange(s2::onChange);

        ///////////////////////////// another change -> BOTH subscribers receives NEW event
        // change source => config v3
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue(fullKey, "value 3").build());
        // s1 receives v3
        Config v3 = s1.get(200, true);
        assertThat(v3.get(key1).asString(), is(ConfigValues.simpleValue("value 3")));
        s1.reset();
        // s2 receives v3
        Config s2v3 = s2.get(200, true);
        assertThat(s2v3.get(key1).asString(), is(ConfigValues.simpleValue("value 3")));
        s2.reset();
        //same v3s
        assertThat(v3, is(s2v3));

        ///////////////////// new subscriber on V1 receives JUST the last event V3
        // subscribe s3 on v1
        ConfigChangeListener s3 = new ConfigChangeListener();
        v1.onChange(s3::onChange);

        ///////////////////// new subscriber on V2 receives also JUST the last event V3
        // subscribe s4 on v2
        ConfigChangeListener s4 = new ConfigChangeListener();
        v2.onChange(s4::onChange);

        ///////////////////////////// another change -> ALL subscribers receives NEW event, no matter what ver. they subscribed on
        // change source => config v4
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue(fullKey, "value 4").build());
        // s1 receives v4
        Config v4 = s1.get(200, true);
        assertThat(v4.get(key1).asString(), is(ConfigValues.simpleValue("value 4")));
        s1.reset();
        // s2 receives v4
        Config s2v4 = s2.get(200, true);
        assertThat(s2v4.get(key1).asString(), is(ConfigValues.simpleValue("value 4")));
        s2.reset();
        // s3 receives v4
        Config s3v4 = s3.get(200, true);
        assertThat(s3v4.get(key1).asString(), is(ConfigValues.simpleValue("value 4")));
        s3.reset();
        // s4 receives v4
        Config s4v4 = s4.get(200, true);
        assertThat(s4v4.get(key1).asString(), is(ConfigValues.simpleValue("value 4")));
        s4.reset();
        //same v4s
        assertThat(v4, is(s2v4));
        assertThat(v4, is(s3v4));
        assertThat(v4, is(s4v4));

        ///////////////////// subscribing on the LAST Config does NOT fire the last event to subscriber
        // subscribe s5 on v4
        ConfigChangeListener s5 = new ConfigChangeListener();
        v4.onChange(s5::onChange);
        // s5 must NOT receive v4
        Config s5event = s5.get(200, false);
        assertThat(s5event, is(nullValue()));

        ///////////////////////////// another change -> ALL subscribers receives NEW event, no matter what ver. they subscribed on
        // change source => config v5
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue(fullKey, "value 5").build());
        // s1 receives v5
        Config v5 = s1.get(200, true);
        assertConfigValue(v5.get(key1).asString(), "value 5");
        // s2 receives v5
        Config s2v5 = s2.get(200, true);
        assertConfigValue(s2v5.get(key1).asString(), "value 5");
        // s3 receives v5
        Config s3v5 = s3.get(200, true);
        assertConfigValue(s3v5.get(key1).asString(), "value 5");
        // s4 receives v5
        Config s4v5 = s4.get(200, true);
        assertConfigValue(s4v5.get(key1).asString(), "value 5");
        // s5 receives v5
        Config s5v5 = s5.get(200, true);
        assertConfigValue(s5v5.get(key1).asString(), "value 5");
        //same v5s
        assertThat(v5, is(s2v5));
        assertThat(v5, is(s3v5));
        assertThat(v5, is(s4v5));
        assertThat(v5, is(s5v5));
    }

    // todo maybe move to a shared place, so we can play around with method singatures
    public static <T> void assertConfigValue(ConfigValue<T> value, T expectedValue) {
        assertThat(value, is(ConfigValues.simpleValue(expectedValue)));
    }
}
