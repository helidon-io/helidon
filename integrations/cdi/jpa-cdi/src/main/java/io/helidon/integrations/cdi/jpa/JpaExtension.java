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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Vetoed;
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
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceProperty;
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
     * Instance fields.
     */


    /**
     * A {@link Map} of {@link PersistenceUnitInfoBean} instances that
     * were created by the {@link
     * #gatherImplicitPersistenceUnits(ProcessAnnotatedType,
     * BeanManager)} observer method, indexed by the names of
     * persistence units.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>The contents of this field are used only when no explicit
     * {@link PersistenceUnitInfo} beans are otherwise available in
     * the container.</p>
     *
     * @see #gatherImplicitPersistenceUnits(ProcessAnnotatedType, BeanManager)
     *
     * @see #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)
     */
    private final Map<String, PersistenceUnitInfoBean> implicitPersistenceUnits;

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

    /**
     * The {@link Logger} for use by this {@link JpaExtension}.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see #createLogger()
     */
    private final Logger logger;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JpaExtension}.
     *
     * @exception NullPointerException if an override of the {@link
     * #createLogger()} method returns {@code null}, violating its
     * contract
     */
    public JpaExtension() {
        super();
        this.logger = Objects.requireNonNull(this.createLogger());
        this.unlistedManagedClassesByPersistenceUnitNames = new HashMap<>();
        this.implicitPersistenceUnits = new HashMap<>();
    }


    /*
     * Instance methods.
     */


    /**
     * Returns a {@link Logger} for use by this {@link JpaExtension}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>Overrides of this method must not return {@code null}.</p>
     *
     * <p>This method is called during {@linkplain #JpaExtension()
     * construction}.</p>
     *
     * @return a non-{@code null} {@link Logger}
     *
     * @see #getLogger()
     */
    protected Logger createLogger() {
        return Logger.getLogger(this.getClass().getName());
    }

    /**
     * Returns the non-{@code null} {@link Logger} created by the
     * {@link #createLogger()} method.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a non-{@code null} {@link Logger}
     *
     * @see #createLogger()
     */
    protected final Logger getLogger() {
        return this.logger;
    }

    /**
     * Looks for type-level {@link PersistenceContext} annotations
     * that have at least one {@link PersistenceProperty} annotation
     * {@linkplain PersistenceContext#properties() associated with}
     * them and uses them to define persistence units, potentially
     * preventing the need for {@code META-INF/persistence.xml}
     * processing.
     *
     * @param event the {@link ProcessAnnotatedType} event occurring;
     * may be {@code null} in which case no action will be taken
     *
     * @param beanManager the {@link BeanManager} in effect; may be
     * {@code null} in which case no action will be taken
     *
     * @see PersistenceContext
     *
     * @see PersistenceProperty
     *
     * @see PersistenceUnitInfoBean
     */
    private void gatherImplicitPersistenceUnits(@Observes
                                                @WithAnnotations({
                                                    PersistenceContext.class // yes, PersistenceContext, not PersistenceUnit
                                                })
                                                final ProcessAnnotatedType<?> event,
                                                final BeanManager beanManager) {
        final String cn = this.getClass().getName();
        final String mn = "gatherImplicitPersistenceUnits";
        final Logger logger = this.getLogger();
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, new Object[] {event, beanManager});
        }

        if (event != null && beanManager != null) {
            final AnnotatedType<?> annotatedType = event.getAnnotatedType();
            if (annotatedType != null
                && !annotatedType.isAnnotationPresent(Vetoed.class)) {
                final Set<? extends PersistenceContext> persistenceContexts =
                    annotatedType.getAnnotations(PersistenceContext.class);
                if (persistenceContexts != null && !persistenceContexts.isEmpty()) {
                    for (final PersistenceContext persistenceContext : persistenceContexts) {
                        assert persistenceContext != null;
                        final PersistenceProperty[] persistenceProperties = persistenceContext.properties();
                        if (persistenceProperties != null && persistenceProperties.length > 0) {
                            final String persistenceUnitName = persistenceContext.unitName();
                            assert persistenceUnitName != null;
                            PersistenceUnitInfoBean persistenceUnit = this.implicitPersistenceUnits.get(persistenceUnitName);
                            if (persistenceUnit == null) {
                                final String jtaDataSourceName;
                                if (persistenceUnitName.isEmpty()) {
                                    jtaDataSourceName = null;
                                } else {
                                    jtaDataSourceName = persistenceUnitName;
                                }
                                final Class<?> javaClass = annotatedType.getJavaClass();
                                assert javaClass != null;
                                URL persistenceUnitRoot = null;
                                final ProtectionDomain pd = javaClass.getProtectionDomain();
                                if (pd != null) {
                                    final CodeSource cs = pd.getCodeSource();
                                    if (cs != null) {
                                        persistenceUnitRoot = cs.getLocation();
                                    }
                                }
                                final Properties properties = new Properties();
                                for (final PersistenceProperty persistenceProperty : persistenceProperties) {
                                    assert persistenceProperty != null;
                                    final String persistencePropertyName = persistenceProperty.name();
                                    assert persistencePropertyName != null;
                                    if (!persistencePropertyName.isEmpty()) {
                                        properties.setProperty(persistencePropertyName, persistenceProperty.value());
                                    }
                                }
                                persistenceUnit =
                                    new PersistenceUnitInfoBean(persistenceUnitName,
                                                                persistenceUnitRoot,
                                                                null,
                                                                new BeanManagerBackedDataSourceProvider(beanManager),
                                                                properties);
                                this.implicitPersistenceUnits.put(persistenceUnitName, persistenceUnit);
                            } else {
                                // Skip processing this annotation, or
                                // actually probably register an
                                // error
                            }
                        } else {
                            // Skip processing this annotation; there
                            // aren't any @PersistenceProperties on it
                            // so it may be a simple declaration of a
                            // dependency on this persistence unit
                        }
                    }
                }
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
        }
    }

    /**
     * Tracks {@linkplain Converter converters}, {@linkplain Entity
     * entities}, {@linkplain Embeddable embeddables} and {@linkplain
     * MappedSuperclass mapped superclasses} that were auto-discovered
     * by CDI bean discovery, and makes sure that they are not
     * actually CDI beans, since according to the JPA specification
     * they cannot be.
     *
     * <p>This method also keeps track of these classes as potential
     * "unlisted classes" to be used by a {@linkplain
     * PersistenceUnitInfo persistence unit} if its {@linkplain
     * PersistenceUnitInfo#excludeUnlistedClasses()} method returns
     * {@code false}.</p>
     *
     * @param event the event describing the {@link AnnotatedType}
     * being processed; may be {@code null} in which case no action
     * will be taken
     *
     * @see Converter
     *
     * @see Embeddable
     *
     * @see Entity
     *
     * @see MappedSuperclass
     *
     * @see PersistenceUnitInfo#excludeUnlistedClasses()
     */
    private void discoverManagedClasses(@Observes
                                        @WithAnnotations({
                                            Converter.class,
                                            Embeddable.class,
                                            Entity.class,
                                            MappedSuperclass.class
                                        })
                                        final ProcessAnnotatedType<?> event) {
        final String cn = this.getClass().getName();
        final String mn = "discoverManagedClasses";
        final Logger logger = this.getLogger();
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, event);
        }

        if (event != null) {
            final AnnotatedType<?> annotatedType = event.getAnnotatedType();
            if (annotatedType != null && !annotatedType.isAnnotationPresent(Vetoed.class)) {
                this.assignManagedClassToPersistenceUnit(annotatedType.getAnnotations(PersistenceContext.class),
                                                         annotatedType.getAnnotations(PersistenceUnit.class),
                                                         annotatedType.getJavaClass());
                event.veto(); // managed classes can't be beans
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
        }
    }

    /**
     * Given {@link Set}s of {@link PersistenceContext} and {@link
     * PersistenceUnit} annotations that will be used for their {@code
     * unitName} elements only, associates the supplied {@link Class}
     * with the persistence units implied by the annotations.
     *
     * @param persistenceContexts a {@link Set} of {@link
     * PersistenceContext}s whose {@link PersistenceContext#unitName()
     * unitName} elements identify persistence units; may be {@code
     * null} or {@linkplain Collection#isEmpty() empty}
     *
     * @param persistenceUnits a {@link Set} of {@link
     * PersistenceUnit}s whose {@link PersistenceUnit#unitName()
     * unitName} elements identify persistence units; may be {@code
     * null} or {@linkplain Collection#isEmpty() empty}
     *
     * @param c the {@link Class} to associate; may be {@code null} in
     * which case no action will be taken
     *
     * @see PersistenceContext
     *
     * @see PersistenceUnit
     */
    private void assignManagedClassToPersistenceUnit(final Set<? extends PersistenceContext> persistenceContexts,
                                                     final Set<? extends PersistenceUnit> persistenceUnits,
                                                     final Class<?> c) {
        final String cn = this.getClass().getName();
        final String mn = "assignManagedClassToPersistenceUnit";
        final Logger logger = this.getLogger();
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, new Object[] {persistenceContexts, persistenceUnits, c});
        }

        if (c != null) {
            boolean processed = false;
            if (persistenceContexts != null && !persistenceContexts.isEmpty()) {
                for (final PersistenceContext persistenceContext : persistenceContexts) {
                    if (persistenceContext != null) {
                        final String name = persistenceContext.unitName(); // yes, unitName
                        assert name != null;
                        if (!name.isEmpty()) {
                            processed = true;
                            addUnlistedManagedClass(name, c);
                        }
                    }
                }
            }
            if (persistenceUnits != null && !persistenceUnits.isEmpty()) {
                for (final PersistenceUnit persistenceUnit : persistenceUnits) {
                    if (persistenceUnit != null) {
                        final String name = persistenceUnit.unitName(); // yes, unitName
                        assert name != null;
                        if (!name.isEmpty()) {
                            processed = true;
                            addUnlistedManagedClass(name, c);
                        }
                    }
                }
            }
            if (!processed) {
                addUnlistedManagedClass("", c);
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
        }
    }

    /**
     * Given a {@link Class} and a name of a persistence unit,
     * associates the {@link Class} with that persistence unit as a
     * member of its list of governed classes.
     *
     * @param name the name of the persistence unit in question; may
     * be {@code null} in which case the empty string ({@code ""})
     * will be used instead
     *
     * @param managedClass the {@link Class} to associate; may be
     * {@code null} in which case no action will be taken
     *
     * @see PersistenceUnitInfo#getManagedClassNames()
     */
    private void addUnlistedManagedClass(String name, final Class<?> managedClass) {
        final String cn = this.getClass().getName();
        final String mn = "addUnlistedManagedClass";
        final Logger logger = this.getLogger();
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, new Object[] {name, managedClass});
        }

        if (managedClass != null) {
            if (name == null) {
                name = "";
            }
            if (!name.isEmpty()) {
                Set<Class<?>> unlistedManagedClasses = this.unlistedManagedClassesByPersistenceUnitNames.get(name);
                if (unlistedManagedClasses == null) {
                    unlistedManagedClasses = new HashSet<>();
                    this.unlistedManagedClassesByPersistenceUnitNames.put(name, unlistedManagedClasses);
                }
                unlistedManagedClasses.add(managedClass);
            }
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
        }
    }

    /**
     * Converts {@code META-INF/persistence.xml} resources into {@link
     * PersistenceUnitInfo} objects and takes into account any other
     * {@link PersistenceUnitInfo} objects that already exist and
     * ensures that all of them are registered as CDI beans.
     *
     * <p>This allows other CDI-provider-specific mechanisms to use
     * these {@link PersistenceUnitInfo} beans as inputs for creating
     * {@link EntityManager} instances.</p>
     *
     * @param event the {@link AfterBeanDiscovery} event describing
     * the fact that bean discovery has been performed; may be {@code
     * null} in which case no action will be taken
     *
     * @param beanManager the {@link BeanManager} currently in effect;
     * may be {@code null} in which case no action will be taken
     *
     * @exception IOException if an input or output error occurs,
     * typically because a {@code META-INF/persistence.xml} resource
     * was found but could not be loaded for some reason
     *
     * @exception JAXBException if there was a problem {@linkplain
     * Unmarshaller#unmarshal(Reader) unmarshalling} a {@code
     * META-INF/persistence.xml} resource
     *
     * @exception ReflectiveOperationException if reflection failed
     *
     * @exception XMLStreamException if there was a problem setting up
     * JAXB
     *
     * @see PersistenceUnitInfo
     */
    private void afterBeanDiscovery(@Observes @Priority(LIBRARY_AFTER)
                                    final AfterBeanDiscovery event,
                                    final BeanManager beanManager)
        throws IOException, JAXBException, ReflectiveOperationException, XMLStreamException {
        final String cn = this.getClass().getName();
        final String mn = "afterBeanDiscovery";
        final Logger logger = this.getLogger();
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, new Object[] {event, beanManager});
        }

        if (event != null && beanManager != null) {
            final PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
            event.addBean()
                .types(PersistenceProviderResolver.class)
                .scope(Singleton.class)
                .createWith(cc -> resolver);
            final Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
            for (final PersistenceProvider provider : providers) {
                event.addBean()
                    .addTransitiveTypeClosure(provider.getClass())
                    .scope(Singleton.class)
                    .createWith(cc -> provider);
            }
            // Should we consider type-level @PersistenceContext
            // definitions of persistence units?
            boolean processImplicits = true;
            // Collect all pre-existing PersistenceUnitInfo beans and
            // make sure their associated PersistenceProviders are
            // beanified.  (Many times this Set will be empty.)
            final Set<Bean<?>> preexistingPersistenceUnitInfoBeans =
                beanManager.getBeans(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
            if (preexistingPersistenceUnitInfoBeans != null && !preexistingPersistenceUnitInfoBeans.isEmpty()) {
                processImplicits = false;
                for (final Bean<?> preexistingPersistenceUnitInfoBean : preexistingPersistenceUnitInfoBeans) {
                    if (preexistingPersistenceUnitInfoBean != null) {
                        // We use the Bean directly to create a
                        // PersistenceUnitInfo instance.  We need it
                        // only for the return values of
                        // getPersistenceProviderClassName() and
                        // getClassLoader().
                        final Object pui = preexistingPersistenceUnitInfoBean.create(null);
                        if (pui instanceof PersistenceUnitInfo) {
                            maybeAddPersistenceProviderBean(event, (PersistenceUnitInfo) pui, providers);
                        }
                    }
                }
            }
            // Load all META-INF/persistence.xml resources with JAXB,
            // and turn them into PersistenceUnitInfo instances, and
            // add beans for all of them as well as their associated
            // PersistenceProviders (if applicable).
            final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            assert classLoader != null;
            final Enumeration<URL> urls = classLoader.getResources("META-INF/persistence.xml");
            if (urls != null && urls.hasMoreElements()) {
                processImplicits = false;
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
                // ( https://docs.oracle.com/javase/8/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory-- ),
                // IS deprecated in JDK 9
                // ( https://docs.oracle.com/javase/9/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory-- )
                // with a claim that it was deprecated since JDK 1.7, but in
                // JDK 7 it actually was _not_ deprecated
                // ( https://docs.oracle.com/javase/7/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory() )
                // and now in JDK 10 it is NO LONGER deprecated
                // ( https://docs.oracle.com/javase/10/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory() ),
                // nor in JDK 11
                // ( https://docs.oracle.com/en/java/javase/11/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory() ),
                // nor in JDK 12
                // ( https://docs.oracle.com/en/java/javase/12/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory() ).
                // Suppress deprecation warnings since the JDK can't
                // even figure it out!
                @SuppressWarnings("deprecation")
                final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
                assert xmlInputFactory != null;
                final Unmarshaller unmarshaller =
                    JAXBContext.newInstance(Persistence.class.getPackage().getName()).createUnmarshaller();
                assert unmarshaller != null;
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
            if (processImplicits) {
                for (final PersistenceUnitInfoBean persistenceUnitInfoBean : this.implicitPersistenceUnits.values()) {
                    assert persistenceUnitInfoBean != null;
                    final String persistenceUnitName = persistenceUnitInfoBean.getPersistenceUnitName();
                    if (!persistenceUnitInfoBean.excludeUnlistedClasses()) {
                        final Collection<? extends Class<?>> unlistedManagedClasses =
                            this.unlistedManagedClassesByPersistenceUnitNames.get(persistenceUnitName);
                        if (unlistedManagedClasses != null && !unlistedManagedClasses.isEmpty()) {
                            for (final Class<?> unlistedManagedClass : unlistedManagedClasses) {
                                assert unlistedManagedClass != null;
                                persistenceUnitInfoBean.addManagedClassName(unlistedManagedClass.getName());
                            }
                        }
                    }
                    event.addBean()
                        .types(Collections.singleton(PersistenceUnitInfo.class))
                        .scope(Singleton.class)
                        .addQualifiers(NamedLiteral.of(persistenceUnitName == null ? "" : persistenceUnitName))
                        .createWith(cc -> persistenceUnitInfoBean);
                    maybeAddPersistenceProviderBean(event, persistenceUnitInfoBean, providers);
                }
            }
        }
        this.unlistedManagedClassesByPersistenceUnitNames.clear();
        this.implicitPersistenceUnits.clear();

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
        }
    }

    /**
     * Given a {@link PersistenceUnitInfo} and a {@link Collection} of
     * {@link PersistenceProvider} instances representing already
     * "beanified" {@link PersistenceProvider}s, adds a CDI bean for
     * the {@linkplain
     * PersistenceUnitInfo#getPersistenceProviderClassName()
     * persistence provider referenced by the supplied
     * <code>PersistenceUnitInfo</code>} if appropriate.
     *
     * @param event the {@link AfterBeanDiscovery} event that will do
     * the actual bean addition; must not be {@code null}
     *
     * @param persistenceUnitInfo the {@link PersistenceUnitInfo}
     * whose {@linkplain
     * PersistenceUnitInfo#getPersistenceProviderClassName()
     * associated persistence provider} will be beanified; must not be
     * {@code null}
     *
     * @param providers a {@link Collection} of {@link
     * PersistenceProvider} instances that represent {@link
     * PersistenceProvider}s that have already had beans added for
     * them; may be {@code null}
     *
     * @exception NullPointerException if {@code event} or {@code
     * persistenceUnitInfo} is {@code null}
     *
     * @exception ReflectiveOperationException if an error occurs
     * during reflection
     */
    private static void maybeAddPersistenceProviderBean(final AfterBeanDiscovery event,
                                                        final PersistenceUnitInfo persistenceUnitInfo,
                                                        final Collection<? extends PersistenceProvider> providers)
        throws ReflectiveOperationException {
        final String cn = JpaExtension.class.getName();
        final String mn = "maybeAddPersistenceProviderBean";
        final Logger logger = Logger.getLogger(cn);
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, new Object[] {event, persistenceUnitInfo, providers});
        }

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

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
        }
    }

}
