/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.translator.frontend;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.http.BadRequestException;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Translator frontend resource.
 */
public final class TranslatorFrontendService implements HttpService {

    private static final Logger LOGGER = Logger.getLogger(TranslatorFrontendService.class.getName());
    private final Http1Client client;

    /**
     * Create a new instance.
     *
     * @param backendHostname backend service host
     * @param backendPort     backend service port
     */
    public TranslatorFrontendService(String backendHostname, int backendPort) {
        client = Http1Client.builder()
                .baseUri("http://" + backendHostname + ":" + backendPort)
                .build();
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get(this::getText);
    }

    private void getText(ServerRequest re, ServerResponse res) {
        try {
            String query = re.query()
                    .first("q")
                    .orElseThrow(() -> new BadRequestException("missing query parameter 'q'"));

            String language = re.query()
                    .first("lang")
                    .orElseThrow(() -> new BadRequestException("missing query parameter 'lang'"));

            try (Http1ClientResponse clientRes = client.get()
                    .queryParam("q", query)
                    .queryParam("lang", language)
                    .request()) {

                final String result;
                if (clientRes.status().family() == Status.Family.SUCCESSFUL) {
                    result = clientRes.entity().as(String.class);
                } else {
                    result = "Error: " + clientRes.entity().as(String.class);
                }
                res.send(result + "\n");
            }
        } catch (RuntimeException pe) {
            LOGGER.log(Level.WARNING, "Problem to call translator frontend.", pe);
            res.status(503).send("Translator backend service isn't available.");
        }
    }
}
