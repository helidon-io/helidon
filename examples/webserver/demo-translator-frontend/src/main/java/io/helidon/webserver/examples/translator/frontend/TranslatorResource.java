/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.translator.frontend;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.common.CollectionsHelper;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.opentracing.Opentraceable;
import io.helidon.webserver.opentracing.OpentracingClientFilter;

import org.glassfish.jersey.server.Uri;

/**
 * Translator frontend resource.
 */
@Path("translator")
public class TranslatorResource {

    private static final Logger LOGGER = Logger.getLogger(TranslatorResource.class.getName());

    private static final String TRANSLATOR_VERSION = "TRANSLATOR_VERSION";
    private static final String TRANSLATOR_BACKEND = "http://{backend-hostname}:{backend-port}";

    /** The backend port named injection qualifier. */
    public static final String BACKEND_PORT = "backend-port";
    /** The backend hostname named injection qualifier. */
    public static final String BACKEND_HOSTNAME = "backend-hostname";

    private List<String> languages = CollectionsHelper.listOf("czech", "spanish", "chinese", "hindi");

    private List<String> languagesV2 = CollectionsHelper.listOf("italian", "french");

    @Inject
    private ServerRequest request;

    @Inject @Named(BACKEND_PORT)
    private Integer backendPort;

    @Inject @Named(BACKEND_HOSTNAME)
    private String backendHostname;

    /**
     * Get the translated text as a html page.
     *
     * @param query the text to translate
     * @param translator the backend translator to use
     * @return a page with the translated text
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getHtml(@QueryParam("q") String query,
                          @Uri(TRANSLATOR_BACKEND) @Opentraceable WebTarget translator) {
        return "<!doctype html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "    <meta charset=\"UTF-8\">\n"
                + "    <title>Translator Frontend</title>\n"
                + "</head>\n"
                + "\n"
                + "<body>\n"
                + "<h2>Translator Frontend</h2>\n"
                + getText(query, translator).replace("\n", "<br />")
                + "</body>\n"
                + "</html>\n";
    }

    /**
     * Get method to translate a given query.
     *
     * @param query      the query to translate
     * @param translator the backend translator to use
     * @return the translated text
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getText(@QueryParam("q") String query,
                          @Uri(TRANSLATOR_BACKEND) @Opentraceable WebTarget translator) {
        try {
            translator = translator.resolveTemplate("backend-port", backendPort)
                                   .resolveTemplate("backend-hostname", backendHostname)
                                   .property(OpentracingClientFilter.SERVER_REQUEST_PROPERTY_NAME, request)
                                   .path("translator").queryParam("q", query);
            final StringBuilder sb = new StringBuilder();

            // Add languages for version 2
            final String version = System.getenv(TRANSLATOR_VERSION);
            if ("2".equals(version)) {
                languages.addAll(languagesV2);
            }

            for (String language : languages) {
                final WebTarget lang = translator.queryParam("lang", language);
                LOGGER.info("GET " + lang.getUri());

                translate(lang, sb);
            }

            return sb.toString();
        } catch (ProcessingException pe) {
            LOGGER.log(Level.WARNING, "Problem to call translator frontend.", pe);
            return "Translator backend service isn't available.";
        } catch (Exception e) {
            final StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));

            return stringWriter.toString();
        }
    }

    private void translate(final WebTarget backend, final StringBuilder sb) {
        if (Main.isSecurityDisabled()) {
            LOGGER.info("[dev-local] Backend is called without security.");
            translateImpl(backend, sb);
        } else {
            // TODO add security
            //SecurityModule.runAsSystem(() -> translateImpl(backend, sb));
        }
    }

    private void translateImpl(final WebTarget backend, final StringBuilder sb) {
        Response response = backend.request().get();
        final String result;
        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            result = response.readEntity(String.class);
        } else {
            result = "Error: " + response.readEntity(String.class);
        }
        sb.append(result).append('\n');
    }

    /**
     * An example of a post method that simply sleeps.
     *
     * @return {@code post!} string
     */
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String post() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "post!";
    }

    /**
     * Marathon health check.
     * <p>
     * This should check the health of state/storage service (if present).
     *
     * @return HTTP 2xx response.
     */
    @GET
    @Path("health")
    public Response health() {
        return Response.noContent().build();
    }
}
