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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.spi.ConfigBuilder;
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
 * <li>inWebContainer: (Optional) defaults to false: loads WEB-INF/beans.xml and find any
 * jakarta.ws.rs.core.Application in the webapp classes</li>
 * <li>useBeanXmlTemplate: (Optional) defaults to true: will create the default templates/beans.xml when beans.xml is missing</li>
 * <li>includeWarContextPath: (Optional) defaults to false: will include the war name as a root context.
 * For example, if a example.war is deployed, the root context is going to be /example.</li>
 * <li>skipContextPaths: (Optional) defaults to empty: define the context paths to be excluded.</li>
 * <li>multipleDeployments: (Optional) defaults to true: workaround for tests that unintentionally
 * executes 1+ times @org.jboss.arquillian.container.test.api.Deployment</li>
 * </ul>
 */
public class HelidonContainerConfiguration implements ContainerConfiguration {
    private String appClassName = null;
    private String excludeArchivePattern = null;
    private int port = 8080;
    private boolean deleteTmp = true;
    private boolean useRelativePath = false;
    private boolean useParentClassloader = true;
    private boolean inWebContainer = false;
    private boolean useBeanXmlTemplate = true;
    private boolean multipleDeployments = true;
    /*
     *  Restful requires it, but core profile don't (because rest used to be deployed in a
     *  web container together with other apps and in core profile there is only one app)
     */
    private boolean includeWarContextPath = false;
    private final List<Consumer<ConfigBuilder>> builderConsumers = new ArrayList<>();
    private final Set<String> skipContextPaths = new HashSet<>();

    /**
     * Access container's config builder.
     *
     * @param addedBuilderConsumer container's config builder
     */
    public void addConfigBuilderConsumer(Consumer<ConfigBuilder> addedBuilderConsumer) {
        this.builderConsumers.add(addedBuilderConsumer);
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

    public boolean isInWebContainer() {
        return inWebContainer;
    }

    public void setInWebContainer(boolean inWebContainer) {
        this.inWebContainer = inWebContainer;
    }

    public boolean isUseBeanXmlTemplate() {
        return useBeanXmlTemplate;
    }

    public void setUseBeanXmlTemplate(boolean useBeanXmlTemplate) {
        this.useBeanXmlTemplate = useBeanXmlTemplate;
    }

    public boolean isIncludeWarContextPath() {
        return includeWarContextPath;
    }

    public void setIncludeWarContextPath(boolean includeWarContextPath) {
        this.includeWarContextPath = includeWarContextPath;
    }

    public boolean isMultipleDeployments() {
        return multipleDeployments;
    }

    public void setMultipleDeployments(boolean multipleDeployments) {
        this.multipleDeployments = multipleDeployments;
    }

    @Override
    public void validate() throws ConfigurationException {
        if ((port <= 0) || (port > Short.MAX_VALUE)) {
            throw new ConfigurationException("port value of " + port + " is out of range");
        }
    }

    boolean hasCustomConfig(){
        return !this.builderConsumers.isEmpty();
    }

    ConfigBuilder useBuilder(ConfigBuilder configBuilder) {
        this.builderConsumers.forEach(builderConsumer -> builderConsumer.accept(configBuilder));
        return configBuilder;
    }

    /**
     * Getter of skipContextPaths.
     * @return the skipContextPaths
     */
    public Set<String> getSkipContextPaths() {
        return skipContextPaths;
    }

    /**
     * List of comma separated context roots that should be excluded.
     * @param skipContextPaths the context paths
     */
    public void setSkipContextPaths(String skipContextPaths) {
        this.skipContextPaths.addAll(Arrays.asList(skipContextPaths.trim().split(",")));
    }
}
