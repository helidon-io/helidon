/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.telemetry;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.jboss.arquillian.test.api.ArquillianResource;

/**
 * A really basic client for doing Http requests
 * <p>
 * For use when we don't want to use JAX-RS client or something else which has integration with telemetry
 */
public class BasicHttpClient {

    private URI baseUri;

    /**
     * @param baseUrl
     *            The base URL. Any path requested through this client will be appended to this URL. This should usually
     *            be a URL injected using {@link ArquillianResource}
     */
    public BasicHttpClient(URL baseUrl) {
        try {
            baseUri = baseUrl.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Makes a GET request to a path and returns the response code
     * 
     * @param path
     *            the path to request, relative to the baseUrl
     * @return the response code
     */
    public int get(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        try {
            URL spanUrl = baseUri.resolve(path).toURL();
            HttpURLConnection connection = (HttpURLConnection) spanUrl.openConnection();
            try {
                return connection.getResponseCode();
            } catch (Exception e) {
                throw new RuntimeException("Exception retriving " + spanUrl, e);
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception retriving path " + path, e);
        }
    }

}