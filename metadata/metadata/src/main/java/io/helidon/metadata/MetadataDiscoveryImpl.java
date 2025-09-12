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

package io.helidon.metadata;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

class MetadataDiscoveryImpl implements MetadataDiscovery {
    static final String SYSTEM_PROPERTY_MODE = "io.helidon.metadata.mode";

    private static final System.Logger LOGGER = System.getLogger(MetadataDiscoveryImpl.class.getName());

    private final Map<String, List<MetadataFile>> metadata;

    private MetadataDiscoveryImpl(Map<String, List<MetadataFile>> metadata) {
        this.metadata = metadata;
    }

    static MetadataDiscovery create(Mode mode) {
        return create(mode, MetadataDiscoveryContext.create(classLoader()));
    }

    static MetadataDiscovery create(Mode mode, MetadataDiscoveryContext context) {
        if (mode == Mode.AUTO) {
            mode = guessMode(context);
        }

        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Metadata discovery mode: " + mode);
        }

        return switch (mode) {
            case AUTO -> throw new IllegalStateException("We have already guessed mode, this should never be reached");
            case RESOURCES -> createFromResources(context);
            case SCANNING -> createFromClasspathScanning(context);
            case NONE -> new MetadataDiscoveryImpl(Map.of());
        };
    }

    static MetadataDiscovery create(ClassLoader cl) {
        String modeString = System.getProperty("io.helidon.metadata.mode", Mode.AUTO.name()).toUpperCase(Locale.ROOT);
        Mode mode = Mode.valueOf(modeString);
        var context = MetadataDiscoveryContext.create(cl);
        return create(mode, context);
    }

    static MetadataDiscovery createFromClasspathScanning(MetadataDiscoveryContext ctx) {

        // all files and directories that are part of the current classpath/module path
        Set<Path> configuredClasspath = modules();

        Set<MetadataFile> metadatums = new HashSet<>();

        Deque<Path> pathStack = new ArrayDeque<>(configuredClasspath);

        String location = ctx.location();
        Set<String> metadataFiles = ctx.metadataFiles();

        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG,
                       "Classpath configured: " + configuredClasspath
                               + ", location: " + location
                               + ", metadataFiles: " + metadataFiles);
        }

        while (!pathStack.isEmpty()) {
            Path path = pathStack.pop();
            if (!Files.exists(path)) {
                continue;
            }

            if (Files.isDirectory(path)) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Analyzing directory: " + path);
                }

                try {
                    Files.walkFileTree(path, new MetadataFileVisitor(location, metadataFiles, path, metadatums));
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to do classpath scanning on path: " + path, e);
                }
            } else if (isZipFile(path)) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Analyzing zip file: " + path);
                }
                try (var fs = FileSystems.newFileSystem(path, Map.of())) {
                    for (Path jarRoot : fs.getRootDirectories()) {
                        if (LOGGER.isLoggable(Level.TRACE)) {
                            LOGGER.log(Level.TRACE, "Analyzing zip root directory: " + jarRoot);
                        }
                        Files.walkFileTree(jarRoot,
                                           new ZipMetadataFileVisitor(path,
                                                                      location,
                                                                      metadataFiles,
                                                                      jarRoot,
                                                                      metadatums));
                        Path mf = path.resolve("META-INF/MANIFEST.MF");
                        if (Files.exists(mf)) {
                            // jar files can reference classpath from manifest
                            Manifest manifest = new Manifest(Files.newInputStream(mf));
                            String cp = manifest.getMainAttributes().getValue("Class-Path");
                            if (cp != null) {
                                Path parent = path.getParent();
                                for (String e : cp.split("\\s")) {
                                    if (!e.isEmpty()) {
                                        Path path0 = parent.resolve(e).toAbsolutePath().normalize();
                                        if (configuredClasspath.add(path0)) {
                                            // avoid adding the same library more than once
                                            pathStack.push(path0);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to close zip file " + path, e);
                }
            } else {
                throw new IllegalArgumentException("Invalid classpath element, neither jar nor directory: "
                                                           + path.toAbsolutePath());
            }
        }

        Map<String, List<MetadataFile>> metadataMap = new HashMap<>();
        metadatums.forEach(it -> metadataMap.computeIfAbsent(it.fileName(), k -> new ArrayList<>()).add(it));

        return new MetadataDiscoveryImpl(metadataMap);
    }

    @Override
    public List<MetadataFile> list(String fileName) {
        Objects.requireNonNull(fileName, "fileName is null");

        List<MetadataFile> found = metadata.get(fileName);
        if (found == null) {
            return List.of();
        }
        return found;
    }

    @Override
    public String toString() {
        return "MetadataImpl{"
                + "metadata=" + metadata
                + '}';
    }

    private static Mode guessMode(MetadataDiscoveryContext ctx) {
        // check if there is more than one MANIFEST.MF on the classpath
        if (isMultipleJars(ctx.classLoader())) {
            LOGGER.log(Level.TRACE, "Multiple jars on classpath, using resource discovery");
            return Mode.RESOURCES;
        }
        // use scanning unless we have merged metadata file
        var manifests = ctx.classLoader()
                .resources(ctx.location() + "/" + ctx.manifestFile())
                .distinct()
                .toList();

        if (manifests.isEmpty()) {
            LOGGER.log(Level.TRACE, "Single jar file, no manifests - using scanning");
            return Mode.SCANNING;
        }

        if (manifests.size() > 1) {
            LOGGER.log(Level.TRACE, "Multiple manifests, using resource discovery");
            return Mode.RESOURCES;
        }

        // we have exactly one manifest, let's check if it is merged or not
        try (BufferedReader br = new BufferedReader(new InputStreamReader(manifests.getFirst().openStream(), UTF_8))) {
            boolean found = false;
            String line;
            while ((line = br.readLine()) != null) {
                if (MetadataConstants.MANIFEST_ID_LINE.equals(line)) {
                    if (found) {
                        // if there is more than one id line, consider this file merged
                        LOGGER.log(Level.TRACE, "Manifest is merged, using resource discovery");
                        return Mode.RESOURCES;
                    }
                    found = true;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read manifest " + manifests.getFirst(), e);
        }
        LOGGER.log(Level.TRACE, "Single jar file, unmerged manifest - using scanning");
        return Mode.SCANNING;
    }

    private static boolean isZipFile(Path path) {
        // simple one - just check .jar or .zip
        Path fileNamePath = path.getFileName();
        String fileName = fileNamePath == null ? "" : fileNamePath.toString();

        if (fileName.endsWith(".jar") || fileName.endsWith(".zip")) {
            return true;
        }

        // magic number lookup
        try {
            if (Files.size(path) > 2) {
                try (InputStream is = Files.newInputStream(path)) {
                    return is.read() == 0x50 && is.read() == 0x4b; // magic number
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read magic number of " + path, e);
        }

        return false;
    }

    private static Set<Path> modules() {
        Set<Path> modules = new HashSet<>();
        for (String e : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!e.isEmpty()) {
                modules.add(Path.of(e).toAbsolutePath().normalize());
            }
        }
        for (String e : System.getProperty("jdk.module.path", "").split(File.pathSeparator)) {
            if (!e.isEmpty()) {
                modules.add(Path.of(e).toAbsolutePath().normalize());
            }
        }
        return modules;
    }

    private static MetadataDiscovery createFromResources(MetadataDiscoveryContext config) {
        Map<String, List<MetadataFile>> metadataMap = new HashMap<>();

        /*
        Find all files listed in the manifest
        Find all files in `location` with one of the names in `metadataFiles`
        Create a map where key is the file name, and value is the list of discovered resources
         */
        ClassLoader cl = config.classLoader();
        String location = config.location();
        String manifestFile = config.manifestFile();
        Set<String> metadataFiles = config.metadataFiles();

        var manfiestStream = cl.resources(location + "/" + manifestFile)
                .distinct()
                .flatMap(it -> parseHelidonManifest(cl, it));
        var nonManfiestStream = defaultMetadata(cl, location, metadataFiles);

        Stream.concat(manfiestStream, nonManfiestStream)
                // remove duplicates, which may happen
                .distinct()
                .forEach(it -> metadataMap.computeIfAbsent(it.fileName(), k -> new ArrayList<>()).add(it));

        return new MetadataDiscoveryImpl(metadataMap);
    }

    private static Stream<MetadataFile> defaultMetadata(ClassLoader cl, String defaultLocation, Set<String> metadataFiles) {
        List<MetadataFile> foundFiles = new ArrayList<>();

        for (String metadataFile : metadataFiles) {
            // something like `META-INF/helidon` + `/` + `service-registry.json`
            String location = defaultLocation + "/" + metadataFile;

            cl.resources(location)
                    .distinct()
                    .map(url -> MetadataFileImpl.create(location, metadataFile, url))
                    .forEach(foundFiles::add);
        }

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Found " + foundFiles.size() + " metadata files directly in "
                    + defaultLocation + ". Details in Debug (if more than 0).");
        }
        if (LOGGER.isLoggable(Level.DEBUG)) {
            foundFiles.forEach(it -> LOGGER.log(Level.DEBUG, "Found metadata file: " + it));
        }

        return foundFiles.stream();
    }

    private static Stream<MetadataFile> parseHelidonManifest(ClassLoader cl,
                                                             URL url) {
        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Parsing manifest: " + url);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream(), UTF_8))) {
            List<MetadataFile> foundFiles = new ArrayList<>();

            String line;
            int lineNumber = 0;

            while ((line = br.readLine()) != null) {
                lineNumber++; // we want to start with 1
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    // comments or empty lines
                    continue;
                }
                // the line now contains an exact location of a resource
                String resourceLocation = line;
                FoundFile fileName = fileName(line);
                URL resourceUrl = cl.getResource(resourceLocation);
                if (resourceUrl == null) {
                    throw new IllegalArgumentException("Metadata file " + resourceLocation + " not found, it is defined in "
                                                               + "manifest " + url + " at line " + lineNumber);
                }

                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG, "Adding file from manifest: " + resourceLocation + " at line " + lineNumber);
                }

                // only add files that are either in non-default directory, or that we do not know about
                // if in default directory, and in metadataFiles, we will look it up later
                foundFiles.add(MetadataFileImpl.create(resourceLocation,
                                                       fileName.fileName(),
                                                       resourceUrl));
            }

            return foundFiles.stream();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read services from " + url, e);
            return Stream.of();
        }
    }

    private static FoundFile fileName(String resourceLocation) {
        int lastSlash = resourceLocation.lastIndexOf('/');
        if (lastSlash == -1) {
            return new FoundFile("", resourceLocation);
        }
        String directory = resourceLocation.substring(0, lastSlash);
        String fileName = resourceLocation.substring(lastSlash + 1);

        return new FoundFile(directory, fileName);
    }

    private static boolean isMultipleJars(ClassLoader cl) {
        try {
            Enumeration<URL> enumeration = cl.getResources("META-INF/MANIFEST.MF");

            boolean found = false;

            while (enumeration.hasMoreElements()) {
                if (found) {
                    return true;
                }
                enumeration.nextElement();
                found = true;
            }

            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClassLoader classLoader() {
        var classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = MetadataDiscoveryImpl.class.getClassLoader();
        }
        return classLoader;
    }

    private record FoundFile(String resourceDirectory, String fileName) {
    }

    private static class MetadataFileVisitor extends SimpleFileVisitor<Path> {
        private final Path metaDir;
        private final Set<MetadataFile> metadata;
        private final Set<String> metadataFiles;
        private final String rootLocation;

        MetadataFileVisitor(String rootLocation, Set<String> metadataFiles, Path root, Set<MetadataFile> metadata) {
            this.rootLocation = rootLocation;
            this.metaDir = root.resolve(rootLocation);
            this.metadata = metadata;
            this.metadataFiles = metadataFiles;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (metaDir.startsWith(dir) || dir.startsWith(metaDir)) {
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG, "Visitor: continue with directory: " + dir);
                }
                return FileVisitResult.CONTINUE;
            } else {
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG, "Visitor: ignore directory: " + dir);
                }
                return FileVisitResult.SKIP_SUBTREE;
            }
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Visitor: visit file: " + file);
            }
            if (file.startsWith(metaDir)) {
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG, "Visitor: found valid location: " + file);
                }
                Path fileNamePath = file.getFileName();
                String fileName = fileNamePath == null ? "" : fileNamePath.toString();
                if (metadataFiles.contains(fileName)) {
                    if (LOGGER.isLoggable(Level.DEBUG)) {
                        LOGGER.log(Level.DEBUG, "Visitor: found valid file: " + file);
                    }
                    String relativePath = metaDir.relativize(file).toString().replace('\\', '/');
                    addMetadataFile(metadata, rootLocation + "/" + relativePath, fileName, file);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        void addMetadataFile(Set<MetadataFile> metadata, String location, String fileName, Path file) {
            metadata.add(MetadataFileImpl.create(location, fileName, file));
        }
    }

    private static class ZipMetadataFileVisitor extends MetadataFileVisitor {
        private final Path zipFile;

        ZipMetadataFileVisitor(Path zipFile,
                               String rootLocation,
                               Set<String> metadataFiles,
                               Path root,
                               Set<MetadataFile> metadata) {
            super(rootLocation, metadataFiles, root, metadata);
            this.zipFile = zipFile;
        }

        @Override
        void addMetadataFile(Set<MetadataFile> metadata, String location, String fileName, Path file) {
            metadata.add(MetadataFileImpl.create(zipFile, location, fileName, file));
        }
    }

    static class InstanceHolder {
        private static final Lock CACHE_LOCK = new ReentrantLock();
        private static final LinkedHashMap<ClassLoader, MetadataDiscovery> CACHE = new LinkedHashMap<>();

        static MetadataDiscovery getInstance() {
            CACHE_LOCK.lock();

            MetadataDiscovery instance;
            try {
                instance = CACHE.computeIfAbsent(classLoader(), MetadataDiscoveryImpl::create);

                if (CACHE.size() > 10) {
                    var iterator = CACHE.entrySet().iterator();
                    while (CACHE.size() > 10 && iterator.hasNext()) {
                        iterator.remove();
                    }
                }
            } finally {
                CACHE_LOCK.unlock();
            }

            return instance;
        }

        private static ClassLoader classLoader() {
            var cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = MetadataDiscoveryImpl.class.getClassLoader();
            }
            return cl;
        }
    }
}
