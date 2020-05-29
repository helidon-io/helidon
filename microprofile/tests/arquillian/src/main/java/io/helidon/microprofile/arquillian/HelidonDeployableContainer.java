/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.DefinitionException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;
import io.helidon.microprofile.server.Server;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * Implementation of DeployableContainer for launching Helidon microprofile server.
 *
 * This implementation works by starting a Helidon Server for each deployment. The {@link #start()} and
 * {@link #stop()} methods are no-ops. When Arquillian invokes the {@link #deploy(Archive)} method:
 * <ol>
 * <li>A temporary directory is created</li>
 * <li>The WebArchive contents are written to the temporary directory</li>
 * <li>beans.xml is created in WEB-INF/classes if not present</li>
 * <li>The server is started with WEB-INF/classes and all libraries in WEB-INF/libon the classpath.</li>
 * </ol>
 *
 * Control is then returned to the test harness (in this case, generally testng) to run tests over HTTP.
 *
 * A test client has to provide arquillian with an arquillian.xml file. The properties in this file are
 * loaded automatically into the {@link HelidonContainerConfiguration} instance that Arquillian manages
 * and supplies to this class.
 */
public class HelidonDeployableContainer implements DeployableContainer<HelidonContainerConfiguration> {
    private static final Logger LOGGER = Logger.getLogger(HelidonDeployableContainer.class.getName());

    /**
     * The configuration for this container.
     */
    private HelidonContainerConfiguration containerConfig;

    /**
     * Run contexts - kept for each deployment.
     */
    private final Map<String, RunContext> contexts = new HashMap<>();

    private static ConcurrentLinkedQueue<Runnable> stopCalls = new ConcurrentLinkedQueue<>();
    private Server dummyServer = null;

    @Override
    public Class<HelidonContainerConfiguration> getConfigurationClass() {
        return HelidonContainerConfiguration.class;
    }

    @Override
    public void setup(HelidonContainerConfiguration configuration) {
        this.containerConfig = configuration;
    }

    @Override
    public void start() {
        dummyServer = Server.builder().build();
    }

    @Override
    public void stop() {
        // No-op
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription(HelidonLocalProtocol.PROTOCOL_NAME);
        // return new ProtocolDescription(LocalProtocol.NAME);
    }

    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
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
            LOGGER.info("Running Arquillian tests in directory: " + context.deployDir.toAbsolutePath());

            // Copy the archive into deployDir. Save off the class names for all classes included in the
            // "classes" dir. Later I will visit each of these and see if they are JAX-RS Resources or
            // Applications, so I can add those to the Server automatically.
            final Set<String> classNames = new TreeSet<>();
            copyArchiveToDeployDir(archive, context.deployDir, p -> {
                if (p.endsWith(".class")) {
                    final int prefixLength = isJavaArchive ? 1 : "/WEB-INF/classes/".length();
                    classNames.add(p.substring(prefixLength, p.lastIndexOf(".class")).replace('/', '.'));
                }
            });

            // If the configuration specified a Resource to load, add that to the set of class names
            if (containerConfig.getResource() != null) {
                classNames.add(containerConfig.getResource());
            }

            // If the configuration specified an Application to load, add that to the set of class names.
            // The "Main" method (see Main.template) will go through all these classes and discover whether
            // they are apps or resources and call the right builder methods on the Server.Builder.
            if (containerConfig.getApp() != null) {
                classNames.add(containerConfig.getApp());
            }

            URL[] classPath;

            Path rootDir = context.deployDir.resolve("");
            if (isJavaArchive) {
                ensureBeansXml(rootDir);
                classPath = new URL[] {
                        rootDir.toUri().toURL()
                };
            } else {
                // Prepare the launcher files
                Path webInfDir = context.deployDir.resolve("WEB-INF");
                Path classesDir = webInfDir.resolve("classes");
                Path libDir = webInfDir.resolve("lib");
                ensureBeansXml(classesDir);
                classPath = getServerClasspath(classesDir, libDir, rootDir);
            }

            startServer(context, classPath, classNames);
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Failed to start container", e);
            throw new DeploymentException("Failed to copy the archive assets into the deployment directory", e);
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.INFO, "Failed to start container", e);
            throw new DefinitionException(e.getCause());        // validation exceptions
        }

        // Server has started, so we're done.
        //        ProtocolMetaData pm = new ProtocolMetaData();
        //        pm.addContext(new HTTPContext("Helidon", "localhost", containerConfig.getPort()));
        //        return pm;
        return new ProtocolMetaData();
    }

    void startServer(RunContext context, URL[] classPath, Set<String> classNames)
            throws ReflectiveOperationException {

        try {
            Optional.of((SeContainer) CDI.current())
                    .ifPresent(SeContainer::close);
            stopAll();
        } catch (IllegalStateException ignored) {
            // there is no server running
        }

        context.classLoader = new MyClassloader(containerConfig.getExcludeArchivePattern(), new URLClassLoader(classPath));

        context.oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(context.classLoader);

        List<Supplier<? extends ConfigSource>> configSources = new LinkedList<>();
        configSources.add(ConfigSources.file(context.deployDir.resolve("META-INF/microprofile-config.properties").toString())
                                  .optional());
        // The following line supports MP OpenAPI, which allows an alternate
        // location for the config file.
        configSources.add(ConfigSources.file(
                context.deployDir.resolve("WEB-INF/classes/META-INF/microprofile-config.properties").toString())
                                  .optional());
        configSources.add(ConfigSources.file(context.deployDir.resolve("arquillian.properties").toString()).optional());
        configSources.add(ConfigSources.file(context.deployDir.resolve("application.properties").toString()).optional());
        configSources.add(ConfigSources.file(context.deployDir.resolve("application.yaml").toString()).optional());
        configSources.add(ConfigSources.classpath("tck-application.yaml").optional());

        // workaround for tck-fault-tolerance
        if (containerConfig.getReplaceConfigSourcesWithMp()) {
            URL mpConfigProps = context.classLoader.getResource("META-INF/microprofile-config.properties");
            if (mpConfigProps != null) {
                try {
                    Properties props = new Properties();
                    props.load(mpConfigProps.openStream());
                    configSources.clear();
                    configSources.add(ConfigSources.create(props));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        Config config = Config.builder()
                .sources(configSources)
                .build();

        context.runnerClass = context.classLoader
                .loadClass("io.helidon.microprofile.arquillian.ServerRunner");

        context.runner = context.runnerClass
                .getDeclaredConstructor()
                .newInstance();

        stopCalls.add(() -> {
            try {
                context.runnerClass.getDeclaredMethod("stop").invoke(context.runner);
            } catch (ReflectiveOperationException e) {
                LOGGER.log(Level.WARNING, "Can't stop embedded Helidon", e);
            }
        });

        context.runnerClass
                .getDeclaredMethod("start", Config.class, HelidonContainerConfiguration.class, Set.class, ClassLoader.class)
                .invoke(context.runner, config, containerConfig, classNames, context.classLoader);
    }

    URL[] getServerClasspath(Path classesDir, Path libDir, Path rootDir) throws IOException {
        List<URL> urls = new ArrayList<>();

        // classes directory
        urls.add(classesDir.toUri().toURL());

        // lib directory - need to find each jar file
        if (Files.exists(libDir)) {
            Files.list(libDir)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .forEach(path -> {
                        try {
                            urls.add(path.toUri().toURL());
                        } catch (MalformedURLException e) {
                            throw new HelidonArquillianException("Failed to get URL from library on path: "
                                                                         + path.toAbsolutePath(), e);
                        }
                    });
        }

        urls.add(rootDir.toUri().toURL());

        return urls.toArray(new URL[0]);
    }

    private void ensureBeansXml(Path classesDir) throws IOException {
        Path beansPath = classesDir.resolve("META-INF/beans.xml");
        if (Files.exists(beansPath)) {
            return;
        }
        try (InputStream beanXmlTemplate = HelidonDeployableContainer.class.getResourceAsStream("/templates/beans.xml")) {
            Path metaInfPath = beansPath.getParent();
            if (null != metaInfPath) {
                Files.createDirectories(metaInfPath);
            }

            if (null == beanXmlTemplate) {
                Files.write(beansPath, new byte[0]);
            } else {

                Files.copy(beanXmlTemplate, beansPath);
            }
        }
    }

    @Override
    public void undeploy(Archive<?> archive) {
        RunContext context = contexts.remove(archive.getId());
        if (null == context) {
            LOGGER.severe("Undeploying an archive that was not deployed. ID: " + archive.getId());
            return;
        }
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

        if (containerConfig.getDeleteTmp()) {
            // Try to clean up the deploy directory
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
        Runnable polled = stopCalls.poll();
        while (Objects.nonNull(polled)) {
            polled.run();
            polled = stopCalls.poll();
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
     * Copies the given Archive to the given deployDir. For each asset copied, the callback will be invoked.
     *
     * @param archive   The archive to deploy. This cannot be null.
     * @param deployDir The directory to deploy to. This cannot be null.
     * @param callback  The callback to invoke per item. This can be null.
     * @throws IOException if there is an I/O failure related to copying the archive assets to the deployment
     *                     directory. If this happens, the entire setup is bad and must be terminated.
     */
    private void copyArchiveToDeployDir(Archive<?> archive, Path deployDir, Consumer<String> callback) throws IOException {
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
                // Invoke the callback if one was registered
                String p = n.getPath().get();
                if (callback != null) {
                    callback.accept(p);
                }
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
        private MyClassloader classLoader;
        // class of the runner - loaded once per each run
        private Class<?> runnerClass;
        // runner used to run this server instance
        private Object runner;
        // existing class loader
        private ClassLoader oldClassLoader;
    }

    static class MyClassloader extends ClassLoader implements Closeable {
        private final URLClassLoader wrapped;
        private final Pattern excludePattern;

        MyClassloader(String excludeArchivePattern, URLClassLoader wrapped) {
            super(wrapped);
            this.wrapped = wrapped;
            this.excludePattern = (null == excludeArchivePattern ? null : Pattern.compile(excludeArchivePattern));
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            InputStream stream = wrapped.getResourceAsStream(name);
            if ((null == stream) && name.startsWith("/")) {
                return wrapped.getResourceAsStream(name.substring(1));
            }
            return stream;
        }


        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (excludePattern == null) {
                return wrapped.getResources(name);
            }

            if ("META-INF/beans.xml".equals(name)) {
                // workaround for graphql tck - need to exclude the TCK jar
                Enumeration<URL> resources = wrapped.getResources(name);
                List<URL> theList = new LinkedList<>();
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    String ref = url.toString();
                    Matcher matcher = excludePattern.matcher(ref);
                    if (matcher.matches()) {
                        LOGGER.info("Excluding " + url + " from bean archives.");
                    } else {
                        theList.add(url);
                    }
                }
                return Collections.enumeration(theList);
            }

            return super.getResources(name);
        }

        @Override
        public void close() throws IOException {
            this.wrapped.close();
        }
    }
}
