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

package io.helidon.config.spi;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.TestingConfigSourceChangeSubscriber;
import io.helidon.config.TestingPollingStrategy;
import io.helidon.config.internal.PropertiesConfigParser;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigHelperTest.readerToString;
import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link AbstractParsableConfigSource}.
 */
public class AbstractParsableConfigSourceTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_CONFIG = TEST_KEY + " = test-value";
    private static final int TEST_DELAY_MS = 1;

    @Test
    public void testBuilderContentNotExists() {
        TestingParsableConfigSource source = TestingParsableConfigSource.builder().build();

        assertThat(source.isMandatory(), is(true));
        assertThat(source.mediaType(), is(nullValue()));
        assertThat(source.parser(), is(nullValue()));
    }

    @Test
    public void testBuilderContentExists() throws IOException {
        TestingParsableConfigSource source = TestingParsableConfigSource.builder()
                .content(mockContent())
                .build();

        assertThat(source.isMandatory(), is(true));
        assertThat(source.mediaType(), is(nullValue()));
        assertThat(source.parser(), is(nullValue()));
        assertThat(source.content().mediaType(), is(TEST_MEDIA_TYPE));
        assertThat(readerToString(source.content().asReadable()), is(TEST_CONFIG));
    }

    @Test
    public void testFromReadable() throws IOException {
        AbstractParsableConfigSource source = (AbstractParsableConfigSource) ConfigSources
                .create(new StringReader(TEST_CONFIG), TEST_MEDIA_TYPE);

        assertThat(source.isMandatory(), is(true));
        assertThat(source.mediaType(), is(TEST_MEDIA_TYPE));
        assertThat(source.parser(), is(nullValue()));
        assertThat(source.content().mediaType(), is(TEST_MEDIA_TYPE));
        assertThat(readerToString(source.content().asReadable()), is(TEST_CONFIG));
    }

    @Test
    public void testCompleteBuilder() {
        ConfigParser parser = mock(ConfigParser.class);
        TestingParsableConfigSource source = TestingParsableConfigSource.builder()
                .mediaType(TEST_MEDIA_TYPE)
                .parser(parser)
                .optional()
                .build();

        assertThat(source.isMandatory(), is(false));
        assertThat(source.mediaType(), is(TEST_MEDIA_TYPE));
        assertThat(source.parser(), is(parser));
    }

    @Test
    public void testMandatoryParserSetContentExistsParserRegistered() {
        ConfigParser.Content content = mockContent();
        ConfigParser registeredParser = mockParser("registered");
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(content.mediaType())).thenReturn(Optional.of(registeredParser));

        ConfigParser setParser = mockParser("set");

        TestingParsableConfigSource source = TestingParsableConfigSource.builder()
                .content(content)
                .parser(setParser)
                .build();

        source.init(context);
        assertThat(source.load().get().get(TEST_KEY), valueNode("set"));
    }

    @Test
    public void testMandatoryParserSetContentNotExistsParserRegistered() {
        ConfigParser.Content content = mockContent();
        ConfigParser registeredParser = mockParser("registered");
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(content.mediaType())).thenReturn(Optional.of(registeredParser));

        ConfigParser setParser = mockParser("set");

        TestingParsableConfigSource source = TestingParsableConfigSource.builder()
                .parser(setParser)
                .build();

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            source.init(context);
            source.load();
        });
        assertThat(ex.getMessage(), stringContainsInOrder(List.of(
                "Cannot load data from mandatory source",
                "TestingParsableConfig[parsable-test]")));
        assertThat(ex.getCause(),
                   instanceOf(ConfigException.class)); //Cannot find suitable parser for 'my/media/type' media type.
    }

    @Test
    public void testMandatoryParserNotSetContentExistsParserRegistered() {
        ConfigParser.Content content = mockContent();
        ConfigParser registeredParser = mockParser("registered");
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(content.mediaType())).thenReturn(Optional.of(registeredParser));

        TestingParsableConfigSource source = TestingParsableConfigSource.builder()
                .content(content)
                .build();

        source.init(context);
        assertThat(source.load().get().get(TEST_KEY), valueNode("registered"));
    }

    @Test
    public void testMandatoryParserNotSetContentExistsParserNotRegistered() {
        ConfigParser.Content content = mockContent();
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(content.mediaType())).thenReturn(Optional.empty());

        TestingParsableConfigSource source = TestingParsableConfigSource.builder()
                .content(content)
                .build();

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            source.init(context);
            source.load();
        });
        assertThat(ex.getMessage(), stringContainsInOrder(List.of(
                "Cannot load data from mandatory source",
                "TestingParsableConfig[parsable-test]")));
        assertThat(ex.getCause(),
                   instanceOf(ConfigException.class)); //Cannot find suitable parser for 'my/media/type' media type.

    }

    @Test
    public void testOptionalParserNotSetContentExistsParserNotRegistered() {
        ConfigParser.Content content = mockContent();
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(content.mediaType())).thenReturn(Optional.empty());

        TestingParsableConfigSource source = TestingParsableConfigSource.builder()
                .content(content)
                .optional()
                .build();

        source.init(context);
        assertThat(source.load(), is(Optional.empty()));
    }

    @Test
    public void testOptionalParserSetContentNotExistsParserNotRegistered() {
        ConfigParser.Content content = mockContent();
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(content.mediaType())).thenReturn(Optional.empty());

        ConfigParser setParser = mockParser("set");

        TestingParsableConfigSource source = TestingParsableConfigSource.builder()
                .optional()
                .parser(setParser)
                .build();

        source.init(context);
        assertThat(source.load(), is(Optional.empty()));
    }

    @Test
    public void testFormatDescriptionOptionalNoParams() {
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().optional().build();

        assertThat(configSource.formatDescription(""), is("TestingParsableConfig[]?"));
    }

    @Test
    public void testFormatDescriptionOptionalWithParams() {
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().optional().build();

        assertThat(configSource.formatDescription("PA,RAMS"), is("TestingParsableConfig[PA,RAMS]?"));
    }

    @Test
    public void testFormatDescriptionMandatoryNoParams() {
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().build();

        assertThat(configSource.formatDescription(""), is("TestingParsableConfig[]"));
    }

    @Test
    public void testFormatDescriptionMandatoryWithParams() {
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().build();

        assertThat(configSource.formatDescription("PA,RAMS"), is("TestingParsableConfig[PA,RAMS]"));
    }

    @Test
    public void testChangesFromNoContentToStillNoContent() throws InterruptedException {
        AtomicReference<ConfigParser.Content> contentReference = new AtomicReference<>();

        ConfigContext context = mock(ConfigContext.class);
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().content(contentReference::get)
                .optional()
                .parser(ConfigParsers.properties())
                .pollingStrategy(() -> pollingStrategy)
                .build();

        configSource.init(context);
        // load from 'no' content
        assertThat(configSource.load(), is(Optional.empty()));

        // last data is empty
        assertThat(configSource.lastData(), is(Optional.empty()));

        // listen on changes
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // polling ticks event
        pollingStrategy.submitEvent();

        // NO changes event
        assertThat(subscriber.getLastOnNext(200, false), is(nullValue()));
        // objectNode still empty
        assertThat(configSource.lastData(), is(Optional.empty()));
    }

    @Test
    public void testChangesFromNoContentToNewOne() throws InterruptedException {
        AtomicReference<ConfigParser.Content> contentReference = new AtomicReference<>();

        ConfigContext context = mock(ConfigContext.class);
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().content(contentReference::get)
                .optional()
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        configSource.init(context);
        // load 'no' content
        assertThat(configSource.load(), is(Optional.empty()));

        // last data is empty
        assertThat(configSource.lastData(), is(Optional.empty()));

        // listen on changes
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // change content
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        Optional<Instant> contentTimestamp = Optional.of(Instant.now());
        contentReference.set(ConfigParser.Content.create(new StringReader("aaa=bbb"),
                                                         PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                         contentTimestamp));

        // polling ticks event
        pollingStrategy.submitEvent();

        // wait for event
        assertThat(subscriber.getLastOnNext(150, true).get(), is(configSource.lastData().get().data().get()));

        // objectNode
        assertThat(configSource.lastData().get().data().get().get("aaa"), valueNode("bbb"));
        // content timestamp
        assertThat(configSource.lastData().get().stamp(), is(contentTimestamp));
    }

    @Test
    public void testChangesFromContentToSameContent() throws InterruptedException {
        AtomicReference<ConfigParser.Content<Instant>> contentReference = new AtomicReference<>();
        // set content
        Optional<Instant> contentTimestamp = Optional.of(Instant.now());
        contentReference.set(ConfigParser.Content.create(new StringReader("aaa=bbb"),
                                                         PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                         contentTimestamp));

        ConfigContext context = mock(ConfigContext.class);
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().content(contentReference::get)
                .optional()
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        configSource.init(context);
        // load from content
        ObjectNode lastObjectNode = configSource.load().get();
        assertThat(lastObjectNode, is(configSource.lastData().get().data().get()));

        // objectNode
        assertThat(configSource.lastData().get().data().get().get("aaa"), valueNode("bbb"));
        // content timestamp
        assertThat(configSource.lastData().get().stamp(), is(contentTimestamp));

        // listen on changes
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // reset content
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        contentTimestamp = Optional.of(Instant.now());
        contentReference.set(ConfigParser.Content.create(new StringReader("aaa=bbb"),
                                                         PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                         contentTimestamp));

        // polling ticks event
        pollingStrategy.submitEvent();

        // NO changes event
        assertThat(subscriber.getLastOnNext(200, false), is(nullValue()));

        // objectNode still null
        assertThat(configSource.lastData().get().data().get(), is(lastObjectNode));
        // timestamp has not changed
        assertThat(configSource.lastData().get().stamp(), is(contentTimestamp));
    }

    @Test
    public void testChangesFromContentToNoContent() throws InterruptedException {
        AtomicReference<ConfigParser.Content<Instant>> contentReference = new AtomicReference<>();
        // set content
        Optional<Instant> contentTimestamp = Optional.of(Instant.now());
        contentReference.set(ConfigParser.Content.create(new StringReader("aaa=bbb"),
                                                         PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                         contentTimestamp));

        ConfigContext context = mock(ConfigContext.class);
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().content(contentReference::get)
                .optional()
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        configSource.init(context);
        // load from content
        assertThat(configSource.load().get(), is(configSource.lastData().get().data().get()));

        // objectNode
        assertThat(configSource.lastData().get().data().get().get("aaa"), valueNode("bbb"));
        // content timestamp
        assertThat(configSource.lastData().get().stamp(), is(contentTimestamp));

        // listen on changes
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // remove content
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        contentReference.set(null);

        // polling ticks event
        pollingStrategy.submitEvent();

        // wait for event
        assertThat(subscriber.getLastOnNext(5000, true), is(Optional.empty()));

        // last data is empty
        assertThat(configSource.lastData().get().data(), is(Optional.empty()));
    }

    @Test
    public void testChangesFromContentToDifferentOne() throws InterruptedException {
        AtomicReference<ConfigParser.Content> contentReference = new AtomicReference<>();
        // set content
        Optional<Instant> contentTimestamp = Optional.of(Instant.now());
        contentReference.set(ConfigParser.Content.create(new StringReader("aaa=bbb"),
                                                         PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                         contentTimestamp));

        ConfigContext context = mock(ConfigContext.class);
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().content(contentReference::get)
                .optional()
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        configSource.init(context);
        // load from content
        ObjectNode lastObjectNode = configSource.load().get();
        assertThat(lastObjectNode, is(configSource.lastData().get().data().get()));

        // objectNode
        assertThat(configSource.lastData().get().data().get().get("aaa"), valueNode("bbb"));
        // content timestamp
        assertThat(configSource.lastData().get().stamp(), is(contentTimestamp));

        // listen on changes
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);
        subscriber.request1();

        // reset content
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        contentTimestamp = Optional.of(Instant.now());
        contentReference.set(ConfigParser.Content.create(new StringReader("aaa=ccc"),
                                                         PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES,
                                                         contentTimestamp));

        // polling ticks event
        pollingStrategy.submitEvent();

        // wait for event
        assertThat(subscriber.getLastOnNext(150, true).get(), is(configSource.lastData().get().data().get()));

        // objectNode
        assertThat(configSource.lastData().get().data().get().get("aaa"), valueNode("ccc"));
        // content timestamp
        assertThat(configSource.lastData().get().stamp(), is(contentTimestamp));
    }

    @Test
    public void testChangesTransitivePollingSubscription() throws InterruptedException {
        TestingPollingStrategy pollingStrategy = new TestingPollingStrategy();
        AtomicReference<ConfigParser.Content> contentReference = new AtomicReference<>();
        TestingParsableConfigSource configSource = TestingParsableConfigSource.builder().content(contentReference::get)
                .optional()
                .parser(ConfigParsers.properties())
                .pollingStrategy(pollingStrategy)
                .build();

        // config source is not subscribed on polling strategy yet
        assertThat(configSource.isSubscribePollingStrategyInvoked(), is(false));

        // first subscribe
        TestingConfigSourceChangeSubscriber subscriber = new TestingConfigSourceChangeSubscriber();
        configSource.changes().subscribe(subscriber);

        // config source is not subscribed on polling strategy yet
        assertThat(configSource.isSubscribePollingStrategyInvoked(), is(false));

        // first request
        subscriber.getSubscription().request(1);
        assertThat(configSource.isSubscribePollingStrategyInvoked(), is(true));
        assertThat(configSource.isCancelPollingStrategyInvoked(), is(false));

        // cancel subscription
        subscriber.getSubscription().cancel();

        TimeUnit.MILLISECONDS.sleep(150);

        // config source is no longer subscribed on polling strategy
        assertThat(configSource.isCancelPollingStrategyInvoked(), is(true));
    }

    @Test
    public void testBuilderDefault() {
        TestingParsableConfigSource.Builder builder = TestingParsableConfigSource.builder();

        assertThat(builder.pollingStrategy(), is(PollingStrategies.nop()));
        assertThat(builder.changesExecutor(), is(AbstractSource.Builder.DEFAULT_CHANGES_EXECUTOR));
        assertThat(builder.changesMaxBuffer(), is(Flow.defaultBufferSize()));
    }

    @Test
    public void testBuilderCustomChanges() {
        Executor myExecutor = Runnable::run;
        TestingParsableConfigSource.Builder builder = TestingParsableConfigSource.builder()
                .changesExecutor(myExecutor)
                .changesMaxBuffer(1);

        assertThat(builder.changesExecutor(), is(myExecutor));
        assertThat(builder.changesMaxBuffer(), is(1));
    }

    @Test
    public void testInitAll() {
        TestingParsableConfigSource.TestingBuilder builder = TestingParsableConfigSource.builder()
                .config(Config.create(ConfigSources.create(Map.of("media-type", "application/x-yaml"))));

        //media-type
        assertThat(builder.mediaType(), is("application/x-yaml"));
    }

    @Test
    public void testInitNothing() {
        TestingParsableConfigSource.TestingBuilder builder = TestingParsableConfigSource.builder().config((Config.empty()));

        //media-type
        assertThat(builder.mediaType(), is(nullValue()));
    }

    private ConfigParser.Content mockContent() {
        ConfigParser.Content content = mock(ConfigParser.Content.class);
        Readable readable = new StringReader(TEST_CONFIG);
        when(content.asReadable()).thenReturn(readable);
        when(content.mediaType()).thenReturn(TEST_MEDIA_TYPE);
        when(content.stamp()).thenReturn(Optional.of(Instant.EPOCH));
        return content;
    }

    private ConfigParser mockParser(String value) {
        ConfigParser parser = mock(ConfigParser.class);
        when(parser.supportedMediaTypes()).thenReturn(Set.of(TEST_MEDIA_TYPE));
        when(parser.parse(any())).thenReturn(ObjectNode.builder().addValue(TEST_KEY, ValueNode.create(value)).build());

        return parser;
    }

}
