/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.docs.Main;
import io.helidon.config.metadata.model.CmModel;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.messaging.spi.ConnectorConfig;
import io.helidon.messaging.spi.ConnectorDirection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MessagingGeneratedArtifactsTest {
    private static final Path GENERATED_SOURCES = Path.of("target/generated-sources/annotations/io/helidon/messaging");
    private static final Path CONFIG_METADATA = Path.of("target/classes/META-INF/helidon/config-metadata.json");

    @Test
    void builderDecoratorsRemainImplementationDetails() throws IOException {
        assertThat(FailurePolicyBlueprint.class.getDeclaredClasses().length, is(0));
        assertThat(MessagingExecutionConfigBlueprint.class.getDeclaredClasses().length, is(0));
        assertThat(Modifier.isPublic(FailurePolicyBuilderDecorator.class.getModifiers()), is(false));
        assertThat(Modifier.isPublic(MessagingExecutionConfigBuilderDecorator.class.getModifiers()), is(false));
        assertThat(hasPublicBuilderDecorator(FailurePolicy.class), is(false));
        assertThat(hasPublicBuilderDecorator(MessagingExecutionConfig.class), is(false));

        String failurePolicySource = Files.readString(GENERATED_SOURCES.resolve("FailurePolicy.java"));
        assertThat(failurePolicySource,
                   containsString("new FailurePolicyBuilderDecorator().decorate(this)"));
        assertThat(failurePolicySource.contains("FailurePolicyBlueprint.BuilderDecorator"), is(false));

        String executionConfigSource = Files.readString(GENERATED_SOURCES.resolve("MessagingExecutionConfig.java"));
        assertThat(executionConfigSource,
                   containsString("new MessagingExecutionConfigBuilderDecorator().decorate(this)"));
        assertThat(executionConfigSource.contains("MessagingExecutionConfigBlueprint.BuilderDecorator"), is(false));
    }

    @Test
    void metadataDocumentsMessagingConfiguration() throws IOException {
        CmModel model;
        try (var input = Files.newInputStream(CONFIG_METADATA)) {
            model = CmModel.fromJson(input);
        }
        assertThat(model.modules().stream()
                           .flatMap(module -> module.types().stream())
                           .noneMatch(type -> type.typeName().equals(MessageBatchConfig.class.getName())),
                   is(true));

        CmType connector = type(model, ConnectorConfig.class);
        assertThat(connector.standalone(), is(false));
        assertThat(connector.prefix(), is(Optional.empty()));
        Map<String, CmOption> connectorOptions = options(connector);
        assertThat(connectorOptions.keySet(), is(Set.of("channel-name", "connector", "direction")));
        assertOption(connectorOptions, "channel-name", "java.lang.String", null, true);
        assertOption(connectorOptions, "connector", "java.lang.String", null, true);
        assertOption(connectorOptions, "direction", ConnectorDirection.class.getName(), null, true);
        assertThat(allowedValues(connectorOptions.get("direction")), is(Set.of("INCOMING", "OUTGOING")));
        assertThat(connectorOptions.get("channel-name").description().orElseThrow(),
                   containsString("Runtime-supplied"));

        CmType failure = type(model, FailurePolicy.class);
        assertThat(failure.standalone(), is(false));
        assertThat(failure.prefix(), is(Optional.empty()));
        Map<String, CmOption> failureOptions = options(failure);
        assertThat(failureOptions.keySet(),
                   is(Set.of("dead-letter.channel", "on-exhausted", "retry.delay", "retry.max-attempts")));
        assertOption(failureOptions, "dead-letter.channel", "java.lang.String", null, false);
        assertOption(failureOptions, "on-exhausted", FailureDisposition.class.getName(), "FAIL", false);
        assertOption(failureOptions, "retry.delay", "java.time.Duration", "PT1S", false);
        assertOption(failureOptions, "retry.max-attempts", "java.lang.Integer", "0", false);
        assertThat(allowedValues(failureOptions.get("on-exhausted")),
                   is(Set.of("FAIL", "DROP", "DEAD_LETTER")));
        assertThat(failureOptions.get("retry.delay").description().orElseThrow(), containsString("Positive"));
        assertThat(failureOptions.get("retry.max-attempts").description().orElseThrow(), containsString("zero"));
        assertThat(failureOptions.get("retry.max-attempts").description().orElseThrow(), containsString("DROP"));
        assertThat(failureOptions.get("retry.max-attempts").description().orElseThrow(), containsString("DEAD_LETTER"));
        assertThat(failureOptions.get("dead-letter.channel").description().orElseThrow(), containsString("required"));

        CmType execution = type(model, MessagingExecutionConfig.class);
        assertThat(execution.standalone(), is(true));
        assertThat(execution.prefix(), is(Optional.of(MessagingConfigSupport.EXECUTION)));
        Map<String, CmOption> executionOptions = options(execution);
        assertThat(executionOptions.keySet(),
                   is(Set.of("admission-timeout",
                             "max-in-flight-messages",
                             "max-pending-admissions",
                             "max-pending-messages",
                             "queue-capacity",
                             "shutdown-timeout")));
        assertOption(executionOptions, "admission-timeout", "java.time.Duration", null, false);
        assertOption(executionOptions, "max-in-flight-messages", "java.lang.Integer", "1024", false);
        assertOption(executionOptions, "max-pending-admissions", "java.lang.Integer", "64", false);
        assertOption(executionOptions, "max-pending-messages", "java.lang.Integer", "1024", false);
        assertOption(executionOptions, "queue-capacity", "java.lang.Integer", "0", false);
        assertOption(executionOptions, "shutdown-timeout", "java.time.Duration", "PT10S", false);
        assertThat(executionOptions.get("queue-capacity").description().orElseThrow(),
                   containsString("zero or greater"));
        assertThat(executionOptions.get("admission-timeout").description().orElseThrow(),
                   containsString("representable in nanoseconds"));
    }

    @Test
    void configReferenceIncludesMessaging(@TempDir Path outputDirectory) throws IOException {
        Main.main(new String[] {outputDirectory.toString()});

        assertThat(Files.isRegularFile(outputDirectory.resolve("config_reference.adoc")), is(true));
        String manifest = Files.readString(outputDirectory.resolve("manifest.adoc"));
        assertThat(manifest, containsString("io.helidon.messaging.spi.ConnectorConfig"));
        assertThat(manifest, containsString("io.helidon.messaging.FailurePolicy"));
        assertThat(manifest, containsString("io.helidon.messaging.MessagingExecutionConfig"));
        assertThat(manifest, containsString("io.helidon.messaging.spi.ConnectorDirection"));
        assertThat(manifest, containsString("io.helidon.messaging.FailureDisposition"));

        String helidonRoot = Files.readString(outputDirectory.resolve("io_helidon_HelidonConfig.adoc"));
        assertThat(helidonRoot, containsString("[`messaging`]"));
        String messagingRoot = Files.readString(outputDirectory.resolve("io_helidon_helidon_MessagingConfig.adoc"));
        assertThat(messagingRoot, containsString("io_helidon_messaging_MessagingExecutionConfig.adoc[`execution`]"));
        assertThat(messagingRoot, containsString("helidon.messaging"));

        String connector = Files.readString(
                outputDirectory.resolve("io_helidon_messaging_spi_ConnectorConfig.adoc"));
        assertThat(connector, containsString("`channel-name`"));
        assertThat(connector, containsString("`connector`"));
        assertThat(connector, containsString("`direction`"));
        assertThat(connector, containsString("io_helidon_messaging_spi_ConnectorDirection.adoc"));

        String failure = Files.readString(outputDirectory.resolve("io_helidon_messaging_FailurePolicy.adoc"));
        assertThat(failure, containsString("`retry.delay`"));
        assertThat(failure, containsString("`PT1S`"));
        assertThat(failure, containsString("`retry.max-attempts`"));
        assertThat(failure, containsString("`on-exhausted`"));
        assertThat(failure, containsString("io_helidon_messaging_FailureDisposition.adoc"));
        assertThat(failure, containsString("`dead-letter.channel`"));
        assertThat(failure, containsString("required"));

        String execution = Files.readString(
                outputDirectory.resolve("io_helidon_messaging_MessagingExecutionConfig.adoc"));
        assertThat(execution, containsString("`queue-capacity`"));
        assertThat(execution, containsString("zero or greater"));
        assertThat(execution, containsString("`max-pending-admissions`"));
        assertThat(execution, containsString("`max-pending-messages`"));
        assertThat(execution, containsString("`max-in-flight-messages`"));
        assertThat(execution, containsString("`admission-timeout`"));
        assertThat(execution, containsString("`shutdown-timeout`"));
        assertThat(execution, containsString("`PT10S`"));

        String connectorDirection = Files.readString(
                outputDirectory.resolve("io_helidon_messaging_spi_ConnectorDirection.adoc"));
        assertThat(connectorDirection, containsString("`INCOMING`"));
        assertThat(connectorDirection, containsString("`OUTGOING`"));
        String failureDisposition = Files.readString(
                outputDirectory.resolve("io_helidon_messaging_FailureDisposition.adoc"));
        assertThat(failureDisposition, containsString("`FAIL`"));
        assertThat(failureDisposition, containsString("`DROP`"));
        assertThat(failureDisposition, containsString("`DEAD_LETTER`"));
    }

    private static boolean hasPublicBuilderDecorator(Class<?> type) {
        for (Class<?> nestedType : type.getClasses()) {
            if (Prototype.BuilderDecorator.class.isAssignableFrom(nestedType)) {
                return true;
            }
        }
        return false;
    }

    private static CmType type(CmModel model, Class<?> type) {
        return model.modules().stream()
                .flatMap(module -> module.types().stream())
                .filter(candidate -> candidate.typeName().equals(type.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, CmOption> options(CmType type) {
        return type.options().stream()
                .collect(Collectors.toMap(option -> option.key().orElseThrow(), Function.identity()));
    }

    private static Set<String> allowedValues(CmOption option) {
        return option.allowedValues().stream()
                .map(CmModel.CmAllowedValue::value)
                .collect(Collectors.toSet());
    }

    private static void assertOption(Map<String, CmOption> options,
                                     String key,
                                     String type,
                                     String defaultValue,
                                     boolean required) {
        CmOption option = options.get(key);
        assertThat(option.typeName(), is(type));
        assertThat(option.defaultValue(), is(Optional.ofNullable(defaultValue)));
        assertThat(option.required(), is(required));
    }
}
