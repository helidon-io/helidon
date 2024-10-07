/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.soap.ws;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.ws.transport.http.DeploymentDescriptorParser;
import com.sun.xml.ws.transport.http.ResourceLoader;

class HelidonResourceLoader implements ResourceLoader {

    private final String catalog;
    private final boolean loadCustomSchemaEnabled;

    private static final String DD_DIR = DeploymentDescriptorParser.JAXWS_WSDL_DD_DIR + "/";

    private static final Logger LOGGER = Logger.getLogger(HelidonResourceLoader.class.getName());

    HelidonResourceLoader(String catalog, boolean loadCustomSchemaEnabled) {
        this.catalog = catalog;
        this.loadCustomSchemaEnabled = loadCustomSchemaEnabled;
    }

    @Override
    public URL getResource(String resource) throws MalformedURLException {
        String res = resource.startsWith("/") ? resource.substring(1) : resource;
        return Thread.currentThread().getContextClassLoader().getResource(res);
    }

    @Override
    public URL getCatalogFile() throws MalformedURLException {
        return getResource(catalog);
    }

    @Override
    public Set<String> getResourcePaths(String path) {
        // should be always true, warn if not
        if (("/" + DD_DIR).equals(path)) {
            Set<String> r = new HashSet<>();
            try {
                Collection<URL> resources;
                String res = path.substring(1);
                if (loadCustomSchemaEnabled) {
                    resources = Collections.list(Thread.currentThread().getContextClassLoader().getResources(res));
                } else {
                    resources = Arrays.asList(getResource(res));
                }
                for (URL rootUrl : resources) {
                    loadResources(path, rootUrl, r);
                }
                return r;
            } catch (IOException e) {
                LOGGER.log(Level.FINE, null, e);
            }
        }
        LOGGER.log(Level.WARNING, "Empty set for {0}", path);
        return Collections.EMPTY_SET;
    }

    private Set<String> loadResources(String path, URL rootUrl, Set<String> r) {
        FileSystem jarFS = null;
        try {
            if (rootUrl == null) {
                // no wsdls found
                return r;
            }
            Path wsdlDir = null;
            switch (rootUrl.getProtocol()) {
                case "file":
                    wsdlDir = Paths.get(rootUrl.toURI());
                    break;
                case "jar":
                    jarFS = FileSystems.newFileSystem(rootUrl.toURI(), Collections.emptyMap());
                    wsdlDir = jarFS.getPath(path);
                    break;
                default:
                    LOGGER.log(Level.WARNING, "Unsupported protocol: {0}", rootUrl.getProtocol());
                    LOGGER.log(Level.WARNING, "Empty set for {0}", rootUrl);
                    return Collections.EMPTY_SET;
            }

            //since we don't know exact file extension (can be .wsdl, .xml, .asmx,...)
            //nor whether the file is used or not (we are not processing wsdl/xsd imports here)
            //simply return all found files
            Files.walkFileTree(wsdlDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        String p = DD_DIR + Paths.get(rootUrl.toURI()).relativize(file).toString();
                        r.add(p);
                    } catch (URISyntaxException ex) {
                        LOGGER.log(Level.FINE, null, ex);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | URISyntaxException ex) {
            LOGGER.log(Level.FINE, null, ex);
        } finally {
            if (jarFS != null) {
                try {
                    jarFS.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.FINE, null, ex);
                }
            }
        }
        return r;
    }

}
