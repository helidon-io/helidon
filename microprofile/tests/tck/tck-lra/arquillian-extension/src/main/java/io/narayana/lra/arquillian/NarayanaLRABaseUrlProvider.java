/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package io.narayana.lra.arquillian;

import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;
import org.jboss.logging.Logger;

public class NarayanaLRABaseUrlProvider implements ResourceProvider {
    private static final Logger log = Logger.getLogger(NarayanaLRABaseUrlProvider.class);

    @Override
    public Object lookup(ArquillianResource resource, Annotation... qualifiers) {
        log.debugf("base url lookup executed with resource '%s' and qualifiers '%s'", resource, qualifiers);
        String hostname = System.getProperty("lra.tck.suite.host");
        String port = System.getProperty("lra.tck.suite.port");
        if (hostname == null) {
            throw new IllegalStateException("Provide parameter 'lra.tck.suite.host' for TCK suite knows where to connect to");
        }
        if (port == null) {
            throw new IllegalStateException("Provide parameter 'lra.tck.suite.port' for TCK suite knows the porte where to connect to");
        }

        try {
            return new URL(String.format("http://%s:%s", hostname, port));
        } catch (MalformedURLException murle) {
            throw new IllegalStateException(String.format("Cannot compose URL from host '%s' and port '%s' "
                    + "provided with testsuite parameters 'lra.tck.suite.host' and 'lra.tck.suite.port'.", hostname, port), murle);
        }
    }

    @Override
    public boolean canProvide(Class<?> type) {
        if (log.isDebugEnabled()) {
            log.debugf("Checking to provide if type '%s' is assignable to URL");
        }
        return type.isAssignableFrom(URL.class);
    }

}
