/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

import io.helidon.integrations.cdi.jpa.jaxb.Persistence;
import io.helidon.integrations.cdi.jpa.jaxb.Persistence.PersistenceUnit;
import io.helidon.integrations.cdi.jpa.jaxb.PersistenceUnitCachingType;
import io.helidon.integrations.cdi.jpa.jaxb.PersistenceUnitValidationModeType;

/**
 * A {@link PersistenceUnitInfo} implementation that can be
 * constructed by hand.
 *
 * @see PersistenceUnitInfo
 */
public class PersistenceUnitInfoBean implements PersistenceUnitInfo {


    /*
     * Instance fields.
     */


    private final ClassLoader classLoader;

    private final ClassLoader originalClassLoader;

    private final boolean excludeUnlistedClasses;

    private final List<URL> jarFileUrls;

    private final Set<String> managedClassNames;

    private final List<String> managedClassNamesView;

    private final List<String> mappingFileNames;

    private final String jtaDataSourceName;

    private final String nonJtaDataSourceName;

    private final Supplier<? extends DataSourceProvider> dataSourceProviderSupplier;

    private DataSourceProvider dataSourceProvider;

    private final String persistenceProviderClassName;

    private final String persistenceUnitName;

    private final URL persistenceUnitRootUrl;

    private final String persistenceXMLSchemaVersion;

    private final Properties properties;

    private final SharedCacheMode sharedCacheMode;

    private final Consumer<? super ClassTransformer> classTransformerConsumer;

    private final Supplier<? extends ClassLoader> tempClassLoaderSupplier;

    private final PersistenceUnitTransactionType transactionType;

    private final ValidationMode validationMode;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link PersistenceUnitInfoBean} using as many
     * defaults as reasonably possible.
     *
     * @param persistenceUnitName the name of the persistence unit
     * this {@link PersistenceUnitInfoBean} represents; must not be
     * {@code null}
     *
     * @param persistenceUnitRootUrl the {@link URL} identifying the
     * root of the persistence unit this {@link
     * PersistenceUnitInfoBean} represents; must not be {@code null}
     *
     * @param managedClassNames a {@link Collection} of
     * fully-qualified class names identifying JPA-managed classes
     * (such as entity classes, mapped superclasses and the like); may
     * be {@code null}.  The {@link Collection} is copied and no
     * reference to it is retained.
     *
     * @param dataSourceProvider a {@link DataSourceProvider} capable
     * of supplying {@link DataSource} instances; must not be {@code
     * null}
     *
     * @param properties a {@link Properties} object representing the
     * properties of the persistence unit represented by this {@link
     * PersistenceUnitInfoBean}; may be {@code null}.  A reference is
     * retained to this object.
     *
     * @exception NullPointerException if {@code persistenceUnitName},
     * {@code persistenceUnitRootUrl} or {@code dataSourceProvider} is
     * {@code null}
     *
     * @see #PersistenceUnitInfoBean(String, URL, String, String,
     * ClassLoader, Supplier, Consumer, boolean, Collection,
     * Collection, Collection, String, String, DataSourceProvider,
     * Properties, SharedCacheMode, PersistenceUnitTransactionType,
     * ValidationMode)
     */
    public PersistenceUnitInfoBean(final String persistenceUnitName,
                                   final URL persistenceUnitRootUrl,
                                   final Collection<? extends String> managedClassNames,
                                   final DataSourceProvider dataSourceProvider,
                                   final Properties properties) {
        this(persistenceUnitName,
             persistenceUnitRootUrl,
             null,
             null,
             Thread.currentThread().getContextClassLoader(),
             null,
             null,
             managedClassNames != null && !managedClassNames.isEmpty(),
             null,
             managedClassNames,
             null,
             persistenceUnitName,
             null,
             dataSourceProvider,
             properties,
             SharedCacheMode.UNSPECIFIED,
             PersistenceUnitTransactionType.JTA,
             ValidationMode.AUTO);
    }

    /**
     * Creates a new {@link PersistenceUnitInfoBean} using as many
     * defaults as reasonably possible.
     *
     * @param persistenceUnitName the name of the persistence unit
     * this {@link PersistenceUnitInfoBean} represents; must not be
     * {@code null}
     *
     * @param persistenceUnitRootUrl the {@link URL} identifying the
     * root of the persistence unit this {@link
     * PersistenceUnitInfoBean} represents; must not be {@code null}
     *
     * @param managedClassNames a {@link Collection} of
     * fully-qualified class names identifying JPA-managed classes
     * (such as entity classes, mapped superclasses and the like); may
     * be {@code null}.  The {@link Collection} is copied and no
     * reference to it is retained.
     *
     * @param dataSourceProviderSupplier a {@link Supplier} capable of
     * supplying {@link DataSourceProvider} instances; must not be
     * {@code null}
     *
     * @param properties a {@link Properties} object representing the
     * properties of the persistence unit represented by this {@link
     * PersistenceUnitInfoBean}; may be {@code null}.  A reference is
     * retained to this object.
     *
     * @exception NullPointerException if {@code persistenceUnitName},
     * {@code persistenceUnitRootUrl} or {@code
     * dataSourceProviderSupplier} is {@code null}
     *
     * @see #PersistenceUnitInfoBean(String, URL, String, String,
     * ClassLoader, Supplier, Consumer, boolean, Collection,
     * Collection, Collection, String, String, DataSourceProvider,
     * Properties, SharedCacheMode, PersistenceUnitTransactionType,
     * ValidationMode)
     */
    public PersistenceUnitInfoBean(final String persistenceUnitName,
                                   final URL persistenceUnitRootUrl,
                                   final Collection<? extends String> managedClassNames,
                                   final Supplier<? extends DataSourceProvider> dataSourceProviderSupplier,
                                   final Properties properties) {
        this(persistenceUnitName,
             persistenceUnitRootUrl,
             null,
             null,
             Thread.currentThread().getContextClassLoader(),
             null,
             null,
             managedClassNames != null && !managedClassNames.isEmpty(),
             null,
             managedClassNames,
             null,
             persistenceUnitName,
             null,
             dataSourceProviderSupplier,
             properties,
             SharedCacheMode.UNSPECIFIED,
             PersistenceUnitTransactionType.JTA,
             ValidationMode.AUTO);
    }

    /**
     * Creates a new {@link PersistenceUnitInfoBean}.
     *
     * @param persistenceUnitName the name of the persistence unit
     * this {@link PersistenceUnitInfoBean} represents; must not be
     * {@code null}
     *
     * @param persistenceUnitRootUrl the {@link URL} identifying the
     * root of the persistence unit this {@link
     * PersistenceUnitInfoBean} represents; must not be {@code null}
     *
     * @param persistenceXMLSchemaVersion a {@link String}
     * representation of the version of JPA being supported; may be
     * {@code null} in which case "{@code 2.2}" will be used instead
     *
     * @param persistenceProviderClassName the fully-qualified class
     * name of a {@link PersistenceProvider} implementation; may be
     * {@code null} in which case a default will be used
     *
     * @param classLoader a {@link ClassLoader} to be returned by the
     * {@link #getClassLoader()} method; may be {@code null}
     *
     * @param tempClassLoaderSupplier a {@link Supplier} of {@link
     * ClassLoader} instances to be used by the {@link
     * #getNewTempClassLoader()} method; may be {@code null}
     *
     * @param classTransformerConsumer a {@link Consumer} of any
     * {@link ClassTransformer}s that may be added via a JPA
     * provider's invocation of the {@link
     * #addTransformer(ClassTransformer)} method; may be {@code null}
     * in which case no action will be taken
     *
     * @param excludeUnlistedClasses if {@code true}, then any
     * automatically discovered managed classes not explicitly
     * contained in {@code managedClassNames} will be excluded from
     * consideration
     *
     * @param jarFileUrls a {@link Collection} of {@link URL}s
     * identifying {@code .jar} files containing managed classes; may
     * be {@code null}.  The {@link Collection} is copied and no
     * reference to it is retained.
     *
     * @param managedClassNames a {@link Collection} of
     * fully-qualified class names identifying JPA-managed classes
     * (such as entity classes, mapped superclasses and the like); may
     * be {@code null}.  The {@link Collection} is copied and no
     * reference to it is retained.
     *
     * @param mappingFileNames a {@link Collection} of classpath
     * resource names identifying JPA mapping files; may be {@code
     * null}.  The {@link Collection} is copied and no reference to it
     * is retained.
     *
     * @param jtaDataSourceName the name of a data source that may be
     * enrolled in JTA-compliant transactions; may be {@code null}
     *
     * @param nonJtaDataSourceName the name of a data source that
     * should not be enrolled in JTA-compliant transactions; may be
     * {@code null}
     *
     * @param dataSourceProvider a {@link DataSourceProvider} capable
     * of supplying {@link DataSource} instances; must not be {@code
     * null}
     *
     * @param properties a {@link Properties} object representing the
     * properties of the persistence unit represented by this {@link
     * PersistenceUnitInfoBean}; may be {@code null}.  A reference is
     * retained to this object.
     *
     * @param sharedCacheMode the {@link SharedCacheMode} this {@link
     * PersistenceUnitInfoBean} will use; may be {@code null} in which
     * case {@link SharedCacheMode#UNSPECIFIED} will be used instead
     *
     * @param transactionType the {@link
     * PersistenceUnitTransactionType} this {@link
     * PersistenceUnitInfoBean} will use; may be {@code null} in which
     * case {@link PersistenceUnitTransactionType#JTA} will be used
     * instead
     *
     * @param validationMode the {@link ValidationMode} this {@link
     * PersistenceUnitInfoBean} will use; may be {@code null} in which
     * case {@link ValidationMode#AUTO} will be used instead
     *
     * @exception NullPointerException if {@code persistenceUnitName},
     * {@code persistenceUnitRootUrl} or {@code dataSourceProvider} is
     * {@code null}
     *
     * @see #getPersistenceUnitName()
     *
     * @see #getPersistenceUnitRootUrl()
     *
     * @see #getPersistenceXMLSchemaVersion()
     *
     * @see #getPersistenceProviderClassName()
     *
     * @see #getClassLoader()
     *
     * @see #getNewTempClassLoader()
     *
     * @see #excludeUnlistedClasses()
     *
     * @see #getJarFileUrls()
     *
     * @see #getManagedClassNames()
     *
     * @see #getMappingFileNames()
     *
     * @see #getJtaDataSource()
     *
     * @see #getNonJtaDataSource()
     *
     * @see #getProperties()
     *
     * @see #getSharedCacheMode()
     *
     * @see #getTransactionType()
     *
     * @see #getValidationMode()
     */
    public PersistenceUnitInfoBean(final String persistenceUnitName,
                                   final URL persistenceUnitRootUrl,
                                   final String persistenceXMLSchemaVersion,
                                   final String persistenceProviderClassName,
                                   final ClassLoader classLoader,
                                   final Supplier<? extends ClassLoader> tempClassLoaderSupplier,
                                   final Consumer<? super ClassTransformer> classTransformerConsumer,
                                   final boolean excludeUnlistedClasses,
                                   final Collection<? extends URL> jarFileUrls,
                                   final Collection<? extends String> managedClassNames,
                                   final Collection<? extends String> mappingFileNames,
                                   final String jtaDataSourceName,
                                   final String nonJtaDataSourceName,
                                   final DataSourceProvider dataSourceProvider,
                                   final Properties properties,
                                   final SharedCacheMode sharedCacheMode,
                                   final PersistenceUnitTransactionType transactionType,
                                   final ValidationMode validationMode) {
        this(persistenceUnitName,
             persistenceUnitRootUrl,
             persistenceXMLSchemaVersion,
             persistenceProviderClassName,
             classLoader,
             tempClassLoaderSupplier,
             classTransformerConsumer,
             excludeUnlistedClasses,
             jarFileUrls,
             managedClassNames,
             mappingFileNames,
             jtaDataSourceName,
             nonJtaDataSourceName,
             () -> dataSourceProvider,
             properties,
             sharedCacheMode,
             transactionType,
             validationMode);
    }

    /**
     * Creates a new {@link PersistenceUnitInfoBean}.
     *
     * @param persistenceUnitName the name of the persistence unit
     * this {@link PersistenceUnitInfoBean} represents; must not be
     * {@code null}
     *
     * @param persistenceUnitRootUrl the {@link URL} identifying the
     * root of the persistence unit this {@link
     * PersistenceUnitInfoBean} represents; must not be {@code null}
     *
     * @param persistenceXMLSchemaVersion a {@link String}
     * representation of the version of JPA being supported; may be
     * {@code null} in which case "{@code 2.2}" will be used instead
     *
     * @param persistenceProviderClassName the fully-qualified class
     * name of a {@link PersistenceProvider} implementation; may be
     * {@code null} in which case a default will be used
     *
     * @param classLoader a {@link ClassLoader} to be returned by the
     * {@link #getClassLoader()} method; may be {@code null}
     *
     * @param tempClassLoaderSupplier a {@link Supplier} of {@link
     * ClassLoader} instances to be used by the {@link
     * #getNewTempClassLoader()} method; may be {@code null}
     *
     * @param classTransformerConsumer a {@link Consumer} of any
     * {@link ClassTransformer}s that may be added via a JPA
     * provider's invocation of the {@link
     * #addTransformer(ClassTransformer)} method; may be {@code null}
     * in which case no action will be taken
     *
     * @param excludeUnlistedClasses if {@code true}, then any
     * automatically discovered managed classes not explicitly
     * contained in {@code managedClassNames} will be excluded from
     * consideration
     *
     * @param jarFileUrls a {@link Collection} of {@link URL}s
     * identifying {@code .jar} files containing managed classes; may
     * be {@code null}.  The {@link Collection} is copied and no
     * reference to it is retained.
     *
     * @param managedClassNames a {@link Collection} of
     * fully-qualified class names identifying JPA-managed classes
     * (such as entity classes, mapped superclasses and the like); may
     * be {@code null}.  The {@link Collection} is copied and no
     * reference to it is retained.
     *
     * @param mappingFileNames a {@link Collection} of classpath
     * resource names identifying JPA mapping files; may be {@code
     * null}.  The {@link Collection} is copied and no reference to it
     * is retained.
     *
     * @param jtaDataSourceName the name of a data source that may be
     * enrolled in JTA-compliant transactions; may be {@code null}
     *
     * @param nonJtaDataSourceName the name of a data source that
     * should not be enrolled in JTA-compliant transactions; may be
     * {@code null}
     *
     * @param dataSourceProviderSupplier a {@link Supplier} capable of
     * supplying {@link DataSourceProvider} instances; must not be
     * {@code null}
     *
     * @param properties a {@link Properties} object representing the
     * properties of the persistence unit represented by this {@link
     * PersistenceUnitInfoBean}; may be {@code null}.  A reference is
     * retained to this object.
     *
     * @param sharedCacheMode the {@link SharedCacheMode} this {@link
     * PersistenceUnitInfoBean} will use; may be {@code null} in which
     * case {@link SharedCacheMode#UNSPECIFIED} will be used instead
     *
     * @param transactionType the {@link
     * PersistenceUnitTransactionType} this {@link
     * PersistenceUnitInfoBean} will use; may be {@code null} in which
     * case {@link PersistenceUnitTransactionType#JTA} will be used
     * instead
     *
     * @param validationMode the {@link ValidationMode} this {@link
     * PersistenceUnitInfoBean} will use; may be {@code null} in which
     * case {@link ValidationMode#AUTO} will be used instead
     *
     * @exception NullPointerException if {@code persistenceUnitName},
     * {@code persistenceUnitRootUrl} or {@code dataSourceProvider} is
     * {@code null}
     *
     * @see #getPersistenceUnitName()
     *
     * @see #getPersistenceUnitRootUrl()
     *
     * @see #getPersistenceXMLSchemaVersion()
     *
     * @see #getPersistenceProviderClassName()
     *
     * @see #getClassLoader()
     *
     * @see #getNewTempClassLoader()
     *
     * @see #excludeUnlistedClasses()
     *
     * @see #getJarFileUrls()
     *
     * @see #getManagedClassNames()
     *
     * @see #getMappingFileNames()
     *
     * @see #getJtaDataSource()
     *
     * @see #getNonJtaDataSource()
     *
     * @see #getProperties()
     *
     * @see #getSharedCacheMode()
     *
     * @see #getTransactionType()
     *
     * @see #getValidationMode()
     */
    public PersistenceUnitInfoBean(final String persistenceUnitName,
                                   final URL persistenceUnitRootUrl,
                                   final String persistenceXMLSchemaVersion,
                                   final String persistenceProviderClassName,
                                   final ClassLoader classLoader,
                                   final Supplier<? extends ClassLoader> tempClassLoaderSupplier,
                                   final Consumer<? super ClassTransformer> classTransformerConsumer,
                                   final boolean excludeUnlistedClasses,
                                   final Collection<? extends URL> jarFileUrls,
                                   final Collection<? extends String> managedClassNames,
                                   final Collection<? extends String> mappingFileNames,
                                   final String jtaDataSourceName,
                                   final String nonJtaDataSourceName,
                                   final Supplier<? extends DataSourceProvider> dataSourceProviderSupplier,
                                   final Properties properties,
                                   final SharedCacheMode sharedCacheMode,
                                   final PersistenceUnitTransactionType transactionType,
                                   final ValidationMode validationMode) {
        super();
        Objects.requireNonNull(persistenceUnitName);
        Objects.requireNonNull(persistenceUnitRootUrl);
        Objects.requireNonNull(dataSourceProviderSupplier);
        Objects.requireNonNull(transactionType);

        this.persistenceUnitName = persistenceUnitName;
        this.persistenceUnitRootUrl = persistenceUnitRootUrl;
        this.persistenceProviderClassName = persistenceProviderClassName;
        this.persistenceXMLSchemaVersion = persistenceXMLSchemaVersion == null ? "2.2" : persistenceXMLSchemaVersion;
        this.originalClassLoader = classLoader;
        this.classLoader = classLoader;
        this.tempClassLoaderSupplier = tempClassLoaderSupplier;
        this.classTransformerConsumer = classTransformerConsumer;
        this.excludeUnlistedClasses = excludeUnlistedClasses;

        if (jarFileUrls == null || jarFileUrls.isEmpty()) {
            this.jarFileUrls = Collections.emptyList();
        } else {
            this.jarFileUrls = Collections.unmodifiableList(new ArrayList<>(jarFileUrls));
        }

        if (managedClassNames == null || managedClassNames.isEmpty()) {
            this.managedClassNames = new LinkedHashSet<>();
        } else {
            this.managedClassNames = new LinkedHashSet<>(managedClassNames);
        }
        this.managedClassNamesView = new AbstractList<String>() {
                @Override
                public boolean isEmpty() {
                    return PersistenceUnitInfoBean.this.managedClassNames.isEmpty();
                }
                @Override
                public int size() {
                    return PersistenceUnitInfoBean.this.managedClassNames.size();
                }
                @Override
                public Iterator<String> iterator() {
                    return PersistenceUnitInfoBean.this.managedClassNames.iterator();
                }
                @Override
                public String get(final int index) {
                    final Iterator<String> iterator = this.iterator();
                    assert iterator != null;
                    for (int i = 0; i < index; i++) {
                        iterator.next();
                    }
                    return iterator.next();
                }
            };

        if (mappingFileNames == null || mappingFileNames.isEmpty()) {
            this.mappingFileNames = Collections.emptyList();
        } else {
            this.mappingFileNames = Collections.unmodifiableList(new ArrayList<>(mappingFileNames));
        }

        if (properties == null) {
            this.properties = new Properties();
        } else {
            this.properties = properties;
        }

        if (jtaDataSourceName == null || jtaDataSourceName.isEmpty()) {
            this.jtaDataSourceName = null;
        } else {
            this.jtaDataSourceName = jtaDataSourceName;
        }
        this.nonJtaDataSourceName = nonJtaDataSourceName;
        this.dataSourceProviderSupplier = dataSourceProviderSupplier;

        if (sharedCacheMode == null) {
            this.sharedCacheMode = SharedCacheMode.UNSPECIFIED;
        } else {
            this.sharedCacheMode = sharedCacheMode;
        }
        this.transactionType = transactionType;
        if (validationMode == null) {
            this.validationMode = ValidationMode.AUTO;
        } else {
            this.validationMode = validationMode;
        }
    }


    /*
     * Instance methods.
     */


    boolean addManagedClassName(final String className) {
        return className != null && this.managedClassNames.add(className);
    }

    @Override
    public List<URL> getJarFileUrls() {
        return this.jarFileUrls;
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return this.persistenceUnitRootUrl;
    }

    @Override
    public List<String> getManagedClassNames() {
        return this.managedClassNamesView;
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return this.excludeUnlistedClasses;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return this.sharedCacheMode;
    }

    @Override
    public ValidationMode getValidationMode() {
        return this.validationMode;
    }

    @Override
    public Properties getProperties() {
        return this.properties;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return this.persistenceXMLSchemaVersion;
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        ClassLoader cl = null;
        if (this.tempClassLoaderSupplier != null) {
            cl = this.tempClassLoaderSupplier.get();
        }
        if (cl == null) {
            cl = this.originalClassLoader;
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) {
                    cl = this.getClass().getClassLoader();
                }
            }
        }
        return cl;
    }

    @Override
    public void addTransformer(final ClassTransformer classTransformer) {
        if (this.classTransformerConsumer != null) {
            this.classTransformerConsumer.accept(classTransformer);
        }
    }

    @Override
    public String getPersistenceUnitName() {
        return this.persistenceUnitName;
    }

    @Override
    public String getPersistenceProviderClassName() {
        return this.persistenceProviderClassName;
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return this.transactionType;
    }

    @Override
    public final DataSource getJtaDataSource() {
        DataSourceProvider dataSourceProvider = this.dataSourceProvider;
        if (dataSourceProvider == null) {
            dataSourceProvider = this.dataSourceProviderSupplier.get();
            this.dataSourceProvider = dataSourceProvider;
        }
        return dataSourceProvider.getDataSource(true, this.nonJtaDataSourceName == null, this.jtaDataSourceName);
    }

    @Override
    public final DataSource getNonJtaDataSource() {
        DataSourceProvider dataSourceProvider = this.dataSourceProvider;
        if (dataSourceProvider == null) {
            dataSourceProvider = this.dataSourceProviderSupplier.get();
            this.dataSourceProvider = dataSourceProvider;
        }
        return dataSourceProvider.getDataSource(false, false, this.nonJtaDataSourceName);
    }

    @Override
    public List<String> getMappingFileNames() {
        return this.mappingFileNames;
    }

    @Override
    public String toString() {
        return this.getPersistenceUnitName() + " (" + this.getPersistenceUnitRootUrl() + ")";
    }


    /*
     * Static methods.
     */

    /**
     * Given a {@link PersistenceUnit} (a Java object representation
     * of a {@code <persistence-unit>} element in a {@code
     * META-INF/persistence.xml} resource), a {@link URL} representing
     * the persistence unit's root, a {@link Map} of unlisted managed
     * classes (entity classes, mapped superclasses and so on) indexed
     * by persistence unit name, and a {@link DataSourceProvider} that
     * can supply {@link DataSource} instances, returns a {@link
     * PersistenceUnitInfoBean} representing the persistence unit in
     * question.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>This method calls the {@link
     * #fromPersistenceUnit(Persistence.PersistenceUnit, ClassLoader,
     * Supplier, URL, Map, DataSourceProvider)} method using the
     * return value of the {@link Thread#getContextClassLoader()}
     * method as the {@link ClassLoader}.</p>
     *
     * @param persistenceUnit a {@link PersistenceUnit}; must not be
     * {@code null}
     *
     * @param rootUrl the {@link URL} representing the root of the
     * persistence unit; must not be {@code null}
     *
     * @param unlistedClasses a {@link Map} of managed classes indexed
     * by persistence unit name whose values might not be explicitly
     * listed in the supplied {@link PersistenceUnit}; may be {@code
     * null}
     *
     * @param dataSourceProvider a {@link DataSourceProvider}; must not
     * be {@code null}
     *
     * @return a non-{@code null} {@link PersistenceUnitInfoBean}
     *
     * @exception MalformedURLException if a {@link URL} could not be
     * constructed
     *
     * @exception NullPointerException if {@code persistenceUnit},
     * {@code rootUrl} or {@code dataSourceProvider} is {@code null}
     *
     * @see #fromPersistenceUnit(Persistence.PersistenceUnit,
     * ClassLoader, Supplier, URL, Map, DataSourceProvider)
     *
     * @see PersistenceUnit
     *
     * @see PersistenceUnitInfo
     */
    public static final PersistenceUnitInfoBean
        fromPersistenceUnit(final PersistenceUnit persistenceUnit,
                            final URL rootUrl,
                            final Map<? extends String, ? extends Set<? extends Class<?>>> unlistedClasses,
                            final DataSourceProvider dataSourceProvider)
        throws MalformedURLException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return fromPersistenceUnit(persistenceUnit,
                                   classLoader,
                                   () -> classLoader,
                                   rootUrl,
                                   unlistedClasses,
                                   () -> dataSourceProvider);
    }

    /**
     * Given a {@link PersistenceUnit} (a Java object representation
     * of a {@code <persistence-unit>} element in a {@code
     * META-INF/persistence.xml} resource), a {@link URL} representing
     * the persistence unit's root, a {@link Map} of unlisted managed
     * classes (entity classes, mapped superclasses and so on) indexed
     * by persistence unit name, and a {@code
     * DataSourceProviderSupplier} that can supply {@code
     * DataSourceProvider} instances, returns a {@link
     * PersistenceUnitInfoBean} representing the persistence unit in
     * question.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param persistenceUnit a {@link PersistenceUnit}; must not be
     * {@code null}
     *
     * @param rootUrl the {@link URL} representing the root of the
     * persistence unit; must not be {@code null}
     *
     * @param unlistedClasses a {@link Map} of managed classes indexed
     * by persistence unit name whose values might not be explicitly
     * listed in the supplied {@link PersistenceUnit}; may be {@code
     * null}
     *
     * @param dataSourceProviderSupplier a {@link Supplier} capable of
     * supplying {@link DataSourceProvider} instances; must not be
     * {@code null}
     *
     * @return a non-{@code null} {@link PersistenceUnitInfoBean}
     *
     * @exception MalformedURLException if a {@link URL} could not be
     * constructed
     *
     * @exception NullPointerException if {@code persistenceUnit},
     * {@code rootUrl} or {@code dataSourceProviderSupplier} is {@code null}
     */
    public static final PersistenceUnitInfoBean
        fromPersistenceUnit(final PersistenceUnit persistenceUnit,
                            final URL rootUrl,
                            final Map<? extends String, ? extends Set<? extends Class<?>>> unlistedClasses,
                            final Supplier<? extends DataSourceProvider> dataSourceProviderSupplier)
        throws MalformedURLException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return fromPersistenceUnit(persistenceUnit,
                                   classLoader,
                                   () -> classLoader,
                                   rootUrl,
                                   unlistedClasses,
                                   dataSourceProviderSupplier);
    }

    /**
     * Given a {@link PersistenceUnit} (a Java object representation
     * of a {@code <persistence-unit>} element in a {@code
     * META-INF/persistence.xml} resource), a {@link ClassLoader} for
     * loading JPA classes and resources, a {@link Supplier} of {@link
     * ClassLoader} instances for helping to implement the {@link
     * PersistenceUnitInfo#getNewTempClassLoader()} method, a {@link
     * URL} representing the persistence unit's root, a {@link Map} of
     * unlisted managed classes (entity classes, mapped superclasses
     * and so on) indexed by persistence unit name, and a {@link
     * DataSourceProvider} that can provide {@link DataSource}
     * instances, returns a {@link PersistenceUnitInfoBean}
     * representing the persistence unit in question.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param persistenceUnit a {@link PersistenceUnit}; must not be
     * {@code null}
     *
     * @param classLoader a {@link ClassLoader} that the resulting
     * {@link PersistenceUnitInfoBean} will use; may be {@code null}
     *
     * @param tempClassLoaderSupplier a {@link Supplier} of a {@link
     * ClassLoader} that will be used to implement the {@link
     * PersistenceUnitInfo#getNewTempClassLoader()} method; may be
     * {@code null}
     *
     * @param rootUrl the {@link URL} representing the root of the
     * persistence unit; must not be {@code null}
     *
     * @param unlistedClasses a {@link Map} of managed classes indexed
     * by persistence unit name whose values might not be explicitly
     * listed in the supplied {@link PersistenceUnit}; may be {@code
     * null}
     *
     * @param dataSourceProvider a {@link DataSourceProvider}; must
     * not be {@code null}
     *
     * @return a non-{@code null} {@link PersistenceUnitInfoBean}
     *
     * @exception MalformedURLException if a {@link URL} could not be
     * constructed
     *
     * @exception NullPointerException if {@code persistenceUnit} or
     * {@code rootUrl} is {@code null}
     *
     * @see PersistenceUnit
     *
     * @see PersistenceUnitInfo
     */
    public static final PersistenceUnitInfoBean
        fromPersistenceUnit(final PersistenceUnit persistenceUnit,
                            final ClassLoader classLoader,
                            Supplier<? extends ClassLoader> tempClassLoaderSupplier,
                            final URL rootUrl,
                            final Map<? extends String, ? extends Set<? extends Class<?>>> unlistedClasses,
                            final DataSourceProvider dataSourceProvider)
        throws MalformedURLException {
        return fromPersistenceUnit(persistenceUnit,
                                   classLoader,
                                   tempClassLoaderSupplier,
                                   rootUrl,
                                   unlistedClasses,
                                   () -> dataSourceProvider);
    }

    /**
     * Given a {@link PersistenceUnit} (a Java object representation
     * of a {@code <persistence-unit>} element in a {@code
     * META-INF/persistence.xml} resource), a {@link ClassLoader} for
     * loading JPA classes and resources, a {@link Supplier} of {@link
     * ClassLoader} instances for helping to implement the {@link
     * PersistenceUnitInfo#getNewTempClassLoader()} method, a {@link
     * URL} representing the persistence unit's root, a {@link Map} of
     * unlisted managed classes (entity classes, mapped superclasses
     * and so on) indexed by persistence unit name, and a {@link
     * DataSourceProvider} that can provide {@link DataSource}
     * instances, returns a {@link PersistenceUnitInfoBean}
     * representing the persistence unit in question.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param persistenceUnit a {@link PersistenceUnit}; must not be
     * {@code null}
     *
     * @param classLoader a {@link ClassLoader} that the resulting
     * {@link PersistenceUnitInfoBean} will use; may be {@code null}
     *
     * @param tempClassLoaderSupplier a {@link Supplier} of a {@link
     * ClassLoader} that will be used to implement the {@link
     * PersistenceUnitInfo#getNewTempClassLoader()} method; may be
     * {@code null}
     *
     * @param rootUrl the {@link URL} representing the root of the
     * persistence unit; must not be {@code null}
     *
     * @param unlistedClasses a {@link Map} of managed classes indexed
     * by persistence unit name whose values might not be explicitly
     * listed in the supplied {@link PersistenceUnit}; may be {@code
     * null}
     *
     * @param dataSourceProviderSupplier a {@link Supplier} of {@link
     * DataSourceProvider} instances; must not be {@code null}
     *
     * @return a non-{@code null} {@link PersistenceUnitInfoBean}
     *
     * @exception MalformedURLException if a {@link URL} could not be
     * constructed
     *
     * @exception NullPointerException if {@code persistenceUnit},
     * {@code rootUrl} or {@code dataSourceProviderSupplier} is {@code
     * null}
     *
     * @see PersistenceUnit
     *
     * @see PersistenceUnitInfo
     */
    public static final PersistenceUnitInfoBean
        fromPersistenceUnit(final PersistenceUnit persistenceUnit,
                            final ClassLoader classLoader,
                            Supplier<? extends ClassLoader> tempClassLoaderSupplier,
                            final URL rootUrl,
                            final Map<? extends String, ? extends Set<? extends Class<?>>> unlistedClasses,
                            final Supplier<? extends DataSourceProvider> dataSourceProviderSupplier)
        throws MalformedURLException {
        Objects.requireNonNull(persistenceUnit);
        Objects.requireNonNull(rootUrl);
        Objects.requireNonNull(dataSourceProviderSupplier);

        final Collection<? extends String> jarFiles = persistenceUnit.getJarFile();
        final List<URL> jarFileUrls = new ArrayList<>();
        for (final String jarFile : jarFiles) {
            if (jarFile != null) {
                jarFileUrls.add(createJarFileURL(rootUrl, jarFile));
            }
        }

        final Collection<? extends String> mappingFiles = persistenceUnit.getMappingFile();

        final Properties properties = new Properties();
        final PersistenceUnit.Properties persistenceUnitProperties = persistenceUnit.getProperties();
        if (persistenceUnitProperties != null) {
            final Collection<? extends PersistenceUnit.Properties.Property> propertyInstances =
                persistenceUnitProperties.getProperty();
            if (propertyInstances != null && !propertyInstances.isEmpty()) {
                for (final PersistenceUnit.Properties.Property property : propertyInstances) {
                    assert property != null;
                    properties.setProperty(property.getName(), property.getValue());
                }
            }
        }

        final Collection<String> managedClasses = persistenceUnit.getClazz();
        assert managedClasses != null;
        String name = persistenceUnit.getName();
        if (name == null || name.isEmpty()) {
            name = JpaExtension.DEFAULT_PERSISTENCE_UNIT_NAME;
        }

        final Boolean excludeUnlistedClasses = persistenceUnit.isExcludeUnlistedClasses();
        if (!Boolean.TRUE.equals(excludeUnlistedClasses)
            && !unlistedClasses.isEmpty()
            && !Boolean.TRUE.equals(persistenceUnit.isExcludeUnlistedClasses())) {
            Set<? extends Class<?>> myUnlistedClasses = unlistedClasses.get(name);
            if (myUnlistedClasses != null && !myUnlistedClasses.isEmpty()) {
                for (final Class<?> unlistedClass : myUnlistedClasses) {
                    if (unlistedClass != null) {
                        managedClasses.add(unlistedClass.getName());
                    }
                }
            }
            // Also add "default" ones
            myUnlistedClasses = unlistedClasses.get(JpaExtension.DEFAULT_PERSISTENCE_UNIT_NAME);
            if (myUnlistedClasses != null && !myUnlistedClasses.isEmpty()) {
                for (final Class<?> unlistedClass : myUnlistedClasses) {
                    if (unlistedClass != null) {
                        managedClasses.add(unlistedClass.getName());
                    }
                }
            }
        }

        final SharedCacheMode sharedCacheMode;
        final PersistenceUnitCachingType persistenceUnitCachingType = persistenceUnit.getSharedCacheMode();
        if (persistenceUnitCachingType == null) {
            sharedCacheMode = SharedCacheMode.UNSPECIFIED;
        } else {
            sharedCacheMode = SharedCacheMode.valueOf(persistenceUnitCachingType.name());
        }

        final PersistenceUnitTransactionType transactionType;
        final io.helidon.integrations.cdi.jpa.jaxb.PersistenceUnitTransactionType persistenceUnitTransactionType =
            persistenceUnit.getTransactionType();
        if (persistenceUnitTransactionType == null) {
            transactionType = PersistenceUnitTransactionType.JTA;
        } else {
            transactionType = PersistenceUnitTransactionType.valueOf(persistenceUnitTransactionType.name());
        }

        final ValidationMode validationMode;
        final PersistenceUnitValidationModeType validationModeType = persistenceUnit.getValidationMode();
        if (validationModeType == null) {
            validationMode = ValidationMode.AUTO;
        } else {
            validationMode = ValidationMode.valueOf(validationModeType.name());
        }

        if (tempClassLoaderSupplier == null) {
            tempClassLoaderSupplier = () -> {
                if (classLoader instanceof URLClassLoader) {
                    return new URLClassLoader(((URLClassLoader) classLoader).getURLs());
                } else {
                    return classLoader;
                }
            };
        }

        final PersistenceUnitInfoBean returnValue =
            new PersistenceUnitInfoBean(name,
                                        rootUrl,
                                        "2.2",
                                        persistenceUnit.getProvider(),
                                        classLoader,
                                        tempClassLoaderSupplier,
                                        null, // no consuming of ClassTransformer for now
                                        excludeUnlistedClasses == null ? true : excludeUnlistedClasses,
                                        jarFileUrls,
                                        managedClasses,
                                        mappingFiles,
                                        persistenceUnit.getJtaDataSource(),
                                        persistenceUnit.getNonJtaDataSource(),
                                        dataSourceProviderSupplier,
                                        properties,
                                        sharedCacheMode,
                                        transactionType,
                                        validationMode);
        return returnValue;
    }

    private static URL createJarFileURL(final URL persistenceUnitRootUrl, final String jarFileUrlString)
        throws MalformedURLException {
        Objects.requireNonNull(persistenceUnitRootUrl);
        Objects.requireNonNull(jarFileUrlString);
        // Revisit: probably won't work if persistenceUnitRootUrl is, say, a jar URL
        final URL returnValue = new URL(persistenceUnitRootUrl, jarFileUrlString);
        return returnValue;
    }


    /*
     * Inner and nested classes.
     */


    /**
     * A {@linkplain FunctionalInterface functional interface}
     * indicating that its implementations can supply {@link
     * DataSource}s.
     *
     * @see #getDataSource(boolean, boolean, String)
     */
    @FunctionalInterface
    public interface DataSourceProvider {

        /**
         * Supplies a {@link DataSource}.
         *
         * <p>Implementations of this method are permitted to return
         * {@code null}.</p>
         *
         * @param jta if {@code true}, the {@link DataSource} that is
         * returned may be enrolled in JTA-compliant transactions
         *
         * @param useDefaultJta if {@code true}, and if the {@code
         * jta} parameter value is {@code true}, the supplied {@code
         * dataSourceName} may be ignored and a default {@link
         * DataSource} eligible for enrolling in JTA-compliant
         * transactions will be returned if possible
         *
         * @param dataSourceName the name of the {@link DataSource} to
         * return; may be {@code null}; ignored if both {@code jta}
         * and {@code useDefaultJta} are {@code true}
         *
         * @return an appropriate {@link DataSource}, or {@code null}
         *
         * @see PersistenceUnitInfoBean#getJtaDataSource()
         *
         * @see PersistenceUnitInfoBean#getNonJtaDataSource()
         */
        DataSource getDataSource(boolean jta, boolean useDefaultJta, String dataSourceName);

    }

}
