/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.arquillian;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import io.helidon.config.mp.MpConfigSources;

import jakarta.enterprise.inject.ResolutionException;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Implementation of DeployableContainer for launching Helidon microprofile server.
 *
 * This implementation works by starting a Helidon Server for each deployment. The {@link #start()} and
 * {@link #stop()} methods are no-ops. When Arquillian invokes the {@link #deploy(Archive)} method:
 * <ol>
 * <li>A temporary directory is created</li>
 * <li>The WebArchive contents are written to the temporary directory</li>
 * <li>beans.xml is created in WEB-INF/classes if not present</li>
 * <li>WEB-INF/beans.xml will be moved to WEB-INF/classes/META-INF if present</li>
 * <li>The server is started with WEB-INF/classes and all libraries in WEB-INF/lib on the classpath.</li>
 * </ol>
 *
 * Control is then returned to the test harness (in this case, generally testng) to run tests over HTTP.
 *
 * A test client has to provide arquillian with an arquillian.xml file. The properties in this file are
 * loaded automatically into the {@link HelidonContainerConfiguration} instance that Arquillian manages
 * and supplies to this class.
 */
public class HelidonDeployableContainer implements DeployableContainer<HelidonContainerConfiguration> {
    private static final System.Logger LOGGER = System.getLogger(HelidonDeployableContainer.class.getName());
    // runnables that must be executed on stop
    private static final ConcurrentLinkedQueue<Runnable> STOP_RUNNABLES = new ConcurrentLinkedQueue<>();

    public List<Archive<?>> getAdditionalArchives() {
        return additionalArchives;
    }

    public HelidonContainerConfiguration getContainerConfig() {
        return containerConfig;
    }

    private final List<Archive<?>> additionalArchives = new ArrayList<>();

    /**
     * The configuration for this container.
     */
    private HelidonContainerConfiguration containerConfig;
    private Pattern excludedLibrariesPattern;

    /**
     * Run contexts - kept for each deployment.
     */
    private final Map<String, RunContext> contexts = new HashMap<>();

    /**
     * Annotation literal to inject base registry.
     */
    static class BaseRegistryTypeLiteral extends AnnotationLiteral<RegistryType> implements RegistryType {

        @Override
        public MetricRegistry.Type type() {
            return MetricRegistry.Type.BASE;
        }
    }

    /**
     * Annotation literal to inject base registry.
     */
    static class VendorRegistryTypeLiteral extends AnnotationLiteral<RegistryType> implements RegistryType {

        @Override
        public MetricRegistry.Type type() {
            return MetricRegistry.Type.VENDOR;
        }
    }

    /**
     * Annotation literal to inject base registry.
     */
    static class ApplicationRegistryTypeLiteral extends AnnotationLiteral<RegistryType> implements RegistryType {

        @Override
        public MetricRegistry.Type type() {
            return MetricRegistry.Type.APPLICATION;
        }
    }

    @Override
    public Class<HelidonContainerConfiguration> getConfigurationClass() {
        return HelidonContainerConfiguration.class;
    }

    @Override
    public void setup(HelidonContainerConfiguration configuration) {
        this.containerConfig = configuration;
        String excludeArchivePattern = configuration.getExcludeArchivePattern();
        if (excludeArchivePattern == null || excludeArchivePattern.isBlank()) {
            this.excludedLibrariesPattern = null;
        } else {
            this.excludedLibrariesPattern = Pattern.compile(excludeArchivePattern);
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription(HelidonLocalProtocol.PROTOCOL_NAME);
    }

    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        if (containerConfig.isMultipleDeployments() || (!containerConfig.isMultipleDeployments() && contexts.isEmpty())) {
            // Because helidon doesn't have a dynamic war deployment model, we need to actually start the server here.
            RunContext context = new RunContext();
            contexts.put(archive.getId(), context);

            // Is it a JavaArchive?
            boolean isJavaArchive = archive instanceof JavaArchive;

            try {
                // Create the temporary deployment directory.
                if (containerConfig.getUseRelativePath()) {
                    context.deployDir = Paths.get("target/helidon-arquillian-test");
                } else {
                    context.deployDir = Files.createTempDirectory("helidon-arquillian-test");
                }
                LOGGER.log(Level.INFO, "Running Arquillian tests in directory: " + context.deployDir.toAbsolutePath());

                copyArchiveToDeployDir(archive, context.deployDir);

                for (Archive<?> additionalArchive : additionalArchives) {
                    copyArchiveToDeployDir(additionalArchive, context.deployDir);
                }

                List<Path> classPath = new ArrayList<>();

                Path rootDir = context.deployDir.resolve("");
                if (isJavaArchive) {
                    ensureBeansXml(rootDir, null);
                    classPath.add(rootDir);
                } else {
                    // Prepare the launcher files
                    Path webInfDir = context.deployDir.resolve("WEB-INF");
                    Path classesDir = webInfDir.resolve("classes");
                    Path libDir = webInfDir.resolve("lib");
                    ensureBeansXml(classesDir, webInfDir);
                    addServerClasspath(classPath, classesDir, libDir, rootDir);
                    if (containerConfig.isInWebContainer()) {
                        if (containerConfig.isIncludeWarContextPath()) {
                            String rootContext = archive.getName().split("\\.")[0];
                            if (!containerConfig.getSkipContextPaths().contains(rootContext)) {
                                context.rootContext = rootContext;
                            }
                        }
                        if (!loadApplicationFromWebXml(context, webInfDir)) {
                            // Search Application in classes
                            loadApplicationFromClasses(context, archive);
                        }
                    }
                }

                startServer(context, classPath.toArray(new Path[0]));
            } catch (IOException | SAXException | ParserConfigurationException e) {
                LOGGER.log(Level.INFO, "Failed to start container", e);
                throw new DeploymentException("Failed to copy the archive assets into the deployment directory", e);
            } catch (InvocationTargetException e) {

                try {
                    context.runnerClass
                            .getDeclaredMethod("abortedCleanup")
                            .invoke(context.runner);
                } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
                    ex.printStackTrace();
                }

                throw lookForSupressedDeploymentException(e.getTargetException())
                        .map(d ->
                            new org.jboss.arquillian.container.spi.client.container.DeploymentException("Deployment failure!", d))
                        .orElseThrow(() -> new DefinitionException(e));
            } catch (ReflectiveOperationException e) {
                LOGGER.log(Level.INFO, "Failed to start container", e);
                throw new DefinitionException(e);        // validation exceptions
            }

            // Server has started, so we're done.
            //        ProtocolMetaData pm = new ProtocolMetaData();
            //        pm.addContext(new HTTPContext("Helidon", "localhost", containerConfig.getPort()));
            //        return pm;
        }
        return new ProtocolMetaData();
    }

    private boolean loadApplicationFromClasses(RunContext context, Archive<?> archive)
            throws ClassNotFoundException, ReflectiveOperationException {
        ArchivePath classes = ArchivePaths.create("WEB-INF", "classes");
        org.jboss.shrinkwrap.api.Node root = archive.getContent().get(classes);
        Collection<Class<Application>> applications = new HashSet<>();
        if (root != null) {
            deepApplicationFind(root, applications);
            context.applications.addAll(applications);
        }
        return !context.applications.isEmpty();
    }

    private void deepApplicationFind(org.jboss.shrinkwrap.api.Node parent,
            Collection<Class<Application>> applications) throws ClassNotFoundException {
        for (org.jboss.shrinkwrap.api.Node child : parent.getChildren()) {
            if (child.getChildren().isEmpty()) {
                String name = child.toString();
                if (name.endsWith(".class")) {
                    name = name.replaceFirst("\\.class", "").replaceFirst("/WEB-INF/classes/", "").replaceAll("/", ".");
                    Class<?> clazz = Class.forName(name);
                    if (Application.class.isAssignableFrom(clazz)) {
                        applications.add((Class<Application>) clazz);
                    }
                }
            } else {
                deepApplicationFind(child, applications);
            }
        }
    }

    private boolean loadApplicationFromWebXml(RunContext context, Path webInfDir) throws IOException,
    ParserConfigurationException, SAXException, ReflectiveOperationException {
        Path webXml = webInfDir.resolve("web.xml");
        if (Files.exists(webXml)) {
            try (InputStream inputStream = Files.newInputStream(webXml)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(inputStream);
                NodeList nodes = doc.getElementsByTagName("init-param");
                for (int i = 0; i < nodes.getLength(); i++) {
                    NodeList childs = nodes.item(i).getChildNodes();
                    Class<Application> application = application(childs);
                    if (application != null) {
                        context.explicitRsApplication = Optional.of(application);
                        context.applications.add(application);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Class<Application> application(NodeList childs) throws ClassNotFoundException {
        boolean isApp = false;
        String appName = null;
        for (int j = 0; j < childs.getLength(); j++) {
            org.w3c.dom.Node element = childs.item(j);
            String name = element.getNodeName();
            String value = element.getTextContent();
            if ("param-name".equals(name) && "jakarta.ws.rs.Application".equals(value)) {
                isApp = true;
            } else if ("param-value".equals(name)) {
                appName = value;
            }
        }
        if (isApp) {
            return (Class<Application>) Class.forName(appName);
        } else {
            return null;
        }
    }

    static Optional<Exception> lookForSupressedDeploymentException(Throwable t) {
        if (t == null) {
            return Optional.empty();
        }
        if (jakarta.enterprise.inject.spi.DeploymentException.class.isAssignableFrom(t.getClass())) {
            return Optional.of((jakarta.enterprise.inject.spi.DeploymentException) t);
        } else if (IllegalStateException.class.isAssignableFrom(t.getClass())) {
            return Optional.of((IllegalStateException) t);
        } else if (java.lang.Error.class.isAssignableFrom(t.getClass())) {
            return Optional.of((new jakarta.enterprise.inject.spi.DeploymentException(t)));
        }

        var deploymentException = lookForSupressedDeploymentException(t.getCause());
        for (Throwable suppressed : t.getSuppressed()) {
            var candicate = lookForSupressedDeploymentException(suppressed);
            if (candicate.isPresent()) {
                deploymentException = candicate;
            }
        }
        return deploymentException;
    }

    void startServer(RunContext context, Path[] classPath)
            throws ReflectiveOperationException {

        try {
            Optional.of((SeContainer) CDI.current())
                    .ifPresent(SeContainer::close);
            stopAll();
        } catch (IllegalStateException ignored) {
            // there is no server running
        }

        URLClassLoader urlClassloader;
        ClassLoader parent;

        if (containerConfig.getUserParentClassloader()) {
            urlClassloader = new URLClassLoader(toUrls(classPath));
            parent = urlClassloader;
        } else {
            urlClassloader = new URLClassLoader(toUrls(classPath), null);
            parent = Thread.currentThread().getContextClassLoader();
        }

        context.classLoader = new HelidonContainerClassloader(parent,
                                                              urlClassloader,
                                                              excludedLibrariesPattern,
                                                              containerConfig.getUserParentClassloader());

        context.oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(context.classLoader);

        context.runnerClass = context.classLoader
                .loadClass("io.helidon.microprofile.arquillian.ServerRunner");

        context.runner = context.runnerClass
                .getDeclaredConstructor()
                .newInstance();

        STOP_RUNNABLES.add(() -> {
            try {
                context.runnerClass.getDeclaredMethod("stop").invoke(context.runner);
            } catch (ReflectiveOperationException e) {
                LOGGER.log(Level.WARNING, "Can't stop embedded Helidon", e);
            }
        });

        // Configuration needs to be explicit, as some TCK libraries contain an unfortunate
        //    META-INF/microprofile-config.properties (such as JWT-Auth)
        ConfigBuilder builder = ConfigProviderResolver.instance()
                .getBuilder();
        // Add root context path per Application
        if (context.rootContext != null && !context.applications.isEmpty()) {
            containerConfig.addConfigBuilderConsumer(configBuilder -> {
                Map<String, String> properties = new HashMap<>();
                for (Class<Application> app : context.applications) {
                    String key = app.getName() + ".routing-path.path";
                    ApplicationPath path = app.getDeclaredAnnotation(ApplicationPath.class);
                    String value = "/" + context.rootContext;
                    if (path != null && !"/".equals(path.value()) && !"".equals(path.value())) {
                        value = value + "/" + path.value();
                    }
                    properties.put(key, value);
                }
                configBuilder.withSources(MpConfigSources.create(properties));
            });
        }
        // we must use the default configuration to support profiles (and test them correctly in config TCK)
        // we may need to have a custom configuration for TCKs that do require workarounds
        /*
        Config config =
                containerConfig.useBuilder(builder.withSources(findMpConfigSources(classPath)))
                .addDiscoveredConverters()
                // will read application.yaml
                .addDiscoveredSources()
                .build();

        if (config.getOptionalValue("mp.config.profile", Boolean.class).orElse(false)) {
            // there is a configuration profile, we must add correct sources
        }
        */
        Config config;
        if (containerConfig.hasCustomConfig()) {
            config = containerConfig.useBuilder(builder).build();
        } else {
            config = ConfigProvider.getConfig();
        }

        context.runnerClass
                .getDeclaredMethod("start", Config.class, Integer.TYPE, Optional.class)
                .invoke(context.runner, config, containerConfig.getPort(), context.explicitRsApplication);
    }

    private URL[] toUrls(Path[] classPath) {
        List<URL> result = new ArrayList<>();

        for (Path path : classPath) {
            try {
                result.add(path.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Classpath failed to be constructed for path: " + path);
            }
        }

        return result.toArray(new URL[0]);
    }

    private ConfigSource[] findMpConfigSources(Path[] classPath) {
        String location = "META-INF/microprofile-config.properties";
        List<ConfigSource> sources = new ArrayList<>(5);

        for (Path path : classPath) {
            if (Files.isDirectory(path)) {
                Path mpConfig = path.resolve(location);
                if (Files.exists(mpConfig)) {
                    sources.add(MpConfigSources.create(mpConfig));
                }
            } else {
                // this must be a jar file (classpath is either jar file or a directory)
                FileSystem fs;
                try {
                    fs = FileSystems.newFileSystem(path, Thread.currentThread().getContextClassLoader());
                    Path mpConfig = fs.getPath(location);
                    if (Files.exists(mpConfig)) {
                        sources.add(MpConfigSources.create(path + "!" + mpConfig, mpConfig));
                    }
                } catch (IOException e) {
                    // ignored
                }
            }
        }

        // add the expected sysprops and env vars
        sources.add(MpConfigSources.environmentVariables());
        sources.add(MpConfigSources.systemProperties());

        return sources.toArray(new ConfigSource[0]);
    }

    void addServerClasspath(List<Path> classpath, Path classesDir, Path libDir, Path rootDir) throws IOException {
        // classes directory
        classpath.add(classesDir);

        // lib directory - need to find each jar file
        if (Files.exists(libDir)) {
            Files.list(libDir)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .forEach(classpath::add);
        }

        classpath.add(rootDir);
    }

    private void ensureBeansXml(Path classesDir, Path webinfDir) throws IOException {
        Path beansPath = classesDir.resolve("META-INF/beans.xml");
        Path metaInfPath = beansPath.getParent();
        if (null != metaInfPath) {
            Files.createDirectories(metaInfPath);
        }
        if (containerConfig.isInWebContainer() && webinfDir != null) {
            // In case exists WEB-INF/beans.xml, then move it to classes/META-INF/beans.xml
            Path webInfBeansPath = webinfDir.resolve("beans.xml");
            if (Files.exists(webInfBeansPath)) {
                Files.move(webInfBeansPath, beansPath);
                return;
            }
        }
        if (Files.exists(beansPath)) {
            return;
        }
        if (containerConfig.isUseBeanXmlTemplate()) {
            try (InputStream beanXmlTemplate = HelidonDeployableContainer.class.getResourceAsStream("/templates/beans.xml")) {
                if (null == beanXmlTemplate) {
                    Files.write(beansPath, new byte[0]);
                } else {
                    Files.copy(beanXmlTemplate, beansPath);
                }
            }
        }
    }

    @Override
    public void undeploy(Archive<?> archive) {
        // Clean up all the base metrics for next test
        cleanupBaseMetrics();

        // Clean up contexts
        RunContext context = contexts.remove(archive.getId());
        if (null == context) {
            LOGGER.log(Level.ERROR, "Undeploying an archive that was not deployed. ID: " + archive.getId());
            return;
        }

        // Stop the server
        try {
            context.runnerClass.getDeclaredMethod("stop")
                    .invoke(context.runner);
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.WARNING, "Failed to invoke stop operation on server runner", e);
        } finally {
            try {
                context.classLoader.close();
            } catch (IOException ignore) {
            }
            // Restore original context class loader
            Thread.currentThread().setContextClassLoader(context.oldClassLoader);
        }

        // Try to clean up the deploy directory
        if (containerConfig.getDeleteTmp()) {
            if (context.deployDir != null) {
                try {
                    Files.walk(context.deployDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    // Ignored. If we failed to delete for some reason, the OS will clean it up. Eventually.
                                }
                            });

                } catch (IOException ignore) {
                }
                context.deployDir = null;
            }
        }
    }

    void stopAll() {
        Runnable polled = STOP_RUNNABLES.poll();
        while (Objects.nonNull(polled)) {
            polled.run();
            polled = STOP_RUNNABLES.poll();
        }
    }

    @Override
    public void deploy(Descriptor descriptor) {
        // No-Op
    }

    @Override
    public void undeploy(Descriptor descriptor) {
        // No-Op
    }

    /**
     * Injects the base metric registry and cleans up all metrics in preparation to run another
     * Arquillian test in the same VM. Without this cleanup, metrics added by a previous test
     * would be available and may cause failures.
     */
    private void cleanupBaseMetrics() {
        try {
            List.of(new BaseRegistryTypeLiteral(),
                    new VendorRegistryTypeLiteral(),
                    new ApplicationRegistryTypeLiteral())
                    .forEach(literal -> {
                        MetricRegistry metricRegistry = CDI.current().select(MetricRegistry.class,
                                                                             literal).get();
                        Objects.requireNonNull(metricRegistry);
                        metricRegistry.removeMatching((m, v) -> true);
                    });

        } catch (IllegalStateException | ResolutionException e) {
            // this may be a negative CDI test (e.g. when CDI is intended not to start)
            // in such cases, CDI will not be available
            // It can also be that metrics are not used at all in some tests, so we don't want to fail.
            LOGGER.log(Level.DEBUG, "Unable to cleanup base metrics", e);
        }
    }

    /**
     * Copies the given Archive to the given deployDir. For each asset copied, the callback will be invoked.
     *
     * @param archive   The archive to deploy. This cannot be null.
     * @param deployDir The directory to deploy to. This cannot be null.
     * @throws IOException if there is an I/O failure related to copying the archive assets to the deployment
     *                     directory. If this happens, the entire setup is bad and must be terminated.
     */
    private void copyArchiveToDeployDir(Archive<?> archive, Path deployDir) throws IOException {
        Map<ArchivePath, Node> archiveContents = archive.getContent();
        for (Map.Entry<ArchivePath, Node> entry : archiveContents.entrySet()) {
            ArchivePath path = entry.getKey();
            Node n = entry.getValue();
            Path f = subpath(deployDir, path.get());
            if (n.getAsset() == null) {
                // Create the directory and any parents if necessary.
                Files.createDirectories(f);
            } else {
                // Create the directory and any parents if necessary.
                Path parent = f.getParent();
                if (null != parent) {
                    Files.createDirectories(parent);
                }
                // Copy the asset to the destination location
                Files.copy(n.getAsset().openStream(), f, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path subpath(Path directory, String path) {
        if (path.startsWith("/")) {
            return directory.resolve(path.substring(1));
        }
        return directory.resolve(path);
    }

    private static class RunContext {
        /**
         * The temporary directory where deployment will take place. As a real temporary directory, the
         * OS will clean this up (eventually). However we try to be good citizens and clean this directory up
         * during un-deployment.
         */
        private Path deployDir;
        // class loader of this server instance
        private HelidonContainerClassloader classLoader;
        // class of the runner - loaded once per each run
        private Class<?> runnerClass;
        // runner used to run this server instance
        private Object runner;
        // existing class loader
        private ClassLoader oldClassLoader;
        private String rootContext;
        // It is explicit when it is defined in web.xml. CDI has nothing to do with that.
        private Optional<Class<Application>> explicitRsApplication = Optional.empty();
        private Set<Class<Application>> applications = new HashSet<>();
    }

    static class HelidonContainerClassloader extends ClassLoader implements Closeable {
        private final Pattern excludedLibrariesPattern;
        private final URLClassLoader wrapped;
        private final boolean useParentClassloader;

        HelidonContainerClassloader(ClassLoader parent,
                                    URLClassLoader wrapped,
                                    Pattern excludedLibrariesPattern,
                                    boolean useParentClassloader) {
            super(parent);

            this.excludedLibrariesPattern = excludedLibrariesPattern;
            this.wrapped = wrapped;
            this.useParentClassloader = useParentClassloader;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            Set<URL> result = new LinkedHashSet<>();

            Enumeration<URL> resources = wrapped.getResources(name);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                result.add(url);
            }

            resources = super.getResources(name);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();

                if (excludedLibrariesPattern == null) {
                    result.add(url);
                } else {
                    try {
                        String path = url.toURI().toString().replace('\\', '/');
                        if (!excludedLibrariesPattern.matcher(path).matches()) {
                            result.add(url);
                        }
                    } catch (URISyntaxException e) {
                        result.add(url);
                    }
                }
            }
            // Give priority to WebApp resources (for example ServiceLoader provided by WebApp)
            List<URL> toRevert = new ArrayList<URL>(result);
            Collections.reverse(toRevert);
            return Collections.enumeration(toRevert);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            InputStream stream = wrapped.getResourceAsStream(name);
            if ((stream == null) && name.startsWith("/")) {
                stream = wrapped.getResourceAsStream(name.substring(1));
            }

            if ((stream == null) && useParentClassloader) {
                stream = super.getResourceAsStream(name);
            }

            if ((stream == null) && useParentClassloader && name.startsWith("/")) {
                stream = super.getResourceAsStream(name.substring(1));
            }

            return stream;
        }

        @Override
        public void close() throws IOException {
            this.wrapped.close();
        }
    }
}
