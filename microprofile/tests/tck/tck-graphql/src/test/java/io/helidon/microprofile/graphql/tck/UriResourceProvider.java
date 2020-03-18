/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.tck;

import java.lang.annotation.Annotation;
import java.net.URI;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;

/**
 * TCKs use addition when creating URL for a client. The default Arquillian implementation returns url without the trailing
 * /.
 */
public class UriResourceProvider implements ResourceProvider {
    @Override
    public Object lookup(ArquillianResource arquillianResource, Annotation... annotations) {
        return URI.create("http://localhost:8080/");
    }

    @Override
    public boolean canProvide(Class<?> type) {
        return URI.class.isAssignableFrom(type);
    }
}
