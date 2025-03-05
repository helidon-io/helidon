/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.jakarta.persistence;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.AbstractList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import io.helidon.data.api.DataException;

import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;

/**
 * Temporary replacement of Jakarta Persistence 3.2 {@code EntityManagerFactory} initialization
 * while Helidon depends on Jakarta Persistence 3.1.
 */
class PersistenceUnitInfoImpl implements PersistenceUnitInfo {

    private final PersistenceConfiguration config;

    private final ClassLoader classLoader;

    private final ClassLoader originalClassLoader;

    private final boolean excludeUnlistedClasses;

    private final List<URL> jarFileUrls;

    private final Set<String> managedClassNames;

    private final List<String> managedClassNamesView;

    private final List<String> mappingFileNames;

    private final URL persistenceUnitRootUrl;

    private final String persistenceXMLSchemaVersion;

    private final SharedCacheMode sharedCacheMode;

    private final Consumer<? super ClassTransformer> classTransformerConsumer;

    private final Supplier<? extends ClassLoader> tempClassLoaderSupplier;

    private final Properties properties;

    @SuppressWarnings("removal")
    private final PersistenceUnitTransactionType transactionType;

    private final ValidationMode validationMode;

    PersistenceUnitInfoImpl(PersistenceConfiguration config) {
        this.config = config;
        managedClassNames = config.managedClasses()
                .stream()
                .map(Class::getName)
                .collect(Collectors.toSet());
        excludeUnlistedClasses = true;
        // Translate PersistenceUnitTransactionType from temporary enum
        switch (config.transactionType()) {
            case JTA:
                transactionType = PersistenceUnitTransactionType.JTA;
                break;
            case RESOURCE_LOCAL:
                transactionType = PersistenceUnitTransactionType.RESOURCE_LOCAL;
                break;
            default:
                throw new UnsupportedOperationException("Unknown PersistenceUnitTransactionType "
                                                                + config.transactionType().name());
        }
        properties = new Properties(config.properties().size());
        properties.putAll(config.properties());
        // Default values
        classLoader = Thread.currentThread().getContextClassLoader();
        originalClassLoader = classLoader;
        tempClassLoaderSupplier = null;
        classTransformerConsumer = null;
        jarFileUrls = Collections.emptyList();
        sharedCacheMode = SharedCacheMode.UNSPECIFIED;
        validationMode = ValidationMode.AUTO;
        mappingFileNames = Collections.emptyList();
        managedClassNamesView = new AbstractList<String>() {
            @Override
            public boolean isEmpty() {
                return managedClassNames.isEmpty();
            }
            @Override
            public int size() {
                return managedClassNames.size();
            }
            @Override
            public Iterator<String> iterator() {
                return managedClassNames.iterator();
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
        persistenceXMLSchemaVersion = null;
        String classSuffix = DataJpaSupport.class.getName().replaceAll("\\.", "/") + ".class";
        try {
            persistenceUnitRootUrl = computePURootURL(
                    DataJpaSupport.class.getResource(DataJpaSupport.class.getSimpleName() + ".class"),
                    classSuffix);
        } catch (IOException | URISyntaxException ex) {
            throw new DataException("Could not build persistence unit URL", ex);
        }

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
        return config.name();
    }

    @Override
    public String getPersistenceProviderClassName() {
        return config.provider();
    }

    // Jakarta Persistence 3.2 @Override
    public String getScopeAnnotationName() {
        return null;
    }

    // Jakarta Persistence 3.2 @Override
    public List<String> getQualifierAnnotationNames() {
        return List.of();
    }

    @Override
    @SuppressWarnings("removal")
    public PersistenceUnitTransactionType getTransactionType() {
        return this.transactionType;
    }

    @Override
    public final DataSource getJtaDataSource() {
        return null;
    }

    @Override
    public final DataSource getNonJtaDataSource() {
        return null;
    }

    @Override
    public List<String> getMappingFileNames() {
        return this.mappingFileNames;
    }

    @Override
    public String toString() {
        return this.getPersistenceUnitName() + " (" + this.getPersistenceUnitRootUrl() + ")";
    }


    /**
     * Determine the URL path to the persistence unit.
     *
     * @param pxmlURL URL of a resource belonging to the PU (obtained for
     *                {@code descriptorLocation} via {@code Classloader.getResource(String)}).
     * @param descriptorLocation the name of the resource.
     * @return The URL of the PU root containing the resource.
     * @throws IllegalStateException if the resolved root doesn't conform to the JPA specification (8.2)
     */
    public static URL computePURootURL(URL pxmlURL, String descriptorLocation) throws IOException, URISyntaxException {
        StringTokenizer tokenizer = new StringTokenizer(descriptorLocation, "/\\");
        int descriptorDepth = tokenizer.countTokens() - 1;
        URL result;
        String protocol = pxmlURL.getProtocol();
        if ("file".equals(protocol)) { // NOI18N
            StringBuilder path = new StringBuilder();
            boolean firstElement = true;
            for (int i = 0; i < descriptorDepth; i++) {
                if (!firstElement) {
                    path.append("/"); // 315097 URL use standard separators
                }
                path.append("..");
                firstElement = false;
            }
            // e.g. file:/tmp/META-INF/persistence.xml
            // 210280: any file url will be assumed to always reference a file (not a directory)
            result = new URL(pxmlURL, path.toString()); // NOI18N
        } else if ("zip".equals(protocol) || "jar".equals(protocol) || "wsjar".equals(protocol)) {
            // e.g. file:/foo/bar.jar!/META-INF/persistence.xml
            // "zip:" URLs require additional handling.
            String spec = "zip".equals(protocol)
                    ? "file:" + pxmlURL.getFile()
                    : pxmlURL.getFile();

            // Warning: if we ever support nested archive URLs here, make sure
            // that we get the entry in the *innermost* archive.
            int separator = spec.lastIndexOf("!/");

            // It could be possible for a "zip:" or "wsjar:" URL to not have
            // an entry! In that case we take the root of the archive.
            String file = separator == -1 ? spec : spec.substring(0, separator);
            String entry = separator == -1 ? "" : spec.substring(separator + 2);

            // The jar file or directory whose META-INF directory contains
            // the persistence.xml file is termed the root of the persistence
            // unit. (JPA Spec, 8.2)
            if (!entry.endsWith(descriptorLocation)) {
                // Shouldn't happen unless we have a particularly tricky
                // classloader - which we're not obligated to support.
                throw new IllegalStateException(
                        String.format("Invalid persistence root URL %s with descriptor %s", pxmlURL, descriptorLocation));
            }

            String rootEntry = entry.substring(0, entry.length() - descriptorLocation.length());

            // "wsjar:" URLs always have an entry for historical reasons.
            result = !rootEntry.isEmpty() || "wsjar".equals(protocol)
                    ? new URL("jar:" + file + "!/" + rootEntry)
                    : new URL(file);

            // Since EclipseLink is a reference implementation, let's validate
            // the produced root!
            if (!isValidRootInArchive(file, rootEntry)) {
                throw new IllegalStateException(
                        String.format("Invalid persistence root URL %s with descriptor %s", pxmlURL, descriptorLocation));
            }

        } else if ("bundleentry".equals(protocol)) {
            // mkeith - add bundle protocol cases
            result = new URL("bundleentry://" + pxmlURL.getAuthority());
        } else if ("bundleresource".equals(protocol)) {
            result = new URL("bundleresource://" + pxmlURL.getAuthority());
        } else {
            StringBuilder path = new StringBuilder();
            path.append("../".repeat(Math.max(0, descriptorDepth))); // 315097 URL use standard separators
            // some other protocol
            result = new URL(pxmlURL, path.toString()); // NOI18N
        }
        result = fixUNC(result);
        return result;
    }

    private static final String WEBINF_CLASSES_STR = "WEB-INF/classes/";

    private static boolean isValidRootInArchive(String file, String rootEntry) {
        String extension = file.substring(Math.max(0, file.length() - 4));
        if (extension.equalsIgnoreCase(".jar")) {
            // For a JAR, the root can only be the archive itself.
            return rootEntry.isEmpty();
        } else if (extension.equalsIgnoreCase(".war")) {
            // For a WAR, the root can be:
            // 1. WEB-INF/classes
            // 2. One of a JARs inside the WEB-INF/lib
            // In the second case rootEntry is the entry in the innermost
            // archive, and file is the URL of that archive. Since
            // the innermost archive has to be a JAR (according to JPA Spec),
            // this case is handled by the previous branch.
            return rootEntry.equals(WEBINF_CLASSES_STR) || rootEntry.isEmpty();
        } else {
            return false;
        }
    }

    private static URL fixUNC(URL url) throws URISyntaxException, MalformedURLException, UnsupportedEncodingException {
        String protocol = url.getProtocol();
        if (!"file".equalsIgnoreCase(protocol)) {
            return url;
        }
        String authority = url.getAuthority();
        String file = url.getFile();
        if (authority != null) {
//            AbstractSessionLog.getLog().finer(
//                    "fixUNC: before fixing: url = " + url + ", authority = " + authority + ", file = " + file);
            assert (url.getPort() == -1);

            // See GlassFish issue https://glassfish.dev.java.net/issues/show_bug.cgi?id=3209 and
            // JDK issue http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6585937
            // When there is UNC path in classpath, the classloader.getResource
            // returns a file: URL with an authority component in it.
            // The URL looks like this:
            // file://ahost/afile.
            // Interestingly, authority and file components for the above URL
            // are either "ahost" and "/afile" or "" and "//ahost/afile" depending on
            // how the URL is obtained. If classpath is set as a jar with UNC,
            // the former is true, if the classpath is set as a directory with UNC,
            // the latter is true.
            String prefix = "";
            if (!authority.isEmpty()) {
                prefix = "////";
            } else if (file.startsWith("//")) {
                prefix = "//";
            }
            file = prefix.concat(authority).concat(file);
            url = new URL(protocol, null, file);
//            AbstractSessionLog.getLog().finer(
//                    "fixUNC: after fixing: url = " + url + ", authority = " + url.getAuthority() + ", file = " + url.getFile());
        }
        return url;
    }

}
