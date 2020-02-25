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
package io.helidon.tests.integration.mp.ws.services;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import static javax.interceptor.Interceptor.Priority.APPLICATION;

/**
 * Simple message body writer for HTML content.
 */
// can't make it ApplicationScoped yet as it needs to be registered explicitly
// using Dependent scope in order to avoid generic type argument erasure by Weld proxy
//@ApplicationScoped
@Provider
@Produces(MediaType.TEXT_HTML)
public class HtmlWriter implements MessageBodyWriter<String> {

    @Inject
    @ConfigProperty(name = "html.tag")
    private String tag;

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return String.class.equals(type) && MediaType.TEXT_HTML_TYPE.equals(mediaType);
    }

    @Override
    public void writeTo(String s,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders,
                        OutputStream out) throws IOException, WebApplicationException {
        Writer writer = new OutputStreamWriter(out);
        writer.write('<');
        writer.write(tag);
        writer.write('>');
        writer.write(s);
        writer.write("</");
        writer.write(tag);
        writer.write('>');
        writer.flush();
        writer.close();
    }
}
