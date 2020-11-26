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

import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;

/**
 * Extension defined under <code>resources/META-INF/org.jboss.arquillian.core.spi.LoadableExtension</code>
 * to be loaded by Arquillian.<br/>
 * The services here extends the Arquillian functionality for being able to run the LRA tests with Narayana.
 */
public class ArquillianLRAExtension implements LoadableExtension {

   @Override
   public void register(ExtensionBuilder builder) {
       builder.service(AuxiliaryArchiveAppender.class, ConfigAuxiliaryArchiveAppender.class);
       builder.service(ResourceProvider.class, NarayanaLRABaseUrlProvider.class);
   }

}
