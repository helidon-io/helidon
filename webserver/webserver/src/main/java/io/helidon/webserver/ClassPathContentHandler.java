/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;

/**
 * Handles static content from the classpath.
 */
class ClassPathContentHandler extends StaticContentHandler {
    private static final Logger LOGGER = Logger.getLogger(ClassPathContentHandler.class.getName());

    private final ClassLoader classLoader;
    // URL's hash code and equal are not suitable for map or set
    private final Map<String, ExtractedJarEntry> extracted = new ConcurrentHashMap<>();
    private final String root;
    private final String rootWithTrailingSlash;

    ClassPathContentHandler(String welcomeFilename,
                            ContentTypeSelector contentTypeSelector,
                            String root,
                            ClassLoader classLoader) {
        super(welcomeFilename, contentTypeSelector);

        this.classLoader = (classLoader == null) ? this.getClass().getClassLoader() : classLoader;
        this.root = root;
        this.rootWithTrailingSlash = root + '/';
    }

    public static StaticContentHandler create(String welcomeFileName,
                                              ContentTypeSelector selector,
                                              String clRoot,
                                              ClassLoader classLoader) {
        ClassLoader contentClassloader = (classLoader == null)
                ? ClassPathContentHandler.class.getClassLoader()
                : classLoader;

        String cleanRoot = clRoot;

        while (cleanRoot.endsWith("/")) {
            cleanRoot = clRoot.substring(0, cleanRoot.length() - 1);
        }

        if (cleanRoot.isEmpty()) {
            throw new IllegalArgumentException("Cannot serve full classpath, please configure a classpath prefix");
        }

        return new ClassPathContentHandler(welcomeFileName, selector, clRoot, contentClassloader);
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    @Override
    boolean doHandle(Http.RequestMethod method, String requestedPath, ServerRequest request, ServerResponse response)
            throws IOException, URISyntaxException {

        String resource = requestedPath.isEmpty() ? root : (rootWithTrailingSlash + requestedPath);

        LOGGER.finest(() -> "Requested class path resource: " + resource);

        // this MUST be done, so we do not escape the bounds of configured directory
        String requestedResource = URI.create(resource).normalize().toString();

        if (!requestedResource.equals(root) && !requestedResource.startsWith(rootWithTrailingSlash)) {
            return false;
        }

        // try to find the resource on classpath (cannot use root URL and then resolve, as root and sub-resource
        // may be from different jar files/directories
        URL url = classLoader.getResource(resource);
        if (url == null) {
            return false;
        }

        String welcomeFileName = welcomePageName();
        if (null != welcomeFileName) {
            String welcomeFileResource = requestedResource + "/" + welcomeFileName;
            URL welcomeUrl = classLoader.getResource(welcomeFileResource);
            if (null != welcomeUrl) {
                // there is a welcome file under requested resource, ergo requested resource was a directory
                String rawFullPath = request.uri().getRawPath();
                if (rawFullPath.endsWith("/")) {
                    // this is OK, as the path ends with a forward slash
                    url = welcomeUrl;
                } else {
                    // must redirect
                    redirect(response, rawFullPath + "/");
                    return true;
                }
            }
        }

        // now read the URL - we have direct support for files and jar files, others are handled by stream only
        switch (url.getProtocol()) {
        case "file":
            FileSystemContentHandler
                    .sendFile(method, Paths.get(url.toURI()), request, response, contentTypeSelector(), welcomePageName());
            break;
        case "jar":
            return sendJar(method, requestedResource, url, request, response);
        default:
            sendUrlStream(method, url, request, response);
            break;
        }

        return true;
    }

    private boolean sendJar(Http.RequestMethod method,
                            String requestedResource,
                            URL url,
                            ServerRequest request,
                            ServerResponse response) throws URISyntaxException {

        ExtractedJarEntry extrEntry = extracted.computeIfAbsent(requestedResource, thePath -> extractJarEntry(url));
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
                           response.headers(),
                           contentTypeSelector());

        if (method == Http.Method.HEAD) {
            response.send();
        } else {
            response.send(extrEntry.tempFile);
        }

        return true;
    }

    private void sendUrlStream(Http.RequestMethod method, URL url, ServerRequest request, ServerResponse response)
            throws IOException {

        URLConnection urlConnection = url.openConnection();
        long lastModified = urlConnection.getLastModified();

        if (lastModified != 0) {
            processEtag(String.valueOf(lastModified), request.headers(), response.headers());
            processModifyHeaders(Instant.ofEpochMilli(lastModified), request.headers(), response.headers());
        }

        processContentType(fileName(url), request.headers(), response.headers(), contentTypeSelector());

        if (method == Http.Method.HEAD) {
            response.send();
            return;
        }

        InputStream in = url.openStream();
        InputStreamPublisher byteBufPublisher = new InputStreamPublisher(in, 2048);
        Flow.Publisher<DataChunk> dataChunkPublisher = new Flow.Publisher<DataChunk>() {
            @Override
            public void subscribe(Flow.Subscriber<? super DataChunk> s) {
                byteBufPublisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        s.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(ByteBuffer item) {
                        s.onNext(DataChunk.create(item));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        s.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        s.onComplete();
                    }
                });
            }
        };
        response.send(dataChunkPublisher);
    }

    static String fileName(URL url) {
        String path = url.getPath();
        int index = path.lastIndexOf('/');
        if (index > -1) {
            return path.substring(index + 1);
        }

        return path;
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
