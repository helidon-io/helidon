/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.inject.Named;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.spi.InjectionProvider;
import io.helidon.microprofile.cdi.BuildTimeStart;
import io.helidon.microprofile.cdi.RuntimeStart;

import org.eclipse.microprofile.config.ConfigProvider;

public class VaultCdiExtension implements Extension {
    private final List<InjectionProvider> providers;
    private final Set<Type> supportedTypes = new HashSet<>();
    private final Set<RequiredProducer> requiredProducers = new HashSet<>();
    private final Set<String> requiredNames = new HashSet<>();

    private Config config;

    public VaultCdiExtension() {
        providers = HelidonServiceLoader
                .builder(ServiceLoader.load(InjectionProvider.class))
                .build()
                .asList();

        for (InjectionProvider provider : providers) {
            provider.injectables()
                    .stream()
                    .map(InjectionProvider.InjectionType::injectedType)
                    .forEach(supportedTypes::add);
        }
    }

    private void configure(@Observes @RuntimeStart Config config) {
        this.config = config.get("vault");
    }

    /**
     * Add internal qualifier.
     *
     * @param event CDI event
     */
    void updateInjectionPoints(@Observes ProcessInjectionPoint<?, ?> event) {
        InjectionPoint injectionPoint = event.getInjectionPoint();
        Annotated annotated = injectionPoint.getAnnotated();

        Type type = injectionPoint.getType();
        if (supportedTypes.contains(type)) {
            VaultName vault = annotated.getAnnotation(VaultName.class);
            VaultPath vaultPath = annotated.getAnnotation(VaultPath.class);

            VaultInternal internal = VaultInternal.Literal
                    .create((vault == null ? "" : vault.value()),
                            (vaultPath == null ? "" : vaultPath.value()));

            event.configureInjectionPoint()
                    .addQualifier(internal);
        }
    }

    /**
     * Collect injection points that are valid.
     *
     * @param event CDI event
     */
    void processInjectionPointsFromEnabledBeans(@Observes ProcessBean<?> event) {
        for (InjectionPoint injectionPoint : event.getBean().getInjectionPoints()) {
            Set<Annotation> qualifiers = injectionPoint.getQualifiers();
            for (Annotation qualifier : qualifiers) {
                if (qualifier.annotationType().equals(VaultInternal.class)) {
                    VaultInternal vi = (VaultInternal) qualifier;

                    requiredNames.add(vi.name());
                    requiredProducers.add(new RequiredProducer(injectionPoint.getType(), vi));
                    break;
                }
            }

        }
    }

    /**
     * Add producers for all expected injection points and producers for defaults.
     *
     * @param event CDI event
     */
    void registerProducers(@Observes AfterBeanDiscovery event) {

        if (config == null) {
            // this method is called before the configuration is set up for runtime
            // we need to use the build time configuration and add names from it
            // only relevant in native-image
            config = ((Config) ConfigProvider.getConfig()).get("vault");
        }

        if (config.get("default").exists()) {
            addNamesFromConfig(config);
        } else {
            if (config.get("address").exists()) {
                requiredNames.add("");
            } else {
                addNamesFromConfig(config);
            }
        }

        // add all producers for named vaults with default path
        for (InjectionProvider provider : providers) {
            for (InjectionProvider.InjectionType<?> injectable : provider.injectables()) {
                for (String requiredName : requiredNames) {
                    RequiredProducer required = new RequiredProducer(injectable.injectedType(),
                                                                     VaultInternal.Literal.create(requiredName,
                                                                                                  ""));
                    requiredProducers.add(required);
                }
            }
        }

        // add all producers for declared injection points (may have customized path)
        for (RequiredProducer required : requiredProducers) {
            addProducer(event, required);
        }

        requiredProducers.clear();

        // add the named vaults
        for (String requiredName : requiredNames) {
            if (requiredName.isEmpty()) {
                // add the unnamed (default) vault
                addVault(event);
            }
            addVault(event, requiredName, requiredName);
        }
    }

    private void addNamesFromConfig(Config config) {
        config.asNodeList()
                .stream()
                .flatMap(Collection::stream)
                .map(Config::key)
                .map(Config.Key::name)
                .map(name -> "default".equals(name) ? "" : name)
                .forEach(requiredNames::add);
    }

    @SuppressWarnings("unchecked")
    private void addProducer(AfterBeanDiscovery event, RequiredProducer required) {
        String name = required.internal.name();
        Type type = required.type;

        InjectionProvider.InjectionType<?> found = findInjectionProvider(type)
                .orElseThrow(() -> new DeploymentException("Could not find valid injection provider for type " + type));

        event.addBean(new QualifiedBean<>(VaultCdiExtension.class,
                                          (Class<Object>) type,
                                          required.qualifiers(),
                                          () -> {
                                              Config config = producerConfig(name);
                                              Vault vault = CDI.current().select(Vault.class, required.vaultQualifiers()).get();
                                              return found.createInstance(vault, config, required.instanceConfig());
                                          }));

        if (required.internal.path().isEmpty()) {
            // we also want to add named instance if path is default
            String newName;

            if (name.isEmpty()) {
                // add unnamed
                event.addBean(new QualifiedBean<>(VaultCdiExtension.class,
                                                  (Class<Object>) type,
                                                  () -> {
                                                      Config config = producerConfig(name);
                                                      Vault vault = CDI.current().select(Vault.class, required.vaultQualifiers()).get();
                                                      return found.createInstance(vault, config, required.instanceConfig());
                                                  }));
                newName = "default";
            } else {
                newName = name;
            }
            event.addBean(new QualifiedBean<>(VaultCdiExtension.class,
                                (Class<Object>) type,
                                Set.of(NamedLiteral.of(required.internal.name())),
                                () -> {
                                    Config config = producerConfig(newName);
                                    Vault vault = CDI.current().select(Vault.class, required.vaultQualifiers()).get();
                                    return found.createInstance(vault, config, required.instanceConfig());
                                }));
        }
    }

    private Optional<InjectionProvider.InjectionType<?>> findInjectionProvider(Type type) {
        for (InjectionProvider provider : providers) {
            for (InjectionProvider.InjectionType<?> injectable : provider.injectables()) {
                if (injectable.injectedType().equals(type)) {
                    return Optional.of(injectable);
                }
            }
        }
        return Optional.empty();
    }

    private Config producerConfig(String name) {
        if (name.isEmpty() || name.equals("default")) {
            if (config.get("default").exists()) {
                return config.get("default");
            }
            return config;
        }
        return config.get(name);
    }

    private void addVault(AfterBeanDiscovery event) {
        event.addBean(new QualifiedBean<>(Vault.class,
                                          Vault.class,
                                          () -> {
                                              if (config.get("default").exists()) {
                                                  return Vault.builder()
                                                          .config(config.get("default"))
                                                          .build();
                                              } else {
                                                  return Vault.builder()
                                                          .config(config)
                                                          .build();
                                              }
                                          }));
    }

    private void addVault(AfterBeanDiscovery event, String configKey, String name) {
        Named named = NamedLiteral.of(name);

        event.addBean(new QualifiedBean<>(Vault.class,
                                          Vault.class,
                                          Set.of(named),
                                          () -> Vault.builder()
                                                  .config(config.get(configKey))
                                                  .build()));
    }

    private static final class RequiredProducer {
        private static final Annotation[] ANNOTATIONS = new Annotation[0];
        private final Type type;
        private final VaultInternal internal;

        private RequiredProducer(Type type, VaultInternal internal) {
            this.type = type;
            this.internal = internal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RequiredProducer that = (RequiredProducer) o;
            return type.equals(that.type) && internal.equals(that.internal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, internal);
        }

        @Override
        public String toString() {
            return type + ": " + internal;
        }

        public InjectionProvider.InstanceConfig instanceConfig() {
            InjectionProvider.InstanceConfig.Builder builder = InjectionProvider.InstanceConfig.builder();

            if (!internal.name().isEmpty()) {
                builder.vaultName(internal.name());
            }

            if (!internal.path().isEmpty()) {
                builder.vaultPath(internal.path());
            }

            return builder.build();
        }

        private Set<Annotation> qualifiers() {
            return Set.of(internal);
        }

        private Annotation[] vaultQualifiers() {
            if (!internal.name().equals("")) {
                return new Annotation[] {NamedLiteral.of(internal.name())};
            }
            return ANNOTATIONS;
        }
    }
}
