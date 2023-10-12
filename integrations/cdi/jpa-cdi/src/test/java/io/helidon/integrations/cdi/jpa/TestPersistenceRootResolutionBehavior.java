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
package io.helidon.integrations.cdi.jpa;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class TestPersistenceRootResolutionBehavior {

    @Test
    void testUriResolution() throws IOException, URISyntaxException {
        Enumeration<URL> pxmls = Thread.currentThread().getContextClassLoader().getResources("META-INF/persistence.xml");
        assertThat(pxmls.hasMoreElements(), is(true));
        URL pxml = pxmls.nextElement();
        assertThat(pxmls.hasMoreElements(), is(false));
        URI pxmlUri = pxml.toURI();
        assertThat(pxmlUri.isAbsolute(), is(true));
        URI upOneLevel = pxmlUri.resolve("..");
        assertThat(upOneLevel.isAbsolute(), is(true));
        upOneLevel.toURL(); // make sure this doesn't blow up
    }

    @Test
    void testJarStuff() throws IOException, URISyntaxException {
        URI pxmlUri = URI.create("jar:file:///nonexistent/example.jar!/META-INF/persistence.xml");
        assertThat(pxmlUri.isOpaque(), is(true));
        assertThat(pxmlUri.isAbsolute(), is(true)); // implied by opacity
        String ssp = pxmlUri.getSchemeSpecificPart();
        assertThat(ssp, is("file:///nonexistent/example.jar!/META-INF/persistence.xml"));
        URI sspUri = URI.create(ssp);
        assertThat(sspUri.isAbsolute(), is(true));
        assertThat(sspUri.isOpaque(), is(false));
        URI up = URI.create("..");
        assertThat(up.isAbsolute(), is(false));
        assertThat(up.isOpaque(), is(false));
        // Opacity means this should return, simply, and surprisingly to a quick glance, "..".
        assertThat(pxmlUri.resolve(".."), is(up));
        assertThat(URI.create("..").isOpaque(), is(false));
    }

}
