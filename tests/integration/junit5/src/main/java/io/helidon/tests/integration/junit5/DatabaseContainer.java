/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import java.lang.reflect.Type;

public abstract class DatabaseContainer extends AbstractContainer {

    private DatabaseContainerConfig.Builder dbConfigBuilder;
    private DatabaseContainerConfig dbConfig;

    protected DatabaseContainer() {
        super();
        dbConfigBuilder = null;
        dbConfig = null;
    }

    protected DatabaseContainerConfig dbConfig() {
        return dbConfig;
    }

    @Override
    public void setup() {
        super.setup();
        dbConfigBuilder = DatabaseContainerConfig.builder();
    }

    public void createDbConfig() {
        dbConfig = dbConfigBuilder.build();
    }

    @Override
    public boolean supportsParameter(Type type) {
        return super.supportsParameter(type)
                || DatabaseContainerConfig.Builder.class.isAssignableFrom((Class<?>) type)
                || DatabaseContainerConfig.class.isAssignableFrom((Class<?>) type);
    }

    @Override
    public Object resolveParameter(Type type) {
        if (super.supportsParameter(type)) {
            return super.resolveParameter(type);
        } else if (DatabaseContainerConfig.Builder.class.isAssignableFrom((Class<?>) type)) {
            return dbConfigBuilder;
        } else if (DatabaseContainerConfig.class.isAssignableFrom((Class<?>) type)) {
            return dbConfig;
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

    /**
     * Replace port value in provided URL.
     *
     * @param url source URL
     * @param port new port value in returned URL
     * @return source url with port replaced by {@code port} value.
     */
    public static String replacePortInUrl(String url, int port) {
        int begin = url.indexOf("://");
        if (begin >= 0) {
            int end = url.indexOf('/', begin + 3);
            int portBeg = url.indexOf(':', begin + 3);
            // Found port position in URL
            if (end > 0 && portBeg < end) {
                String frontPart = url.substring(0, portBeg + 1);
                String endPart = url.substring(end);
                return frontPart + Integer.toString(port) + endPart;
            } else {
                throw new IllegalStateException(
                        String.format("URL %s does not contain host and port part \"://host:port/\"", url));
            }
        } else {
            throw new IllegalStateException(
                    String.format("Could not find host separator \"://\" in URL %s", url));
        }
    }

}
