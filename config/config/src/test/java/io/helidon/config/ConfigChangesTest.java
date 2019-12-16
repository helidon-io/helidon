/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.TestingConfigSource;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigTest.waitFor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests related to {@link Config#changes()} and/or {@link Config#onChange(Function)}.
 */
public class ConfigChangesTest {

    private static final int TEST_DELAY_MS = 1;

    @Test
    public void testChangesFromMissingToObjectNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // key does not exist
        assertThat(config.get("key1").exists(), is(false));

        // register subscriber
        TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
        config.get("key1").changes().subscribe(subscriber);
        subscriber.request1();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addObject("key1", ObjectNode.builder()
                                .addValue("sub1", "string value")
                                .build())
                        .build());

        // wait for event
        Config newConfig = subscriber.getLastOnNext(1000, true);

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
        TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
        config.get("key-1-1.key-2-1").changes().subscribe(subscriber);
        subscriber.request1();

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
        assertThat(subscriber.getLastOnNext(1000, false), is(nullValue()));
    }

    @Test
    public void testChangesSubscribeOnLeafNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "item 1").build()).build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // register subscriber1 on original leaf
        TestingConfigChangeSubscriber subscriber1 = new TestingConfigChangeSubscriber();
        config.get("key-1-1.key-2-1")
                .changes().subscribe(subscriber1);
        subscriber1.request1();
        // register subscriber2 on leaf of DETACHED parent
        TestingConfigChangeSubscriber subscriber2 = new TestingConfigChangeSubscriber();
        config.get("key-1-1")
                .detach()
                .get("key-2-1")
                .changes().subscribe(subscriber2);
        subscriber2.request1();
        // register subscriber3 on DETACHED leaf
        TestingConfigChangeSubscriber subscriber3 = new TestingConfigChangeSubscriber();
        config.get("key-1-1.key-2-1")
                .detach()
                .changes().subscribe(subscriber3);
        subscriber3.request1();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(ObjectNode.simple("key-1-1.key-2-1", "NEW item 1"));

        // wait for event1
        Config last1 = subscriber1.getLastOnNext(200, true);
        assertThat(last1.key().toString(), is("key-1-1.key-2-1"));
        assertThat(last1.asString().get(), is("NEW item 1"));

        // wait for event2
        Config last2 = subscriber2.getLastOnNext(200, true);
        assertThat(last2.key().toString(), is("key-2-1"));
        assertThat(last2.asString().get(), is("NEW item 1"));

        // wait for event3
        Config last3 = subscriber3.getLastOnNext(200, true);
        assertThat(last3.key().toString(), is(""));
        assertThat(last3.asString().get(), is("NEW item 1"));

        // timestamp 1==2==3

        // no other events
        subscriber1.request1();
        subscriber2.request1();
        subscriber3.request1();
        assertThat(subscriber1.getLastOnNext(500, false), is(nullValue()));
        assertThat(subscriber2.getLastOnNext(500, false), is(nullValue()));
        assertThat(subscriber3.getLastOnNext(500, false), is(nullValue()));
    }

    @Test
    public void testChangesSubscribeOnParentNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "item 1").build()).build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // register subscriber1
        TestingConfigChangeSubscriber subscriber1 = new TestingConfigChangeSubscriber();
        config.get("key-1-1")
                .changes().subscribe(subscriber1);
        subscriber1.request1();
        // register subscriber2 on DETACHED leaf
        TestingConfigChangeSubscriber subscriber2 = new TestingConfigChangeSubscriber();
        config.get("key-1-1")
                .detach()
                .changes().subscribe(subscriber2);
        subscriber2.request1();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "NEW item 1").build());

        // wait for event
        assertThat(subscriber1.getLastOnNext(200, true).key().toString(), is("key-1-1"));

        // wait for event1
        Config last1 = subscriber1.getLastOnNext(200, true);
        assertThat(last1.key().toString(), is("key-1-1"));
        assertThat(last1.get("key-2-1").asString().get(), is("NEW item 1"));

        // wait for event2
        Config last2 = subscriber2.getLastOnNext(200, true);
        assertThat(last2.key().toString(), is(""));
        assertThat(last2.get("key-2-1").asString().get(), is("NEW item 1"));

        // no other events
        subscriber1.request1();
        subscriber2.request1();
        assertThat(subscriber1.getLastOnNext(500, false), is(nullValue()));
        assertThat(subscriber2.getLastOnNext(500, false), is(nullValue()));
    }

    @Test
    public void testChangesSubscribeOnRootNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "item 1").build()).build();

        // config
        Config config = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        // register subscriber1
        TestingConfigChangeSubscriber subscriber1 = new TestingConfigChangeSubscriber();
        config.changes().subscribe(subscriber1);
        subscriber1.request1();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue("key-1-1.key-2-1", "NEW item 1").build());

        // wait for event
        Config last1 = subscriber1.getLastOnNext(200, true);
        assertThat(last1.key().toString(), is(""));
        assertThat(last1.get("key-1-1.key-2-1").asString().get(), is("NEW item 1"));

        // no other events
        subscriber1.request1();
        assertThat(subscriber1.getLastOnNext(500, false), is(nullValue()));
    }

    @Test
    public void testChangesJustSingleSubscriptionOnConfigSource() throws InterruptedException {
        //config source
        TestingConfigSource configSource = TestingConfigSource.builder().build();

        AbstractConfigImpl config = (AbstractConfigImpl) Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .sources(configSource)
                .build();

        //Config not yet subscribed on config source
        assertThat(configSource.isSubscribePollingStrategyInvoked(), is(false));
        assertThat(configSource.isCancelPollingStrategyInvoked(), is(false));

        List<TestingConfigChangeSubscriber> subscribers = new LinkedList<>();
        List.of("", "key1", "sub.key1", "", "key1").forEach(key -> {
            TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
            config.get(key).changes().subscribe(subscriber);
            subscribers.add(subscriber);
            try {
                subscriber.request1();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        //config factory contains 5 subscribers
        assertThat(config.factory().provider().changesSubmitter().getNumberOfSubscribers(), is(5));

        //Config already subscribed on config source
        waitFor(configSource::isSubscribePollingStrategyInvoked, 500, 10);
        assertThat(configSource.isCancelPollingStrategyInvoked(), is(false));

        //config source just 1
        assertThat(configSource.changesSubmitter().getNumberOfSubscribers(), is(1));

        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(null);

        //un-subscribe all
        subscribers.forEach(subscriber -> subscriber.getSubscription().cancel());

        //config factory does not have subscribers
        waitFor(() -> !config.factory().provider().changesSubmitter().hasSubscribers(), 1_000, 10);

        //Config already canceled from config source changes
        assertThat(configSource.isSubscribePollingStrategyInvoked(), is(true));
        assertThat(configSource.isCancelPollingStrategyInvoked(), is(true));

        //config source does not have subscribers
        waitFor(() -> !configSource.changesSubmitter().hasSubscribers(), 1_000, 10);
    }

    @Test
    public void testChangesFromMissingToListNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().build();

        // config
        Config config = Config.builder().sources(configSource).build();

        // key does not exist
        assertThat(config.get("key1").exists(), is(false));

        // register subscriber
        TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
        config.get("key1").changes().subscribe(subscriber);
        subscriber.request1();

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
        Config newConfig = subscriber.getLastOnNext(1000, true);

        // new: key exists
        assertThat(newConfig.exists(), is(true));
        assertThat(newConfig.type(), is(Config.Type.LIST));
        assertThat(newConfig.asList(String.class).get(), contains("item 1", "item 2"));
    }

    @Test
    public void testChangesFromMissingToValueNode() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().build();

        // config
        Config config = Config.builder().sources(configSource).build();

        // key does not exist
        assertThat(config.get("key1").exists(), is(false));

        // register subscriber
        TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
        config.get("key1").changes().subscribe(subscriber);
        subscriber.request1();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder()
                        .addValue("key1", "string value")
                        .build());

        // wait for event
        Config newConfig = subscriber.getLastOnNext(1000, true);

        // new: key exists
        assertThat(newConfig.exists(), is(true));
        assertThat(newConfig.type(), is(Config.Type.VALUE));
        assertThat(newConfig.asString().get(), is("string value"));
    }

    @Test
    public void testChangesFromObjectNodeToMissing() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder()
                        .addObject("key1", ObjectNode.builder()
                                .addValue("sub1", "string value")
                                .build())
                        .build()).build();

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
        TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
        config.get("key1").changes().subscribe(subscriber);
        subscriber.request1();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(null);

        // wait for event
        Config newConfig = subscriber.getLastOnNext(1000, true);

        // new: key does not exist
        assertThat("New config should not exist", newConfig.exists(), is(false));
    }

    @Test
    public void testChangesFromListNodeToMissing() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder()
                        .addList("key1", ConfigNode.ListNode.builder()
                                .addValue("item 1")
                                .addValue("item 2")
                                .build())
                        .build()).build();

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
        TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
        config.get("key1").changes().subscribe(subscriber);
        subscriber.request1();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(null);

        // wait for event
        Config newConfig = subscriber.getLastOnNext(1000, true);

        // new: key does not exist
        assertThat(newConfig.exists(), is(false));
    }

    @Test
    public void testChangesFromValueNodeToMissing() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder()
                        .addValue("key1", "string value")
                        .build()).build();

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
        TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
        config.get("key1").changes().subscribe(subscriber);
        subscriber.request1();

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(null);

        // wait for event
        Config newConfig = subscriber.getLastOnNext(1000, true);

        // new: key does not exist
        assertThat(newConfig.exists(), is(false));
    }

    @Test
    public void testOnChangeValueChanged() throws InterruptedException {
        // config source
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder().addValue("key1", "string value").build()).build();

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
        Function<Config, Boolean> onNextFunction = mock(Function.class);
        CountDownLatch onNextLatch = new CountDownLatch(1);
        when(onNextFunction.apply(any())).then(invocationOnMock -> {
            onNextLatch.countDown();
            return true;
        });

        // register subscriber
        config.get("key1").onChange(onNextFunction);
        // wait for init
        TimeUnit.MILLISECONDS.sleep(20);

        // change config source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue("key1", "string value 2").build());

        // wait for event
        onNextLatch.await(5, TimeUnit.SECONDS);

        // verify event
        verify(onNextFunction, times(1)).apply(argThat(newConfig -> {
            // new: key does exist
            assertThat(newConfig.exists(), is(true));
            assertThat(newConfig.type(), is(Config.Type.VALUE));
            assertThat(newConfig.asString().get(), is("string value 2"));

            return true;
        }));
    }

    @Test
    public void testChangesSendLastLoadedConfigToNewSubscribers() throws InterruptedException {
        String key1 = "key1";
        String fullKey = "parent." + key1;
        /////////////////////// subscribe before any change
        // create new config v1
        TestingConfigSource configSource = TestingConfigSource.builder().objectNode(
                ObjectNode.builder().addValue(fullKey, "value").build()).build();
        Config v1 = Config.builder()
                .sources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build()
                .get("parent");

        assertThat(v1.get(key1).asString().get(), is("value"));
        // subscribe s1 on v1
        TestingConfigChangeSubscriber s1 = new TestingConfigChangeSubscriber();
        v1.changes().subscribe(s1);
        s1.request1();

        ///////////////////////////// FIRST change -> subscriber receives event
        // change source => config v2
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue(fullKey, "value 2").build());
        // s1 receives v2
        Config v2 = s1.getLastOnNext(200, true);
        assertThat(v2.get(key1).asString().get(), is("value 2"));
        s1.request1();

        ///////////////////// subscribing on old Config -> subscriber receives (OLD) already fired event
        // subscribe s2 on v1
        TestingConfigChangeSubscriber s2 = new TestingConfigChangeSubscriber();
        v1.changes().subscribe(s2);
        s2.request1();
        // s2 receives v2
        Config s2v2 = s2.getLastOnNext(1200, true);
        assertThat(s2v2.get(key1).asString(), is(ConfigValues.simpleValue("value 2")));
        //same v2s
        assertThat(v2, is(s2v2));
        s2.request1();

        ///////////////////////////// another change -> BOTH subscribers receives NEW event
        // change source => config v3
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue(fullKey, "value 3").build());
        // s1 receives v3
        Config v3 = s1.getLastOnNext(200, true);
        assertThat(v3.get(key1).asString(), is(ConfigValues.simpleValue("value 3")));
        s1.request1();
        // s2 receives v3
        Config s2v3 = s2.getLastOnNext(200, true);
        assertThat(s2v3.get(key1).asString(), is(ConfigValues.simpleValue("value 3")));
        s2.request1();
        //same v3s
        assertThat(v3, is(s2v3));

        ///////////////////// new subscriber on V1 receives JUST the last event V3
        // subscribe s3 on v1
        TestingConfigChangeSubscriber s3 = new TestingConfigChangeSubscriber();
        v1.changes().subscribe(s3);
        s3.request1();
        // s3 receives v3
        Config s3v3 = s3.getLastOnNext(200, true);
        assertThat(s3v3.get(key1).asString(), is(ConfigValues.simpleValue("value 3")));
        s3.request1();
        //same v3s
        assertThat(v3, is(s2v3));
        assertThat(v3, is(s3v3));

        ///////////////////// new subscriber on V2 receives also JUST the last event V3
        // subscribe s4 on v2
        TestingConfigChangeSubscriber s4 = new TestingConfigChangeSubscriber();
        v2.changes().subscribe(s4);
        s4.request1();
        // s4 receives v3
        Config s4v3 = s4.getLastOnNext(200, true);
        assertThat(s4v3.get(key1).asString(), is(ConfigValues.simpleValue("value 3")));
        s4.request1();
        //same v3s
        assertThat(v3, is(s2v3));
        assertThat(v3, is(s3v3));
        assertThat(v3, is(s4v3));

        ///////////////////////////// another change -> ALL subscribers receives NEW event, no matter what ver. they subscribed on
        // change source => config v4
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue(fullKey, "value 4").build());
        // s1 receives v4
        Config v4 = s1.getLastOnNext(200, true);
        assertThat(v4.get(key1).asString(), is(ConfigValues.simpleValue("value 4")));
        s1.request1();
        // s2 receives v4
        Config s2v4 = s2.getLastOnNext(200, true);
        assertThat(s2v4.get(key1).asString(), is(ConfigValues.simpleValue("value 4")));
        s2.request1();
        // s3 receives v4
        Config s3v4 = s3.getLastOnNext(200, true);
        assertThat(s3v4.get(key1).asString(), is(ConfigValues.simpleValue("value 4")));
        s3.request1();
        // s4 receives v4
        Config s4v4 = s4.getLastOnNext(200, true);
        assertThat(s4v4.get(key1).asString(), is(ConfigValues.simpleValue("value 4")));
        s4.request1();
        //same v4s
        assertThat(v4, is(s2v4));
        assertThat(v4, is(s3v4));
        assertThat(v4, is(s4v4));

        ///////////////////// subscribing on the LAST Config does NOT fire the last event to subscriber
        // subscribe s5 on v4
        TestingConfigChangeSubscriber s5 = new TestingConfigChangeSubscriber();
        v4.changes().subscribe(s5);
        s5.request1();
        // s5 must NOT receive v4
        Config s5event = s5.getLastOnNext(200, false);
        assertThat(s5event, is(nullValue()));

        ///////////////////////////// another change -> ALL subscribers receives NEW event, no matter what ver. they subscribed on
        // change source => config v5
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        configSource.changeLoadedObjectNode(
                ObjectNode.builder().addValue(fullKey, "value 5").build());
        // s1 receives v5
        Config v5 = s1.getLastOnNext(200, true);
        assertConfigValue(v5.get(key1).asString(), "value 5");
        // s2 receives v5
        Config s2v5 = s2.getLastOnNext(200, true);
        assertConfigValue(s2v5.get(key1).asString(), "value 5");
        // s3 receives v5
        Config s3v5 = s3.getLastOnNext(200, true);
        assertConfigValue(s3v5.get(key1).asString(), "value 5");
        // s4 receives v5
        Config s4v5 = s4.getLastOnNext(200, true);
        assertConfigValue(s4v5.get(key1).asString(), "value 5");
        // s5 receives v5
        Config s5v5 = s5.getLastOnNext(200, true);
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
