/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.jpa;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Priority;
import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.inject.Singleton;
import javax.persistence.Converter;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import javax.persistence.PersistenceUnit;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.helidon.integrations.cdi.jpa.PersistenceUnitInfoBean.DataSourceProvider;
import io.helidon.integrations.cdi.jpa.jaxb.Persistence;

import static javax.interceptor.Interceptor.Priority.LIBRARY_AFTER;

/**
 * A {@linkplain Extension portable extension} normally instantiated
 * by the Java {@linkplain java.util.ServiceLoader service provider
 * infrastructure} that integrates the provider-independent parts of
 * <a href="https://javaee.github.io/tutorial/partpersist.html#BNBPY"
 * target="_parent">JPA</a> into CDI.
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
 *
 * @see PersistenceUnitInfoBean
 */
public class JpaExtension implements Extension {


    /*
     * Static fields.
     */


    private static final String JAXB_GENERATED_PACKAGE_NAME = "io.helidon.integrations.cdi.jpa.jaxb";


    /*
     * Instance fields.
     */

    private final Set<PersistenceUnitInfoBean> potentialPersistenceUnitInfoBeans;

    /**
     * A {@link Map} of {@link Set}s of {@link Class}es whose keys are
     * persistence unit names and whose values are {@link Set}s of
     * {@link Class}es discovered by CDI (and hence consist of
     * unlisted classes in the sense that they might not be found in
     * any {@link PersistenceUnitInfo}).
     *
     * <p>Such {@link Class}es, of course, might not have been weaved
     * appropriately by the relevant {@link PersistenceProvider}.</p>
     *
     * <p>This field is never {@code null}.</p>
     */
    private final Map<String, Set<Class<?>>> unlistedManagedClassesByPersistenceUnitNames;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JpaExtension}.
     */
    public JpaExtension() {
        super();
        this.potentialPersistenceUnitInfoBeans = new HashSet<>();
        this.unlistedManagedClassesByPersistenceUnitNames = new HashMap<>();
    }


    /*
     * Instance methods.
     */


    private void discoverDataSourceDefinitions(@Observes
                                               @WithAnnotations({
                                                   DataSourceDefinition.class
                                               })
                                               final ProcessAnnotatedType<?> event,
                                               final BeanManager beanManager) {
        if (event != null && beanManager != null) {
            final AnnotatedType<?> annotatedType = event.getAnnotatedType();
            if (annotatedType != null) {
                final DataSourceDefinition dsd = annotatedType.getAnnotation(DataSourceDefinition.class);
                assert dsd != null;
                final String name = dsd.name();
                if (name != null) {
                    final Class<?> javaClass = annotatedType.getJavaClass();
                    assert javaClass != null;
                    // Get its URL
                    URL url = null;
                    final ProtectionDomain pd = javaClass.getProtectionDomain();
                    if (pd != null) {
                        final CodeSource cs = pd.getCodeSource();
                        if (cs != null) {
                            url = cs.getLocation();
                        }
                    }
                    if (url != null) {
                        // Revisit: need to populate these somehow
                        final Properties properties = new Properties();
                        final DataSourceProvider dataSourceProvider = new BeanManagerBackedDataSourceProvider(beanManager);
                        final PersistenceUnitInfoBean puInfo =
                            new PersistenceUnitInfoBean(name,
                                                        url,
                                                        null,
                                                        dataSourceProvider,
                                                        properties);
                        this.potentialPersistenceUnitInfoBeans.add(puInfo);
                    }
                }
            }
        }
    }

    private void discoverManagedClasses(@Observes
                                        @WithAnnotations({
                                            Converter.class,
                                            Entity.class,
                                            Embeddable.class,
                                            MappedSuperclass.class
                                        })
                                        final ProcessAnnotatedType<?> event) {
        if (event != null) {
            final AnnotatedType<?> annotatedType = event.getAnnotatedType();
            if (annotatedType != null) {
                final Class<?> managedClass = annotatedType.getJavaClass();
                assert managedClass != null;
                final Set<PersistenceUnit> persistenceUnits =
                    annotatedType.getAnnotations(PersistenceUnit.class);
                if (persistenceUnits == null || persistenceUnits.isEmpty()) {
                    Set<Class<?>> unlistedManagedClasses =
                        this.unlistedManagedClassesByPersistenceUnitNames.get("");
                    if (unlistedManagedClasses == null) {
                        unlistedManagedClasses = new HashSet<>();
                        this.unlistedManagedClassesByPersistenceUnitNames.put("", unlistedManagedClasses);
                    }
                    unlistedManagedClasses.add(managedClass);
                } else {
                    for (final PersistenceUnit persistenceUnit : persistenceUnits) {
                        String name = "";
                        if (persistenceUnit != null) {
                            name = persistenceUnit.unitName();
                            assert name != null;
                        }
                        Set<Class<?>> unlistedManagedClasses = this.unlistedManagedClassesByPersistenceUnitNames.get(name);
                        if (unlistedManagedClasses == null) {
                            unlistedManagedClasses = new HashSet<>();
                            this.unlistedManagedClassesByPersistenceUnitNames.put(name, unlistedManagedClasses);
                        }
                        unlistedManagedClasses.add(managedClass);
                    }
                }
            }
            event.veto(); // managed classes can't be beans
        }
    }

    private void afterBeanDiscovery(@Observes @Priority(LIBRARY_AFTER)
                                    final AfterBeanDiscovery event,
                                    final BeanManager beanManager)
        throws IOException, JAXBException, ReflectiveOperationException, XMLStreamException {
        if (event != null && beanManager != null) {
            // Add a bean for PersistenceProviderResolver.
            final PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
            event.addBean()
                .types(PersistenceProviderResolver.class)
                .scope(Singleton.class)
                .createWith(cc -> resolver);
            // Add a bean for each "generic" PersistenceProvider
            // reachable from the resolver.  (Any PersistenceUnitInfo
            // may also specify the class name of a
            // PersistenceProvider whose class may not be among those
            // loaded by the resolver; we deal with those later.)
            final Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
            for (final PersistenceProvider provider : providers) {
                event.addBean()
                    .addTransitiveTypeClosure(provider.getClass())
                    .scope(Singleton.class)
                    .createWith(cc -> provider);
            }
            boolean maybeAddDataSourceDefinitionBasedPersistenceUnits;
            // Collect all pre-existing PersistenceUnitInfo beans and
            // make sure their associated PersistenceProviders are
            // beanified.  (Many times this Set will be empty.)
            final Set<Bean<?>> preexistingPersistenceUnitInfoBeans =
                beanManager.getBeans(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
            if (preexistingPersistenceUnitInfoBeans == null || preexistingPersistenceUnitInfoBeans.isEmpty()) {
                maybeAddDataSourceDefinitionBasedPersistenceUnits = !this.potentialPersistenceUnitInfoBeans.isEmpty();
            } else {
                maybeAddDataSourceDefinitionBasedPersistenceUnits = false;
                for (final Bean<?> preexistingPersistenceUnitInfoBean : preexistingPersistenceUnitInfoBeans) {
                    if (preexistingPersistenceUnitInfoBean != null) {
                        // We use the Bean directly to create a
                        // PersistenceUnitInfo instance.  This
                        // instance is by definition unmanaged by CDI,
                        // which is fine in this narrow case: we throw
                        // it away immediately.  We need it only for
                        // the return values of
                        // getPersistenceProviderClassName() and
                        // getClassLoader().
                        final Object pui = preexistingPersistenceUnitInfoBean.create(null);
                        if (pui instanceof PersistenceUnitInfo) {
                            maybeAddPersistenceProviderBean(event, (PersistenceUnitInfo) pui, providers);
                        }
                    }
                }
            }
            // Discover all META-INF/persistence.xml resources, load
            // them using JAXB, and turn them into PersistenceUnitInfo
            // instances, and add beans for all of them as well as
            // their associated PersistenceProviders (if applicable).
            final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            assert classLoader != null;
            final Enumeration<URL> urls = classLoader.getResources("META-INF/persistence.xml");
            if (urls != null && urls.hasMoreElements()) {
                maybeAddDataSourceDefinitionBasedPersistenceUnits = false;
                final Supplier<? extends ClassLoader> tempClassLoaderSupplier;
                if (classLoader instanceof URLClassLoader) {
                    tempClassLoaderSupplier = () -> new URLClassLoader(((URLClassLoader) classLoader).getURLs());
                } else {
                    tempClassLoaderSupplier = () -> classLoader;
                }
                // We use StAX for XML loading because it is the same
                // strategy used by CDI implementations.  If the end
                // user wants to customize the StAX implementation
                // then we want that customization to apply here as
                // well.
                //
                // Note that XMLInputFactory is NOT deprecated in JDK 8
                // (https://docs.oracle.com/javase/8/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory--),
                // IS deprecated in JDK 9
                // (https://docs.oracle.com/javase/9/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory--)
                // with a claim that it was deprecated since JDK 1.7, but in
                // JDK 7 it actually was _not_ deprecated
                // (https://docs.oracle.com/javase/7/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory() )
                // and now in JDK 10 it is NO LONGER deprecated
                // (https://docs.oracle.com/javase/10/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory() ),
                // nor in JDK 11
                // (https://docs.oracle.com/en/java/javase/11/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory() ).
                @SuppressWarnings("deprecation")
                final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
                assert xmlInputFactory != null;
                final Unmarshaller unmarshaller = JAXBContext.newInstance(JAXB_GENERATED_PACKAGE_NAME).createUnmarshaller();
                assert unmarshaller != null;
                // Normally we'd let CDI instantiate this guy but we
                // are forbidden from getting references at this stage
                // in the lifecycle.  Instantiating this provider by
                // hand is fine as there is no state retained.
                final DataSourceProvider dataSourceProvider = new BeanManagerBackedDataSourceProvider(beanManager);
                while (urls.hasMoreElements()) {
                    final URL url = urls.nextElement();
                    assert url != null;
                    Collection<? extends PersistenceUnitInfo> persistenceUnitInfos = null;
                    try (InputStream inputStream = new BufferedInputStream(url.openStream())) {
                        final XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);
                        assert reader != null;
                        persistenceUnitInfos =
                            PersistenceUnitInfoBean.fromPersistence((Persistence) unmarshaller.unmarshal(reader),
                                                                    classLoader,
                                                                    tempClassLoaderSupplier,
                                                                    new URL(url, ".."), // e.g. META-INF/..
                                                                    this.unlistedManagedClassesByPersistenceUnitNames,
                                                                    dataSourceProvider);
                    }
                    if (persistenceUnitInfos != null && !persistenceUnitInfos.isEmpty()) {
                        for (final PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
                            assert persistenceUnitInfo != null;
                            final String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
                            event.addBean()
                                .types(Collections.singleton(PersistenceUnitInfo.class))
                                .scope(Singleton.class)
                                .addQualifiers(NamedLiteral.of(persistenceUnitName == null ? "" : persistenceUnitName))
                                .createWith(cc -> persistenceUnitInfo);
                            maybeAddPersistenceProviderBean(event, persistenceUnitInfo, providers);
                        }
                    }
                }
            }
            // If we didn't have any explicit PersistenceUnitInfo
            // beans, and we didn't have any META-INF/persistence.xml
            // resources, but we DID discover DataSourceDefinitions,
            // then we built up some PersistenceUnitInfo POJOs
            // earlier.  Beanify them.
            if (maybeAddDataSourceDefinitionBasedPersistenceUnits) {
                assert !this.potentialPersistenceUnitInfoBeans.isEmpty();
                for (final PersistenceUnitInfoBean persistenceUnitInfo : this.potentialPersistenceUnitInfoBeans) {
                    assert persistenceUnitInfo != null;
                    final String name = persistenceUnitInfo.getPersistenceUnitName();
                    assert name != null;
                    final Collection<? extends Class<?>> classes = this.unlistedManagedClassesByPersistenceUnitNames.get(name);
                    if (classes != null && !classes.isEmpty()) {
                        for (final Class<?> c : classes) {
                            assert c != null;
                            persistenceUnitInfo.addManagedClassName(c.getName());
                        }
                    }
                    event.addBean()
                        .types(Collections.singleton(PersistenceUnitInfo.class))
                        .scope(Singleton.class)
                        .addQualifiers(NamedLiteral.of(name))
                        .createWith(cc -> persistenceUnitInfo);
                    maybeAddPersistenceProviderBean(event, persistenceUnitInfo, providers);
                }
            }
        }
        this.potentialPersistenceUnitInfoBeans.clear();
        this.unlistedManagedClassesByPersistenceUnitNames.clear();
    }

    private static void maybeAddPersistenceProviderBean(final AfterBeanDiscovery event,
                                                        final PersistenceUnitInfo persistenceUnitInfo,
                                                        final Collection<? extends PersistenceProvider> providers)
        throws ReflectiveOperationException {
        Objects.requireNonNull(event);
        Objects.requireNonNull(persistenceUnitInfo);
        final String providerClassName = persistenceUnitInfo.getPersistenceProviderClassName();
        if (providerClassName != null) {

            boolean add = true;

            if (providers != null && !providers.isEmpty()) {
                for (final PersistenceProvider provider : providers) {
                    if (provider != null && provider.getClass().getName().equals(providerClassName)) {
                        add = false;
                        break;
                    }
                }
            }

            if (add) {

                // The PersistenceProvider class in question is not one we
                // already loaded.  Add a bean for it too.
                event.addBean()
                    .types(PersistenceProvider.class)
                    .scope(Singleton.class)
                    .createWith(cc -> {
                            try {
                                ClassLoader classLoader = persistenceUnitInfo.getClassLoader();
                                if (classLoader == null) {
                                    classLoader = Thread.currentThread().getContextClassLoader();
                                }
                                assert classLoader != null;
                                @SuppressWarnings("unchecked")
                                    final Class<? extends PersistenceProvider> c =
                                    (Class<? extends PersistenceProvider>) Class.forName(providerClassName, true, classLoader);
                                return c.getDeclaredConstructor().newInstance();
                            } catch (final ReflectiveOperationException reflectiveOperationException) {
                                throw new CreationException(reflectiveOperationException.getMessage(),
                                                            reflectiveOperationException);
                            }
                        });
            }

        }
    }

}
