/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */

package io.helidon.microprofile.health.tck;

import org.jboss.arquillian.container.test.impl.enricher.resource.URIResourceProvider;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;

/**
 * LoadableExtension that will load the HealthResourceProvider.
 */
public class HealthExtension implements LoadableExtension {
    @Override
    public void register(ExtensionBuilder extensionBuilder) {
        extensionBuilder.override(ResourceProvider.class, URIResourceProvider.class, HealthResourceProvider.class);
    }
}
