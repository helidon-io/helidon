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

import java.io.StringReader;
import java.net.MalformedURLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.internal.PropertiesConfigParser;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.TestingParsableConfigSource;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests {@link CompositeConfigSource}.
 */
public class CompositeConfigSourceTest {

    private static final int TEST_DELAY_MS = 1;

    @Test
    public void testDescriptionEmpty() throws MalformedURLException {
        ConfigSource configSource = ConfigSources.create()
                .build();

        assertThat(configSource.description(), is(""));
    }

    @Test
    public void testDescription() throws MalformedURLException {
        ConfigSource configSource = ConfigSources.create()
                .add(ConfigSources.classpath("application.conf"))
                .add(ConfigSources.create(ObjectNode.builder().addValue("prop1", "1").build()))
                .add(ConfigSources.create(Map.of()))
                .add(ConfigSources.create(ObjectNode.builder().addValue("prop1", "2").build()))
                .build();

        assertThat(configSource.description(),
                   is("ClasspathConfig[application.conf]->InMemoryConfig[ObjectNode]->MapConfig[map]->InMemoryConfig[ObjectNode"
                              + "]"));
    }

    @Test
    public void testDescriptionWithEnvVarsAndSysProps() throws MalformedURLException {
        ConfigSource configSource = ConfigSources.create()
                .add(ConfigSources.classpath("application.conf"))
                .add(ConfigSources.create(ObjectNode.builder().addValue("prop1", "1").build()))
                .build();

        assertThat(configSource.description(),
                   is("ClasspathConfig[application.conf]->InMemoryConfig[ObjectNode]"));
    }

    @Test
    public void testBuildEmptyCompositeBuilder() {
        Optional<ObjectNode> rootNode = ConfigSources.create()
                .build()
                .load();

        assertThat(rootNode, is(Optional.empty()));
    }

    @Test
    public void testBuildWithDefaultStrategy() {
        ObjectNode rootNode = initBuilder()
                .build()
                .load()
                .get();

        assertThat(rootNode.get("prop1"), valueNode("source-1"));
        assertThat(rootNode.get("prop2"), valueNode("source-2"));
        assertThat(rootNode.get("prop3"), valueNode("source-3"));
    }

    @Test
    public void testBuildWithCustomStrategy() {
        ObjectNode rootNode = initBuilder()
                .mergingStrategy(new UseTheLastObjectNodeMergingStrategy())
                .build()
                .load()
                .get();

        assertThat(rootNode.get("prop1"), valueNode("source-3"));
        assertThat(rootNode.get("prop2"), is(nullValue()));
        assertThat(rootNode.get("prop3"), valueNode("source-3"));
    }

    @Test
    public void testChangesNoChange() throws InterruptedException {
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        ConfigContext context = mock(ConfigContext.class);

        //config source AAA
        AtomicReference<ConfigParser.Content<Instant>> contentReferenceAAA = new AtomicReference<>();
        contentReferenceAAA.set(ConfigParser.Content.create(new StringReader("ooo=1\nrrr=5"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceAAA = TestingParsableConfigSource.builder()
                .content(contentReferenceAAA::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();
        //config source BBB
        AtomicReference<ConfigParser.Content<Instant>> contentReferenceBBB = new AtomicReference<>();
        contentReferenceBBB.set(ConfigParser.Content.create(new StringReader("ooo=2\nppp=9"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceBBB = TestingParsableConfigSource.builder()
                .content(contentReferenceBBB::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        CompositeConfigSource configSource = (CompositeConfigSource) ConfigSources.create()
                .add(configSourceAAA)
                .add(configSourceBBB)
                .build();

        configSource.init(context);
        // load from content
        ObjectNode lastObjectNode = configSource.load().get();
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));
        assertThat(lastObjectNode.get("ooo"), valueNode("1"));
        assertThat(lastObjectNode.get("rrr"), valueNode("5"));
        assertThat(lastObjectNode.get("ppp"), valueNode("9"));

        // first subscribe
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // polling ticks event
        pollingStrategy.submitEvent();

        // NO changes event
        assertThat(subscriber.getLastOnNext(200, false), is(nullValue()));
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));
    }

    @Test
    public void testChangesPrimaryLayerValueHasChanged() throws InterruptedException {
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        ConfigContext context = mock(ConfigContext.class);

        //config source AAA
        AtomicReference<ConfigParser.Content> contentReferenceAAA = new AtomicReference<>();
        contentReferenceAAA.set(ConfigParser.Content.create(new StringReader("ooo=1\nrrr=5"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceAAA = TestingParsableConfigSource.builder()
                .content(contentReferenceAAA::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();
        //config source BBB
        AtomicReference<ConfigParser.Content> contentReferenceBBB = new AtomicReference<>();
        contentReferenceBBB.set(ConfigParser.Content.create(new StringReader("ooo=2\nppp=9"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceBBB = TestingParsableConfigSource.builder()
                .content(contentReferenceBBB::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        CompositeConfigSource configSource = (CompositeConfigSource) ConfigSources.create()
                .add(configSourceAAA)
                .add(configSourceBBB)
                .build();

        configSource.init(context);
        // load from content
        ObjectNode lastObjectNode = configSource.load().get();
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));
        assertThat(lastObjectNode.get("ooo"), valueNode("1"));
        assertThat(lastObjectNode.get("rrr"), valueNode("5"));
        assertThat(lastObjectNode.get("ppp"), valueNode("9"));

        // first subscribe
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // wait for both subscribers
        ConfigTest.waitFor(() -> pollingStrategy.ticks().getNumberOfSubscribers() == 2, 1_000, 10);

        // change content AAA
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        contentReferenceAAA.set(ConfigParser.Content.create(new StringReader("ooo=11\nrrr=5"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));

        // NO ticks event -> NO change yet
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));

        // polling ticks event
        pollingStrategy.submitEvent();

        // wait for event
        ObjectNode newObjectNode = subscriber.getLastOnNext(500, true).get();
        assertThat(newObjectNode, is(configSource.lastObjectNode().get()));
        assertThat(newObjectNode.get("ooo"), valueNode("11"));
        assertThat(newObjectNode.get("rrr"), valueNode("5"));
        assertThat(newObjectNode.get("ppp"), valueNode("9"));
        // last object-node has changed
        assertThat(lastObjectNode, not(configSource.lastObjectNode().get()));
    }

    @Test
    public void testChangesOverriddenValueHasChanged() throws InterruptedException {
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        ConfigContext context = mock(ConfigContext.class);

        //config source AAA
        AtomicReference<ConfigParser.Content<Instant>> contentReferenceAAA = new AtomicReference<>();
        contentReferenceAAA.set(ConfigParser.Content.create(new StringReader("ooo=1\nrrr=5"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceAAA = TestingParsableConfigSource.builder()
                .content(contentReferenceAAA::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();
        //config source BBB
        AtomicReference<ConfigParser.Content<Instant>> contentReferenceBBB = new AtomicReference<>();
        contentReferenceBBB.set(ConfigParser.Content.create(new StringReader("ooo=2\nppp=9"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceBBB = TestingParsableConfigSource.builder()
                .content(contentReferenceBBB::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        CompositeConfigSource configSource = (CompositeConfigSource) ConfigSources.create()
                .add(configSourceAAA)
                .add(configSourceBBB)
                .build();

        configSource.init(context);
        // load from content
        ObjectNode lastObjectNode = configSource.load().get();
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));
        assertThat(lastObjectNode.get("ooo"), valueNode("1"));
        assertThat(lastObjectNode.get("rrr"), valueNode("5"));
        assertThat(lastObjectNode.get("ppp"), valueNode("9"));

        // first subscribe
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // wait for both subscribers
        ConfigTest.waitFor(() -> pollingStrategy.ticks().getNumberOfSubscribers() == 2, 1_000, 10);

        // change content BBB
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS);
        contentReferenceBBB.set(ConfigParser.Content.create(new StringReader("ooo=22\nppp=9"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));

        // NO ticks event -> NO change yet
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));

        // polling ticks event
        pollingStrategy.submitEvent();

        // NO changes event
        assertThat(subscriber.getLastOnNext(200, false), is(nullValue()));
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));
    }

    @Test
    public void testChangesSecondaryLayerValueHasChanged() throws InterruptedException {
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        ConfigContext context = mock(ConfigContext.class);

        //config source AAA
        AtomicReference<ConfigParser.Content> contentReferenceAAA = new AtomicReference<>();
        contentReferenceAAA.set(ConfigParser.Content.create(new StringReader("ooo=1\nrrr=5"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceAAA = TestingParsableConfigSource.builder()
                .content(contentReferenceAAA::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();
        //config source BBB
        AtomicReference<ConfigParser.Content> contentReferenceBBB = new AtomicReference<>();
        contentReferenceBBB.set(ConfigParser.Content.create(new StringReader("ooo=2\nppp=9"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceBBB = TestingParsableConfigSource.builder()
                .content(contentReferenceBBB::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        CompositeConfigSource configSource = (CompositeConfigSource) ConfigSources.create()
                .add(configSourceAAA)
                .add(configSourceBBB)
                .build();

        configSource.init(context);
        // load from content
        ObjectNode lastObjectNode = configSource.load().get();
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));
        assertThat(lastObjectNode.get("ooo"), valueNode("1"));
        assertThat(lastObjectNode.get("rrr"), valueNode("5"));
        assertThat(lastObjectNode.get("ppp"), valueNode("9"));

        // first subscribe
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // wait for both subscribers
        ConfigTest.waitFor(() -> pollingStrategy.ticks().getNumberOfSubscribers() == 2, 1_000, 10);

        // change content BBB
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS);
        contentReferenceBBB.set(ConfigParser.Content.create(new StringReader("ooo=2\nppp=99"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));

        // NO ticks event -> NO change yet
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));

        // polling ticks event
        pollingStrategy.submitEvent();

        // wait for event
        ObjectNode newObjectNode = subscriber.getLastOnNext(333, true).get();
        assertThat(newObjectNode, is(configSource.lastObjectNode().get()));
        assertThat(newObjectNode.get("ooo"), valueNode("1"));
        assertThat(newObjectNode.get("rrr"), valueNode("5"));
        assertThat(newObjectNode.get("ppp"), valueNode("99"));
        // last object-node has changed
        assertThat(lastObjectNode, not(configSource.lastObjectNode().get()));
    }

    @Test
    public void testChangesBothLayerHasChangedAtOnce() throws InterruptedException {
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        ConfigContext context = mock(ConfigContext.class);

        //config source AAA
        AtomicReference<ConfigParser.Content> contentReferenceAAA = new AtomicReference<>();
        contentReferenceAAA.set(ConfigParser.Content.create(new StringReader("ooo=1\nrrr=5"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceAAA = TestingParsableConfigSource.builder()
                .content(contentReferenceAAA::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();
        //config source BBB
        AtomicReference<ConfigParser.Content> contentReferenceBBB = new AtomicReference<>();
        contentReferenceBBB.set(ConfigParser.Content.create(new StringReader("ooo=2\nppp=9"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        TestingParsableConfigSource configSourceBBB = TestingParsableConfigSource.builder()
                .content(contentReferenceBBB::get)
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        CompositeConfigSource configSource = (CompositeConfigSource) ConfigSources.create()
                .add(configSourceAAA)
                .add(configSourceBBB)
                .build();

        configSource.init(context);
        // load from content
        ObjectNode lastObjectNode = configSource.load().get();
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));
        assertThat(lastObjectNode.get("ooo"), valueNode("1"));
        assertThat(lastObjectNode.get("rrr"), valueNode("5"));
        assertThat(lastObjectNode.get("ppp"), valueNode("9"));

        // first subscribe
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // wait for both subscribers
        ConfigTest.waitFor(() -> pollingStrategy.ticks().getNumberOfSubscribers() == 2, 1_000, 10);

        // change content AAA
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        contentReferenceAAA.set(ConfigParser.Content.create(new StringReader("ooo=11\nrrr=5"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));
        // change content BBB
        contentReferenceBBB.set(ConfigParser.Content.create(new StringReader("ooo=2\nppp=99"),
                                                            PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                            Optional.of(Instant.now())));

        // NO ticks event -> NO change yet
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));

        // polling ticks event
        pollingStrategy.submitEvent();

        // wait for event
        ObjectNode newObjectNode = subscriber.getLastOnNext(333, true).get();
        assertThat(newObjectNode, is(configSource.lastObjectNode().get()));
        assertThat(newObjectNode.get("ooo"), valueNode("11"));
        assertThat(newObjectNode.get("rrr"), valueNode("5"));
        assertThat(newObjectNode.get("ppp"), valueNode("99"));
        // last object-node has changed
        assertThat(lastObjectNode, not(configSource.lastObjectNode().get()));

        // no other change
        subscriber.request1();
        assertThat(subscriber.getLastOnNext(333, false), is(nullValue()));
    }

    @Test
    public void testChangesRepeatSubscription() throws InterruptedException {
        ConfigContext context = mock(ConfigContext.class);

        CompositeConfigSource configSource = (CompositeConfigSource) ConfigSources.create(
                ConfigSources.environmentVariables(),
                ConfigSources.systemProperties(),
                TestingParsableConfigSource.builder()
                        .content(ConfigParser.Content
                                         .create(new StringReader("ooo=1\nrrr=5"),
                                                 PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                 Optional.of(Instant.now())))
                        .parser(ConfigParsers.properties())
                        .build())
                .build();

        configSource.init(context);
        // load from content
        ObjectNode lastObjectNode = configSource.load().get();
        assertThat(lastObjectNode, is(configSource.lastObjectNode().get()));

        // NO transitive subscribers
        assertThat(configSource.compositeConfigSourcesSubscribers(), is(nullValue()));

        // subscribers
        TestingConfigSourceChangeSubscriber subscriber1 = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber1);
        subscriber1.request1();

        TestingConfigSourceChangeSubscriber subscriber2 = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber2);
        subscriber2.request1();

        TestingConfigSourceChangeSubscriber subscriber3 = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber3);
        subscriber3.request1();

        TestingConfigSourceChangeSubscriber subscriber4 = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber4);
        subscriber4.request1();

        // subscribers
        assertThat(configSource.compositeConfigSourcesSubscribers(), hasSize(3)); // env-vars + sys-props + test

        // cancel subscription
        subscriber1.getSubscription().cancel();
        subscriber2.getSubscription().cancel();
        subscriber3.getSubscription().cancel();
        subscriber4.getSubscription().cancel();

        // NO transitive subscribers again
        assertThat(configSource.compositeConfigSourcesSubscribers(), is(nullValue()));
    }

    @Test
    public void testBuilderDefault() {
        ConfigSources.CompositeBuilder builder = ConfigSources.create();
        ConfigSources.CompositeBuilder spyBuilder = spy(builder);
        spyBuilder.build();

        verify(spyBuilder).createCompositeConfigSource(
                argThat(sources -> sources.size() == 0), //ConfigSources
                argThat(strategy -> strategy instanceof FallbackMergingStrategy), //MergingStrategy
                eq(CompositeConfigSource.DEFAULT_CHANGES_EXECUTOR_SERVICE), //reloadExecutorService
                eq(Duration.ofMillis(100)), //changes debounceTimeout
                eq(Flow.defaultBufferSize()) //changesMaxBuffer
        );
    }

    @Test
    public void testBuilderCustomChanges() {
        ScheduledExecutorService myExecutor = mock(ScheduledExecutorService.class);
        ConfigSources.CompositeBuilder builder = ConfigSources.create()
                .changesExecutor(myExecutor)
                .changesDebounce(Duration.ZERO)
                .changesMaxBuffer(1);
        ConfigSources.CompositeBuilder spyBuilder = spy(builder);
        spyBuilder.build();

        verify(spyBuilder).createCompositeConfigSource(
                argThat(sources -> sources.size() == 0), //ConfigSources
                argThat(strategy -> strategy instanceof FallbackMergingStrategy), //MergingStrategy
                eq(myExecutor), //reloadExecutorService
                eq(Duration.ZERO), //changes debounceTimeout
                eq(1) //changesMaxBuffer
        );
    }

    @Test
    public void testBuilderAddSources() {
        ObjectNode rootNode = initBuilder()
                .add(ConfigSources.create(ObjectNode.builder()
                                                .addValue("prop1", "source-4")
                                                .addValue("prop4", "source-4")
                                                .build()))
                .add(ConfigSources.create(ObjectNode.builder()
                                                .addValue("prop1", "source-5")
                                                .addValue("prop5", "source-5")
                                                .build()))
                .build()
                .load()
                .get();

        assertThat(rootNode.get("prop1"), valueNode("source-1"));
        assertThat(rootNode.get("prop2"), valueNode("source-2"));
        assertThat(rootNode.get("prop3"), valueNode("source-3"));
        assertThat(rootNode.get("prop4"), valueNode("source-4"));
        assertThat(rootNode.get("prop5"), valueNode("source-5"));
    }

    //
    // helpers
    //

    public static ConfigSources.CompositeBuilder initBuilder() {
        return ConfigSources.create(
                ConfigSources.create(ObjectNode.builder()
                                           .addValue("prop1", "source-1")
                                           .build()),
                ConfigSources.create(ObjectNode.builder()
                                           .addValue("prop1", "source-2")
                                           .addValue("prop2", "source-2")
                                           .build()),
                ConfigSources.create(ObjectNode.builder()
                                           .addValue("prop1", "source-3")
                                           .addValue("prop3", "source-3")
                                           .build()));
    }

    private static class UseTheLastObjectNodeMergingStrategy implements ConfigSources.MergingStrategy {
        @Override
        public ObjectNode merge(List<ObjectNode> rootNodes) {
            return rootNodes.get(rootNodes.size() - 1);
        }
    }

}
