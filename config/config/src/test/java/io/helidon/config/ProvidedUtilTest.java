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

package io.helidon.config;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.builder.api.Option.Provider.ConfigForm;
import io.helidon.builder.api.Option.Provider.Identity;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.ConfigBuilderSupport.ProviderSettings;
import io.helidon.config.spi.ConfigNode;
import io.helidon.service.registry.ExistingInstanceDescriptor;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class ProvidedUtilTest {

    private static Config config;
    private static Config namedObjectConfig;
    private static Config namedListConfig;

    @BeforeAll
    static void init() {
        /*
        Create config that has a list under "services" to exercise list-based provider discovery.
        We cannot use Properties text because that creates a ConfigObject, not a list.
         */
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("services", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                           .addValue("type", "weighted")
                                           .build())
                        .build())
                .build();

        config = Config.just(ConfigSources.create(root));

        namedObjectConfig = configWithObject(ConfigNode.ObjectNode.builder()
                                                     .addObject("first", ConfigNode.ObjectNode.builder()
                                                             .addValue("type", "weighted")
                                                             .addValue("value", "one")
                                                             .build())
                                                     .addObject("second", ConfigNode.ObjectNode.builder()
                                                             .addValue("type", "weighted")
                                                             .addValue("value", "two")
                                                             .build())
                                                     .build());
        namedListConfig = configWithList(ConfigNode.ListNode.builder()
                                                 .addObject(ConfigNode.ObjectNode.builder()
                                                                    .addValue("type", "weighted")
                                                                    .addValue("name", "first")
                                                                    .addValue("value", "one")
                                                                    .build())
                                                 .addObject(ConfigNode.ObjectNode.builder()
                                                                    .addValue("type", "weighted")
                                                                    .addValue("name", "second")
                                                                    .addValue("value", "two")
                                                                    .build())
                                                 .build());
    }

    @Test
    void testServiceRegistryOrdering() {

        ServiceDescriptor<?> higher = ExistingInstanceDescriptor.create(
                new WeightedServiceProviderImpl("higher"),
                Set.of(WeightedServiceProvider.class),
                2.0);
        ServiceDescriptor<?> lower = ExistingInstanceDescriptor.create(
                new WeightedServiceProviderImpl("lower"),
                Set.of(WeightedServiceProvider.class),
                1.0);

        ServiceRegistry registry = ServiceRegistryManager.create(
                        ServiceRegistryConfig.builder()
                                .addServiceDescriptor(higher)
                                .addServiceDescriptor(lower)
                                .build())
                .registry();

        List<WeightedServiceImpl> matchedServices =
                ProvidedUtil.discoverServices(config,
                                              "services",
                                              Optional.of(registry),
                                              WeightedServiceProvider.class,
                                              WeightedServiceImpl.class,
                                              false,
                                              List.of());

        assertThat("Matched services", matchedServices.getFirst().nickname(), is(equalTo("higher")));
        assertThat(matchedServices, hasSize(1));
    }

    @Test
    void testServiceRegistryDuplicateTypeAndNameFailsBeforeProviderCreation() {
        WeightedServiceProviderImpl provider = new WeightedServiceProviderImpl("registry");
        ConfigException failure = assertThrows(ConfigException.class,
                                               () -> ProvidedUtil.discoverServices(duplicateTypeAndNameConfig(),
                                                                                   "services",
                                                                                   Optional.of(registry(provider)),
                                                                                   WeightedServiceProvider.class,
                                                                                   WeightedServiceImpl.class,
                                                                                   false,
                                                                                   List.of()));

        assertDuplicateIdentity(failure);
        assertThat(provider.createCount(), is(0));
    }

    @Test
    void testTypeAndNameObjectContainsSameEntriesAsListWithServiceRegistry() {
        WeightedServiceProviderImpl provider = new WeightedServiceProviderImpl("registry");
        ServiceRegistry registry = registry(provider);

        List<WeightedServiceImpl> fromObject = ProvidedUtil.discoverServices(namedObjectConfig,
                                                                              "services",
                                                                              Optional.of(registry),
                                                                              WeightedServiceProvider.class,
                                                                              WeightedServiceImpl.class,
                                                                              false,
                                                                              List.of());
        List<WeightedServiceImpl> fromList = ProvidedUtil.discoverServices(namedListConfig,
                                                                            "services",
                                                                            Optional.of(registry),
                                                                            WeightedServiceProvider.class,
                                                                            WeightedServiceImpl.class,
                                                                            false,
                                                                            List.of());

        assertEquivalentServices(fromObject, fromList);
        assertThat(provider.createCount(), is(4));
    }

    @Test
    void testTypeAndNameObjectContainsSameEntriesAsListWithServiceLoader() {
        WeightedServiceProviderImpl provider = new WeightedServiceProviderImpl("loader");
        HelidonServiceLoader<WeightedServiceProvider> serviceLoader =
                HelidonServiceLoader.builder(ServiceLoader.load(WeightedServiceProvider.class))
                        .useSystemServiceLoader(false)
                        .addService(provider, 1.0)
                        .build();

        List<WeightedServiceImpl> fromObject = ProvidedUtil.discoverServices(namedObjectConfig,
                                                                              "services",
                                                                              serviceLoader,
                                                                              WeightedServiceProvider.class,
                                                                              WeightedServiceImpl.class,
                                                                              false,
                                                                              List.of());
        List<WeightedServiceImpl> fromList = ProvidedUtil.discoverServices(namedListConfig,
                                                                            "services",
                                                                            serviceLoader,
                                                                            WeightedServiceProvider.class,
                                                                            WeightedServiceImpl.class,
                                                                            false,
                                                                            List.of());

        assertEquivalentServices(fromObject, fromList);
        assertThat(provider.createCount(), is(4));
    }

    @Test
    void testServiceLoaderDuplicateTypeAndNameFailsBeforeProviderCreation() {
        WeightedServiceProviderImpl provider = new WeightedServiceProviderImpl("loader");
        HelidonServiceLoader<WeightedServiceProvider> serviceLoader =
                HelidonServiceLoader.builder(ServiceLoader.load(WeightedServiceProvider.class))
                        .useSystemServiceLoader(false)
                        .addService(provider, 1.0)
                        .build();

        ConfigException failure = assertThrows(ConfigException.class,
                                               () -> ProvidedUtil.discoverServices(duplicateTypeAndNameConfig(),
                                                                                   "services",
                                                                                   serviceLoader,
                                                                                   WeightedServiceProvider.class,
                                                                                   WeightedServiceImpl.class,
                                                                                   false,
                                                                                   List.of()));

        assertDuplicateIdentity(failure);
        assertThat(provider.createCount(), is(0));
    }

    @Test
    void testTypeOnlyAutoUsesObjectFormAndAllowsProviderSpecificScalarValues() {
        Config scalarChild = configWithObject(ConfigNode.ObjectNode.builder()
                                                      .addValue("weighted", "provider-specific")
                                                      .build());
        WeightedServiceProviderImpl provider = new WeightedServiceProviderImpl("configured");
        ServiceRegistry registry = registry(provider);

        List<WeightedServiceImpl> services = ProvidedUtil.discoverServices(scalarChild,
                                                                            "services",
                                                                            Optional.of(registry),
                                                                            WeightedServiceProvider.class,
                                                                            WeightedServiceImpl.class,
                                                                            ProviderSettings.create(Identity.TYPE_ONLY,
                                                                                                    ConfigForm.AUTO,
                                                                                                    false),
                                                                            List.of());

        assertThat(services, hasSize(1));
        assertThat(services.getFirst().name(), is("weighted"));
        assertThat(provider.createCount(), is(1));

        Config list = configWithList(ConfigNode.ListNode.builder()
                                             .addObject(ConfigNode.ObjectNode.builder()
                                                                .addValue("type", "weighted")
                                                                .build())
                                             .build());
        ConfigException failure = assertInvalid(list, Identity.TYPE_ONLY, ConfigForm.AUTO);
        assertThat(failure.getMessage(), containsString("must use object form"));
    }

    @Test
    void testTypeOnlyObjectForbidsNestedIdentityProperties() {
        Config nestedType = configWithObject(ConfigNode.ObjectNode.builder()
                                                     .addObject("weighted", ConfigNode.ObjectNode.builder()
                                                             .addValue("type", "weighted")
                                                             .build())
                                                     .build());
        ConfigException typeFailure = assertInvalid(nestedType, Identity.TYPE_ONLY, ConfigForm.OBJECT);
        assertThat(typeFailure.getMessage(), containsString("must not declare \"type\""));

        Config nestedName = configWithObject(ConfigNode.ObjectNode.builder()
                                                     .addObject("weighted", ConfigNode.ObjectNode.builder()
                                                             .addValue("name", "instance")
                                                             .build())
                                                     .build());
        ConfigException nameFailure = assertInvalid(nestedName, Identity.TYPE_ONLY, ConfigForm.OBJECT);
        assertThat(nameFailure.getMessage(), containsString("must not declare \"name\""));

        Config listChild = configWithObject(ConfigNode.ObjectNode.builder()
                                                     .addList("weighted", ConfigNode.ListNode.builder()
                                                             .addValue("provider-specific")
                                                             .build())
                                                     .build());
        assertDoesNotThrow(() -> ProvidedUtil.validateProviderConfig(listChild.get("services"),
                                                                      Identity.TYPE_ONLY,
                                                                      ConfigForm.OBJECT));
    }

    @Test
    void testTypeOnlyListRules() {
        Config object = configWithObject(ConfigNode.ObjectNode.builder()
                                                 .addObject("weighted", ConfigNode.ObjectNode.builder().build())
                                                 .build());
        assertThat(assertInvalid(object, Identity.TYPE_ONLY, ConfigForm.LIST).getMessage(),
                   containsString("must use list form"));

        Config missingType = configWithList(ConfigNode.ListNode.builder()
                                                    .addObject(ConfigNode.ObjectNode.builder().build())
                                                    .build());
        assertThat(assertInvalid(missingType, Identity.TYPE_ONLY, ConfigForm.LIST).getMessage(),
                   containsString("must declare \"type\""));

        Config named = configWithList(ConfigNode.ListNode.builder()
                                              .addObject(ConfigNode.ObjectNode.builder()
                                                                 .addValue("type", "weighted")
                                                                 .addValue("name", "instance")
                                                                 .build())
                                              .build());
        assertThat(assertInvalid(named, Identity.TYPE_ONLY, ConfigForm.LIST).getMessage(),
                   containsString("must not declare \"name\""));

        Config duplicate = configWithList(ConfigNode.ListNode.builder()
                                                  .addObject(ConfigNode.ObjectNode.builder()
                                                                     .addValue("type", "weighted")
                                                                     .build())
                                                  .addObject(ConfigNode.ObjectNode.builder()
                                                                     .addValue("type", "weighted")
                                                                     .build())
                                                  .build());
        assertThat(assertInvalid(duplicate, Identity.TYPE_ONLY, ConfigForm.LIST).getMessage(),
                   containsString("Duplicate configured provider type \"weighted\""));

        Config valid = configWithList(ConfigNode.ListNode.builder()
                                              .addObject(ConfigNode.ObjectNode.builder()
                                                                 .addValue("type", "weighted")
                                                                 .build())
                                              .build());
        assertDoesNotThrow(() -> ProvidedUtil.validateProviderConfig(valid.get("services"),
                                                                      Identity.TYPE_ONLY,
                                                                      ConfigForm.LIST));
    }

    @Test
    void testTypeAndNameFormRestrictionsAndCompatibilityAuto() {
        Config object = configWithObject(ConfigNode.ObjectNode.builder()
                                                 .addValue("weighted", "provider-specific")
                                                 .build());
        Config list = configWithList(ConfigNode.ListNode.builder()
                                             .addObject(ConfigNode.ObjectNode.builder()
                                                                .addValue("type", "weighted")
                                                                .build())
                                             .build());

        assertDoesNotThrow(() -> ProvidedUtil.validateProviderConfig(object.get("services"),
                                                                      Identity.TYPE_AND_NAME,
                                                                      ConfigForm.AUTO));
        assertDoesNotThrow(() -> ProvidedUtil.validateProviderConfig(list.get("services"),
                                                                      Identity.TYPE_AND_NAME,
                                                                      ConfigForm.AUTO));
        assertDoesNotThrow(() -> ProvidedUtil.validateProviderConfig(object.get("services"),
                                                                      Identity.TYPE_AND_NAME,
                                                                      ConfigForm.OBJECT_OR_LIST));
        assertDoesNotThrow(() -> ProvidedUtil.validateProviderConfig(list.get("services"),
                                                                      Identity.TYPE_AND_NAME,
                                                                      ConfigForm.OBJECT_OR_LIST));
        assertThat(assertInvalid(list, Identity.TYPE_AND_NAME, ConfigForm.OBJECT).getMessage(),
                   containsString("must use object form"));
        assertThat(assertInvalid(object, Identity.TYPE_AND_NAME, ConfigForm.LIST).getMessage(),
                   containsString("must use list form"));
    }

    @Test
    void testTypeAndNameListAllowsSameNameForDifferentTypes() {
        Config sameName = configWithList(ConfigNode.ListNode.builder()
                                                 .addObject(ConfigNode.ObjectNode.builder()
                                                                    .addValue("type", "first")
                                                                    .addValue("name", "shared")
                                                                    .build())
                                                 .addObject(ConfigNode.ObjectNode.builder()
                                                                    .addValue("type", "second")
                                                                    .addValue("name", "shared")
                                                                    .build())
                                                 .build());

        assertDoesNotThrow(() -> ProvidedUtil.validateProviderConfig(sameName.get("services"),
                                                                      Identity.TYPE_AND_NAME,
                                                                      ConfigForm.LIST));
    }

    @Test
    void testInvalidRawConfigIsRejectedBeforeProviderCreation() {
        Config duplicate = configWithList(ConfigNode.ListNode.builder()
                                                  .addObject(ConfigNode.ObjectNode.builder()
                                                                     .addValue("type", "weighted")
                                                                     .build())
                                                  .addObject(ConfigNode.ObjectNode.builder()
                                                                     .addValue("type", "weighted")
                                                                     .build())
                                                  .build());
        WeightedServiceProviderImpl provider = new WeightedServiceProviderImpl("unused");

        assertThrows(ConfigException.class,
                     () -> ProvidedUtil.discoverServices(duplicate,
                                                         "services",
                                                         Optional.of(registry(provider)),
                                                         WeightedServiceProvider.class,
                                                         WeightedServiceImpl.class,
                                                         ProviderSettings.create(Identity.TYPE_ONLY,
                                                                                 ConfigForm.LIST,
                                                                                 false),
                                                         List.of()));

        assertThat(provider.createCount(), is(0));
    }

    private static ConfigException assertInvalid(Config config, Identity identity, ConfigForm configForm) {
        return assertThrows(ConfigException.class,
                            () -> ProvidedUtil.validateProviderConfig(config.get("services"), identity, configForm));
    }

    private static Config duplicateTypeAndNameConfig() {
        return configWithList(ConfigNode.ListNode.builder()
                                      .addObject(ConfigNode.ObjectNode.builder()
                                                         .addValue("type", "weighted")
                                                         .addValue("name", "duplicate")
                                                         .build())
                                      .addObject(ConfigNode.ObjectNode.builder()
                                                         .addValue("type", "weighted")
                                                         .addValue("name", "duplicate")
                                                         .build())
                                      .build());
    }

    private static void assertDuplicateIdentity(ConfigException failure) {
        assertThat(failure.getMessage(), containsString("Duplicate configured provider identity"));
        assertThat(failure.getMessage(), containsString("type \"weighted\""));
        assertThat(failure.getMessage(), containsString("name \"duplicate\""));
    }

    private static void assertEquivalentServices(List<WeightedServiceImpl> fromObject,
                                                 List<WeightedServiceImpl> fromList) {
        assertThat(fromObject, hasSize(2));
        assertThat(fromList, hasSize(2));

        // Object configuration does not define iteration order, so compare identity and provider payload as a set.
        Set<String> objectOutput = Set.copyOf(fromObject.stream()
                                                      .map(WeightedServiceImpl::output)
                                                      .toList());
        Set<String> listOutput = Set.copyOf(fromList.stream()
                                                    .map(WeightedServiceImpl::output)
                                                    .toList());
        assertThat(objectOutput, hasSize(2));
        assertThat(objectOutput, is(listOutput));
    }

    private static Config configWithObject(ConfigNode.ObjectNode providers) {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addObject("services", providers)
                .build();
        return Config.just(ConfigSources.create(root));
    }

    private static Config configWithList(ConfigNode.ListNode providers) {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("services", providers)
                .build();
        return Config.just(ConfigSources.create(root));
    }

    private static ServiceRegistry registry(WeightedServiceProvider provider) {
        ServiceDescriptor<?> descriptor = ExistingInstanceDescriptor.create(provider,
                                                                             Set.of(WeightedServiceProvider.class),
                                                                             1.0);
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .addServiceDescriptor(descriptor)
                                                     .build())
                .registry();
    }

    @Service.Contract
    interface WeightedServiceProvider extends ConfiguredProvider<WeightedServiceImpl> {
    }

    static class WeightedServiceProviderImpl implements WeightedServiceProvider {

        private final String nickname;
        private int createCount;

        WeightedServiceProviderImpl() {
            nickname = "unknown";
        }

        WeightedServiceProviderImpl(String nickname) {
            this.nickname = nickname;
        }

        @Override
        public String configKey() {
            return "weighted";
        }

        @Override
        public WeightedServiceImpl create(Config config, String name) {
            createCount++;
            return new WeightedServiceImpl(config, name, nickname);
        }

        int createCount() {
            return createCount;
        }

    }

    static class WeightedServiceImpl implements NamedService {

        private final String name;
        private final String nickname;
        private final String configuredValue;

        WeightedServiceImpl(Config config, String name, String nickname) {
            this.name = name;
            this.nickname = nickname;
            this.configuredValue = config.get("value").asString().orElse("");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String type() {
            return "weighted";
        }

        String nickname() {
            return nickname;
        }

        String output() {
            return type() + ":" + name + ":" + nickname + ":" + configuredValue;
        }
    }

}
