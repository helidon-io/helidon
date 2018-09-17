/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */

package io.helidon.microprofile.health.tck;

import java.lang.annotation.Annotation;
import java.net.URI;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;

/**
 * Lets arquillian know what host and port to test with.
 */
public class HealthResourceProvider implements ResourceProvider {

    @Override
    public Object lookup(ArquillianResource arquillianResource, Annotation... annotations) {
        return URI.create("http://localhost:8080");
    }

    @Override
    public boolean canProvide(Class<?> type) {
        return type.isAssignableFrom(URI.class);
    }

}
