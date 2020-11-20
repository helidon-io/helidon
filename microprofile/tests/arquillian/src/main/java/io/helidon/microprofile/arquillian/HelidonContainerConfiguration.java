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

import java.util.HashMap;
import java.util.Map;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

/**
 * Configuration for the Helidon arquillian container.
 *
 * <ul>
 * <li>appClassName: (Optional) used to load a specific Application in the container</li>
 * <li>resourceClassName: (Optional) used to load a specific Resource in the container</li>
 * <li>port: (Optional) defaults to 8080</li>
 * <li>deleteTmp: (Optional) defaults to true: whether to remove temporary directories of tests or leave them in temp</li>
 * <li>addResourcesToApps: (Optional) defaults to false: whether to add resources classes to application (e.g. the application
 * is empty)</li>
 * <li>replaceConfigSourcesWithMp: (Optional) defaults to false: whether to replace config sources with microprofile if it
 * exists</li>
 * </ul>
 */
public class HelidonContainerConfiguration implements ContainerConfiguration {
    private final Map<String, String> customMap = new HashMap<>();
    private String appClassName = null;
    private String excludeArchivePattern = null;
    private int port = 8080;
    private boolean deleteTmp = true;
    private boolean useRelativePath = false;
    private boolean useParentClassloader = true;

    /**
     * Set custom property.
     *
     * @param propertyName name of the custom property
     * @param value        value of custom property
     */
    public void set(String propertyName, String value) {
        customMap.put(propertyName, value);
    }

    /**
     * Get custom property.
     *
     * @param propertyName name of the custom property
     * @return value of custom property or null
     */
    public String get(String propertyName) {
        return customMap.get(propertyName);
    }

    public String getApp() {
        return appClassName;
    }

    public void setApp(String app) {
        this.appClassName = app;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean getDeleteTmp() {
        return deleteTmp;
    }

    public void setDeleteTmp(boolean b) {
        this.deleteTmp = b;
    }

    public boolean getUseRelativePath() {
        return useRelativePath;
    }

    public void setUseRelativePath(boolean b) {
        this.useRelativePath = b;
    }

    public String getExcludeArchivePattern() {
        return excludeArchivePattern;
    }

    public void setExcludeArchivePattern(String excludeArchivePattern) {
        this.excludeArchivePattern = excludeArchivePattern;
    }

    public boolean getUserParentClassloader() {
        return useParentClassloader;
    }

    public void setUseParentClassloader(boolean useParentClassloader) {
        this.useParentClassloader = useParentClassloader;
    }

    @Override
    public void validate() throws ConfigurationException {
        if ((port <= 0) || (port > Short.MAX_VALUE)) {
            throw new ConfigurationException("port value of " + port + " is out of range");
        }
    }

    Map<String, String> getCustomMap() {
        return customMap;
    }
}
