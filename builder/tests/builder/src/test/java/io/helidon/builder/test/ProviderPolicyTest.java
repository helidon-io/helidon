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

package io.helidon.builder.test;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.test.testsubjects.ProviderPolicy;
import io.helidon.builder.test.testsubjects.SomeProvider;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderPolicyTest {
    @Test
    void testAnnotationDefaultsPreserveCompatibility() throws NoSuchMethodException {
        assertThat(Option.Provider.class.getDeclaredMethod("identity").getDefaultValue(),
                   is(Option.Provider.Identity.TYPE_AND_NAME));
        assertThat(Option.Provider.class.getDeclaredMethod("configForm").getDefaultValue(),
                   is(Option.Provider.ConfigForm.AUTO));
    }

    @Test
    void testTypeOnlyAutoObjectForm() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addObject("type-only-auto", ConfigNode.ObjectNode.builder()
                        .addObject("some-1", ConfigNode.ObjectNode.builder()
                                .addValue("prop", "configured")
                                .build())
                        .build())
                .build();

        List<SomeProvider.SomeService> services = ProviderPolicy.create(config(root))
                .typeOnlyAuto()
                .orElseThrow();

        assertThat(services, hasSize(1));
        assertThat(services.getFirst().name(), is("some-1"));
        assertThat(services.getFirst().prop(), is("configured"));
    }

    @Test
    void testGeneratedValidationPrecedesOptionalContainerConversion() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addValue("type-only-auto", "invalid-outer-value")
                .build();

        ConfigException failure = assertThrows(ConfigException.class, () -> ProviderPolicy.create(config(root)));

        assertThat(failure.getMessage(), containsString("must use object form"));
    }

    @Test
    void testTypeOnlyExplicitListForm() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("type-only-list", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-1")
                                .addValue("prop", "configured")
                                .build())
                        .build())
                .build();

        List<SomeProvider.SomeService> services = ProviderPolicy.create(config(root)).typeOnlyList();

        assertThat(services, hasSize(1));
        assertThat(services.getFirst().name(), is("some-1"));
        assertThat(services.getFirst().prop(), is("configured"));
    }

    @Test
    void testTypeOnlyExplicitListRejectsDuplicateTypes() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("type-only-list", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-1")
                                .build())
                        .addObject(ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-1")
                                .build())
                        .build())
                .build();

        ConfigException failure = assertThrows(ConfigException.class, () -> ProviderPolicy.create(config(root)));

        assertThat(failure.getMessage(), containsString("Duplicate configured provider type \"some-1\""));
    }

    @Test
    void testTypeAndNameExplicitForms() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addObject("type-and-name-object", ConfigNode.ObjectNode.builder()
                        .addObject("first", ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-1")
                                .build())
                        .build())
                .addList("type-and-name-list", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-2")
                                .addValue("name", "second")
                                .build())
                        .build())
                .build();

        ProviderPolicy policy = ProviderPolicy.create(config(root));

        assertThat(policy.typeAndNameObject().getFirst().name(), is("first"));
        assertThat(policy.typeAndNameList().getFirst().name(), is("second"));
    }

    @Test
    void testTypeAndNameListRejectsDuplicateIdentities() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("type-and-name-list", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-1")
                                .addValue("name", "duplicate")
                                .build())
                        .addObject(ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-1")
                                .addValue("name", "duplicate")
                                .build())
                        .build())
                .build();

        ConfigException failure = assertThrows(ConfigException.class, () -> ProviderPolicy.create(config(root)));

        assertThat(failure.getMessage(), containsString("Duplicate configured provider identity"));
        assertThat(failure.getMessage(), containsString("type \"some-1\""));
        assertThat(failure.getMessage(), containsString("name \"duplicate\""));
    }

    @Test
    void testTypeAndNameListAllowsSameNameForDifferentTypes() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("type-and-name-list", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-1")
                                .addValue("name", "shared")
                                .build())
                        .addObject(ConfigNode.ObjectNode.builder()
                                .addValue("type", "some-2")
                                .addValue("name", "shared")
                                .build())
                        .build())
                .build();

        List<SomeProvider.SomeService> services = ProviderPolicy.create(config(root)).typeAndNameList();

        assertThat(services, hasSize(2));
        assertThat(services.stream().map(SomeProvider.SomeService::type).toList(),
                   is(List.of("some-1", "some-2")));
        assertThat(services.stream().map(SomeProvider.SomeService::name).toList(),
                   is(List.of("shared", "shared")));
    }

    @Test
    void testTypeAndNameExplicitFormsRejectWrongContainers() {
        ConfigNode.ObjectNode objectAsList = ConfigNode.ObjectNode.builder()
                .addList("type-and-name-object", ConfigNode.ListNode.builder().build())
                .build();
        ConfigException objectFailure = assertThrows(ConfigException.class,
                                                      () -> ProviderPolicy.create(config(objectAsList)));
        assertThat(objectFailure.getMessage(), containsString("must use object form"));

        ConfigNode.ObjectNode listAsObject = ConfigNode.ObjectNode.builder()
                .addObject("type-and-name-list", ConfigNode.ObjectNode.builder().build())
                .build();
        ConfigException listFailure = assertThrows(ConfigException.class,
                                                    () -> ProviderPolicy.create(config(listAsObject)));
        assertThat(listFailure.getMessage(), containsString("must use list form"));
    }

    @Test
    void testNonConfiguredProviderIgnoresConfiguredProviderPolicies() {
        ProviderPolicy policy = ProviderPolicy.create(Config.empty());

        assertThat(policy.nonConfigured(), is(List.of()));
    }

    private static Config config(ConfigNode.ObjectNode root) {
        return Config.just(ConfigSources.create(root));
    }
}
