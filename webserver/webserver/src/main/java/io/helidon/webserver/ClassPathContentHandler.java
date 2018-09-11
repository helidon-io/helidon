/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.helidon.common.http.Http;

/**
 * Handles static content from the classpath.
 */
class ClassPathContentHandler extends FileSystemContentHandler {

    private final ClassLoader classLoader;
    // URL's hash code and equal are not suitable for map or set
    private final Map<Path, ExtractedJarEntry> extracted = new ConcurrentHashMap<>();

    ClassPathContentHandler(String welcomeFilename,
                            ContentTypeSelector contentTypeSelector,
                            String root,
                            ClassLoader classLoader) {
        super(welcomeFilename, contentTypeSelector, Paths.get(root));
        this.classLoader = classLoader == null ? this.getClass().getClassLoader() : classLoader;
    }

    @Override
    boolean doHandle(Http.RequestMethod method, Path path, ServerRequest request, ServerResponse response) throws IOException {
        URL url = classLoader.getResource(path.toString());
        if (url == null) {
            return false;
        } else {
            // If URL exists then it can be directory and we have to try locate a welcome page
            String welcomePageName = getWelcomePageName();
            if (welcomePageName != null && !welcomePageName.isEmpty()) {
                URL welcomeUrl = classLoader.getResource(path.resolve(welcomePageName).toString());
                if (welcomeUrl != null) {
                    url = welcomeUrl;
                }
            }
        }

        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            // If it is not a common file, then tryProcess it as a common file
            try {
                return super.doHandle(method, Paths.get(url.toURI()), request, response);
            } catch (URISyntaxException e) {
                throw new HttpException("ClassLoader resolves to invalid URI!", Http.Status.INTERNAL_SERVER_ERROR_500);
            }
        } else if ("jar".equals(protocol)) {
            URL theUrl = url;
            ExtractedJarEntry extrEntry = extracted.computeIfAbsent(path, thePath -> extractJarEntry(theUrl));
            if (extrEntry.tempFile == null) {
                return false;
            }
            if (extrEntry.lastModified != null) {
                processEtag(String.valueOf(extrEntry.lastModified.toEpochMilli()), request.headers(), response.headers());
                processModifyHeaders(extrEntry.lastModified, request.headers(), response.headers());
            }

            String entryName = extrEntry.entryName == null ? getFileName(path) : extrEntry.entryName;

            processContentType(entryName,
                               request.headers(),
                               response.headers());
            if (method == Http.Method.HEAD) {
                response.send();
            } else {
                response.send(extrEntry.tempFile);
            }
            return true;
        } else {
            throw new HttpException("Static content supports only JAR and File!", Http.Status.INTERNAL_SERVER_ERROR_500);
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
            Instant lastModified = getLastModified(url);

            // Extract JAR entry to file
            try (InputStream is = jarFile.getInputStream(jarEntry)) {
                Path tempFile = Files.createTempFile("ws", ".je");
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                return new ExtractedJarEntry(tempFile, lastModified, jarEntry.getName());
            } finally {
                if (!jarUrlConnection.getUseCaches()) {
                    jarFile.close();
                }
            }
        } catch (IOException ioe) {
            throw new HttpException("Cannot load JAR file!", Http.Status.INTERNAL_SERVER_ERROR_500, ioe);
        }
    }

    private Instant getLastModified(URL jarUrl) throws IOException {
        // Decode potentially encoded parameters
        String path = URI.create(jarUrl.getPath()).getPath();
        int jarDelimIdx = path.indexOf("!/");
        if (jarDelimIdx > -1) {
            path = path.substring(0, jarDelimIdx);
        }

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
