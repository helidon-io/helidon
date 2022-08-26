/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.staticcontent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.Http;
import io.helidon.nima.webserver.http.HttpException;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * Handles static content from the classpath.
 */
class ClassPathContentHandler extends FileBasedContentHandler {
    private static final System.Logger LOGGER = System.getLogger(ClassPathContentHandler.class.getName());

    private final ClassLoader classLoader;
    private final String root;
    private final String rootWithTrailingSlash;
    private final BiFunction<String, String, Path> tmpFile;

    // URL's hash code and equal are not suitable for map or set
    private final Map<String, ExtractedJarEntry> extracted = new ConcurrentHashMap<>();

    ClassPathContentHandler(StaticContentSupport.ClassPathBuilder builder) {
        super(builder);

        this.classLoader = builder.classLoader();
        this.root = builder.root();
        this.rootWithTrailingSlash = root + '/';

        Path tmpDir = builder.tmpDir();
        if (tmpDir == null) {
            this.tmpFile = (prefix, suffix) -> {
                try {
                    return Files.createTempFile(prefix, suffix);
                } catch (IOException e) {
                    throw HttpException.builder()
                            .message("Static content processing issue")
                            .type(DirectHandler.EventType.INTERNAL_ERROR)
                            .cause(e)
                            .build();
                }
            };
        } else {
            this.tmpFile = (prefix, suffix) -> {
                try {
                    return Files.createTempFile(tmpDir, prefix, suffix);
                } catch (IOException e) {
                    throw HttpException.builder()
                            .message("Static content processing issue")
                            .type(DirectHandler.EventType.INTERNAL_ERROR)
                            .cause(e)
                            .build();
                }
            };
        }
    }

    static String fileName(URL url) {
        String path = url.getPath();
        int index = path.lastIndexOf('/');
        if (index > -1) {
            return path.substring(index + 1);
        }

        return path;
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    @Override
    boolean doHandle(Http.Method method, String requestedPath, ServerRequest request, ServerResponse response)
            throws IOException, URISyntaxException {

        String resource = requestedPath.isEmpty() ? root : (rootWithTrailingSlash + requestedPath);

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Requested class path resource: " + resource);
        }

        // this MUST be done, so we do not escape the bounds of configured directory
        // We use multi-arg constructor so it performs url encoding
        URI myuri = new URI(null, null, resource, null);
        String requestedResource = myuri.normalize().getPath();

        if (!requestedResource.equals(root) && !requestedResource.startsWith(rootWithTrailingSlash)) {
            return false;
        }

        // try to find the resource on classpath (cannot use root URL and then resolve, as root and sub-resource
        // may be from different jar files/directories
        URL url = classLoader.getResource(resource);

        String welcomeFileName = welcomePageName();
        if (null != welcomeFileName) {
            String welcomeFileResource = requestedResource + "/" + welcomeFileName;
            URL welcomeUrl = classLoader.getResource(welcomeFileResource);
            if (null != welcomeUrl) {
                // there is a welcome file under requested resource, ergo requested resource was a directory
                String rawFullPath = request.path().rawPath();
                if (rawFullPath.endsWith("/")) {
                    // this is OK, as the path ends with a forward slash
                    url = welcomeUrl;
                } else {
                    // must redirect
                    redirect(request, response, rawFullPath + "/");
                    return true;
                }
            }
        }

        if (url == null) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Requested resource " + resource + " does not exist");
            }
            return false;
        }

        URL logUrl = url; // need to be effectively final to use in lambda
        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Located resource url. Resource: " + resource + ", URL: " + logUrl);
        }

        // now read the URL - we have direct support for files and jar files, others are handled by stream only
        switch (url.getProtocol()) {
        case "file":
            sendFile(method, Paths.get(url.toURI()), request, response, welcomePageName());
            break;
        case "jar":
            return sendJar(method, requestedResource, url, request, response);
        default:
            sendUrlStream(method, url, request, response);
            break;
        }

        return true;
    }

    boolean sendJar(Http.Method method,
                    String requestedResource,
                    URL url,
                    ServerRequest request,
                    ServerResponse response) throws IOException {

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Sending static content from classpath: " + url);
        }

        ExtractedJarEntry extrEntry = extracted
                .compute(requestedResource, (key, entry) -> existOrCreate(url, entry));
        if (extrEntry.tempFile == null) {
            return false;
        }
        if (extrEntry.lastModified != null) {
            processEtag(String.valueOf(extrEntry.lastModified.toEpochMilli()), request.headers(), response.headers());
            processModifyHeaders(extrEntry.lastModified, request.headers(), response.headers());
        }

        String entryName = (extrEntry.entryName == null) ? fileName(url) : extrEntry.entryName;

        processContentType(entryName,
                           request.headers(),
                           response.headers());

        if (method == Http.Method.HEAD) {
            processContentLength(extrEntry.tempFile, response.headers());
            response.send();
        } else {
            send(request, response, extrEntry.tempFile);
        }

        return true;
    }

    private ExtractedJarEntry existOrCreate(URL url, ExtractedJarEntry entry) {
        if (entry == null) {
            return extractJarEntry(url);
        }
        if (entry.tempFile == null) {
            return entry;
        }
        if (Files.notExists(entry.tempFile)) {
            return extractJarEntry(url);
        }
        return entry;
    }

    private void sendUrlStream(Http.Method method, URL url, ServerRequest request, ServerResponse response)
            throws IOException {

        LOGGER.log(Level.DEBUG, "Sending static content using stream from classpath: " + url);

        URLConnection urlConnection = url.openConnection();
        long lastModified = urlConnection.getLastModified();

        if (lastModified != 0) {
            processEtag(String.valueOf(lastModified), request.headers(), response.headers());
            processModifyHeaders(Instant.ofEpochMilli(lastModified), request.headers(), response.headers());
        }

        processContentType(fileName(url), request.headers(), response.headers());

        if (method == Http.Method.HEAD) {
            response.send();
            return;
        }

        try (InputStream in = url.openStream(); OutputStream outputStream = response.outputStream()) {
            in.transferTo(outputStream);
        }
    }

    private ExtractedJarEntry extractJarEntry(URL url) {
        try {
            JarURLConnection jarUrlConnection = (JarURLConnection) url.openConnection();
            JarFile jarFile = jarUrlConnection.getJarFile();
            JarEntry jarEntry = jarUrlConnection.getJarEntry();
            if (jarEntry.isDirectory()) {
                return new ExtractedJarEntry(jarEntry.getName()); // a directory
            }
            Instant lastModified = getLastModified(jarFile.getName());

            // Extract JAR entry to file
            try (InputStream is = jarFile.getInputStream(jarEntry)) {
                Path tempFile = tmpFile.apply("ws", ".je");
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                return new ExtractedJarEntry(tempFile, lastModified, jarEntry.getName());
            } finally {
                if (!jarUrlConnection.getUseCaches()) {
                    jarFile.close();
                }
            }
        } catch (IOException ioe) {
            throw HttpException.builder()
                    .message("Cannot load JAR file!")
                    .type(DirectHandler.EventType.INTERNAL_ERROR)
                    .cause(ioe)
                    .build();
        }
    }

    private Instant getLastModified(String path) throws IOException {
        Path file = Paths.get(path);

        if (Files.exists(file) && Files.isRegularFile(file)) {
            return Files.getLastModifiedTime(file).toInstant();
        } else {
            return null;
        }
    }

    private static class ExtractedJarEntry {
        private final Path tempFile;
        private final Instant lastModified;
        private final String entryName;

        ExtractedJarEntry(Path tempFile, Instant lastModified, String entryName) {
            this.tempFile = tempFile;
            this.lastModified = lastModified;
            this.entryName = entryName;
        }

        /**
         * Creates directory representation.
         */
        ExtractedJarEntry(String entryName) {
            this.tempFile = null;
            this.lastModified = null;
            this.entryName = entryName;
        }
    }
}
