/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.oci.objectstorage;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * An {@link Extension} that integrates the {@link ObjectStorage}
 * interface into CDI-based applications.
 *
 * @see com.oracle.bmc.objectstorage.ObjectStorage
 */
public class OCIObjectStorageExtension implements Extension {

  private final Map<Set<Annotation>, Object> objectStorageBeans;

  private final Map<Set<Annotation>, Object> objectStorageClientBuilderBeans;

  private final Map<Set<Annotation>, Object> authenticationDetailsProviderBeans;

  /**
   * Creates a new {@link OCIObjectStorageExtension}.
   */
  public OCIObjectStorageExtension() {
    super();
    this.objectStorageBeans = new HashMap<>(7);
    this.objectStorageClientBuilderBeans = new HashMap<>(7);
    this.authenticationDetailsProviderBeans = new HashMap<>(7);
  }

  private void processObjectStorageInjectionPoints(@Observes final ProcessInjectionPoint<?, ? extends ObjectStorage> event) {
    if (event != null) {
      final InjectionPoint injectionPoint = event.getInjectionPoint();
      if (injectionPoint != null) {
        this.objectStorageBeans.put(injectionPoint.getQualifiers(), null);
      }
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processObjectStorageClientBuilderInjectionPoints(@Observes final ProcessInjectionPoint<?, ? extends ObjectStorageClient.Builder> event) {
    if (event != null) {
      final InjectionPoint injectionPoint = event.getInjectionPoint();
      if (injectionPoint != null) {
        this.objectStorageClientBuilderBeans.put(injectionPoint.getQualifiers(), null);
      }
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processAuthenticationDetailsProviderInjectionPoints(@Observes final ProcessInjectionPoint<?, ? extends AuthenticationDetailsProvider> event) {
    if (event != null) {
      final InjectionPoint injectionPoint = event.getInjectionPoint();
      if (injectionPoint != null) {
        this.authenticationDetailsProviderBeans.put(injectionPoint.getQualifiers(), null);
      }
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processPreexistingAuthenticationDetailsProviderManagedBean(@Observes final ProcessManagedBean<? extends AuthenticationDetailsProvider> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.authenticationDetailsProviderBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.authenticationDetailsProviderBeans.put(qualifiers, bean);
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processPreexistingAuthenticationDetailsProviderProducerField(@Observes final ProcessProducerField<? extends AuthenticationDetailsProvider, ?> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.authenticationDetailsProviderBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.authenticationDetailsProviderBeans.put(qualifiers, bean);
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processPreexistingAuthenticationDetailsProviderProducerMethod(@Observes final ProcessProducerMethod<? extends AuthenticationDetailsProvider, ?> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.authenticationDetailsProviderBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.authenticationDetailsProviderBeans.put(qualifiers, bean);
    }
  }


  /*
   * ObjectStorageClient.Builder beans.
   */


  @SuppressWarnings("checkstyle:linelength")
  private void processPreexistingObjectStorageClientBuilderManagedBean(@Observes final ProcessManagedBean<? extends ObjectStorageClient.Builder> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.objectStorageClientBuilderBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.objectStorageClientBuilderBeans.put(qualifiers, bean);
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processPreexistingObjectStorageClientBuilderProducerField(@Observes final ProcessProducerField<? extends ObjectStorageClient.Builder, ?> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.objectStorageClientBuilderBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.objectStorageClientBuilderBeans.put(qualifiers, bean);
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processPreexistingObjectStorageClientBuilderProducerMethod(@Observes final ProcessProducerMethod<? extends ObjectStorageClient.Builder, ?> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.objectStorageClientBuilderBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.objectStorageClientBuilderBeans.put(qualifiers, bean);
    }
  }


  /*
   * ObjectStorage beans.
   */


  private void processPreexistingObjectStorageManagedBean(@Observes final ProcessManagedBean<? extends ObjectStorage> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.objectStorageBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.objectStorageBeans.put(qualifiers, bean);
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processPreexistingObjectStorageProducerField(@Observes final ProcessProducerField<? extends ObjectStorage, ?> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.objectStorageBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.objectStorageBeans.put(qualifiers, bean);
    }
  }

  @SuppressWarnings("checkstyle:linelength")
  private void processPreexistingObjectStorageProducerMethod(@Observes final ProcessProducerMethod<? extends ObjectStorage, ?> event) {
    if (event != null) {
      final Bean<?> bean = event.getBean();
      assert bean != null;
      final Set<Annotation> qualifiers = bean.getQualifiers();
      if (this.objectStorageBeans.containsKey(bean.getQualifiers())) {
        throw new AmbiguousResolutionException();
      }
      this.objectStorageBeans.put(qualifiers, bean);
    }
  }


  /*
   * Generated beans.
   */


  private void addBeans(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) {
    if (event != null && beanManager != null) {

      // We can't look up Config as a CDI bean here because it is
      // illegal to call beanManager.getReference() until
      // AfterDeploymentValidation time, by which point it is too late
      // to add custom beans.  And we don't want to look it up
      // expensively inside each bean's create() method.  So we do it
      // "manually" here.
      final Config config = ConfigProvider.getConfig();
      assert config != null;

      if (!this.authenticationDetailsProviderBeans.isEmpty()) {
        for (final Entry<Set<Annotation>, ?> adpEntry : this.authenticationDetailsProviderBeans.entrySet()) {
          assert adpEntry != null;
          if (adpEntry.getValue() == null) {
            // There was a qualified or default injection point, but
            // no bean to satisfy it.  Generate one.
            final Set<Annotation> qualifiers = adpEntry.getKey();
            assert qualifiers != null;
            assert !qualifiers.isEmpty();
            event.<AuthenticationDetailsProvider>addBean()
              .scope(ApplicationScoped.class)
              .addTransitiveTypeClosure(MicroProfileConfigAuthenticationDetailsProvider.class)
              .beanClass(MicroProfileConfigAuthenticationDetailsProvider.class)
              .qualifiers(qualifiers)
              .createWith(cc -> new MicroProfileConfigAuthenticationDetailsProvider(config));
          }
        }
      }

      if (!this.objectStorageClientBuilderBeans.isEmpty()) {
        for (final Entry<Set<Annotation>, ?> oscbEntry : this.objectStorageClientBuilderBeans.entrySet()) {
          assert oscbEntry != null;
          if (oscbEntry.getValue() == null) {
            // There was a qualified or default injection point, but
            // no bean to satisfy it.  Generate one.
            final Set<Annotation> qualifiers = oscbEntry.getKey();
            assert qualifiers != null;
            assert !qualifiers.isEmpty();
            final Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[qualifiers.size()]);
            event.<ObjectStorageClient.Builder>addBean()
              .scope(ApplicationScoped.class)
              .addTransitiveTypeClosure(ObjectStorageClient.Builder.class)
              .beanClass(ObjectStorageClient.Builder.class)
              .qualifiers(qualifiers)
              .createWith(cc -> {
                  final ObjectStorageClient.Builder builder = ObjectStorageClient.builder();
                  assert builder != null;
                  // Permit further customization before the bean is actually created
                  beanManager.getEvent().select(ObjectStorageClient.Builder.class, qualifiersArray).fire(builder);
                  return builder;
                });
          }
        }
      }

      if (!this.objectStorageBeans.isEmpty()) {
        for (final Entry<Set<Annotation>, ?> osbEntry : this.objectStorageBeans.entrySet()) {
          assert osbEntry != null;
          if (osbEntry.getValue() == null) {
            // There was a qualified or default injection point, but
            // no bean to satisfy it.  Generate one.
            final Set<Annotation> qualifiers = osbEntry.getKey();
            assert qualifiers != null;
            assert !qualifiers.isEmpty();
            final Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[qualifiers.size()]);
            event.<ObjectStorage>addBean()
              .scope(ApplicationScoped.class)
              .addTransitiveTypeClosure(ObjectStorageClient.class)
              .beanClass(ObjectStorageClient.class)
              .qualifiers(qualifiers)
              .createWith(cc -> {
                  Set<Bean<?>> beans = beanManager.getBeans(ObjectStorageClient.Builder.class, qualifiersArray);
                  final ObjectStorageClient.Builder builder;
                  if (beans == null || beans.isEmpty()) {
                    builder = ObjectStorageClient.builder();
                    assert builder != null;
                    // Permit further customization before the bean is actually created
                    beanManager.getEvent().select(ObjectStorageClient.Builder.class, qualifiersArray).fire(builder);
                  } else {
                    final Bean<?> bean = beanManager.resolve(beans);
                    assert bean != null;
                    builder = (ObjectStorageClient.Builder) beanManager.getReference(bean,
                                                                                     ObjectStorageClient.Builder.class,
                                                                                     beanManager.createCreationalContext(bean));
                  }
                  assert builder != null;

                  beans = beanManager.getBeans(AuthenticationDetailsProvider.class, qualifiersArray);
                  final AuthenticationDetailsProvider authProvider;
                  if (beans == null || beans.isEmpty()) {
                    authProvider = new MicroProfileConfigAuthenticationDetailsProvider(config);
                  } else {
                    final Bean<?> bean = beanManager.resolve(beans);
                    assert bean != null;
                    authProvider =
                      (AuthenticationDetailsProvider) beanManager.getReference(bean,
                                                                               AuthenticationDetailsProvider.class,
                                                                               beanManager.createCreationalContext(bean));
                  }
                  assert authProvider != null;
                  final ObjectStorage objectStorage = builder.build(authProvider);
                  assert objectStorage != null;
                  objectStorage.setRegion(config.getValue("oci.objectstorage.region", String.class)); // hack
                  return objectStorage;
                });
          }
        }
      }

    }
    this.authenticationDetailsProviderBeans.clear();
    this.objectStorageBeans.clear();
    this.objectStorageClientBuilderBeans.clear();
  }

}
