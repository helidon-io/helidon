/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.lra.coordinator.client.narayana;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.CoordinatorConnectionException;
import io.helidon.lra.coordinator.client.Headers;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

/**
 * Narayana LRA coordinator client.
 */
public class NarayanaClient implements CoordinatorClient {

    private static final Logger LOGGER = Logger.getLogger(NarayanaClient.class.getName());

    private static final int RETRY_ATTEMPTS = 5;
    private static final String QUERY_PARAM_CLIENT_ID = "ClientID";
    private static final String QUERY_PARAM_TIME_LIMIT = "TimeLimit";
    private static final String QUERY_PARAM_PARENT_LRA = "ParentLRA";
    private static final String HEADER_LINK = "Link";
    private static final Pattern LRA_ID_PATTERN = Pattern.compile("(.*)/([^/?]+).*");

    private URI coordinatorBaseUri;
    private Long coordinatorTimeout;
    private TimeUnit coordinatorTimeoutUnit;

    @Override
    public void init(String coordinatorUri, long timeout, TimeUnit timeoutUnit) {
        this.coordinatorBaseUri = URI.create(coordinatorUri);
        this.coordinatorTimeout = timeout;
        this.coordinatorTimeoutUnit = timeoutUnit;
    }

    @Override
    public URI start(String clientID, long timeout) {
        return startInternal(null, clientID, timeout);
    }

    @Override
    public URI start(URI parentLRAUri, String clientID, long timeout) {
        //String parentLra = URLEncoder.encode(parentLRAUri.toASCIIString(), StandardCharsets.UTF_8);
        return startInternal(parentLRAUri, clientID, timeout);
    }

    private URI startInternal(URI parentLRA, String clientID, long timeout) {
        RuntimeException lastError = new IllegalStateException();

        // We need to call coordinator which knows parent LRA
        URI baseUri = Optional.ofNullable(parentLRA)
                .map(p -> parseBaseUri(p.toASCIIString()))
                .orElse(coordinatorBaseUri);

        for (int i = 0; i < RETRY_ATTEMPTS; i++) {
            try {
                WebClientResponse response = prepareWebClient(baseUri)
                        .post()
                        .path("start")
                        .queryParam(QUERY_PARAM_CLIENT_ID, Optional.ofNullable(clientID).orElse(""))
                        .queryParam(QUERY_PARAM_TIME_LIMIT, String.valueOf(timeout))
                        .queryParam(QUERY_PARAM_PARENT_LRA, parentLRA == null ? "" : parentLRA.toASCIIString())
                        .submit()
                        .await(coordinatorTimeout, coordinatorTimeoutUnit);

                if (response.status().code() != 201) {
                    throw coordinationConnectionError("Unexpected response " + response.status() + " from coordinator "
                            + response.lastEndpointURI().toASCIIString()
                            + ": "
                            + (response.content().as(String.class).await(coordinatorTimeout, coordinatorTimeoutUnit)), 500, null);
                }
                // TRM doesn't send lraId as LOCATION
                String lraId = response.headers()
                        .location()
                        .map(URI::toASCIIString)
                        .or(() -> response.headers().first(LRA_HTTP_CONTEXT_HEADER))
                        .orElseThrow(() ->
                                new IllegalArgumentException("Coordinator needs to return lraId either as 'Location' or "
                                        + "'Long-Running-Action' header."));
                return URI.create(lraId); //parseLRAId(lraId);
            } catch (CoordinatorConnectionException e) {
                lastError = e;
            } catch (CompletionException | IllegalStateException e) {
                lastError = coordinationConnectionError("Unable to start LRA", 500, e);
            }
        }
        throw lastError;
    }

    @Override
    public void cancel(URI lraId) {
        RuntimeException lastError = new IllegalStateException();
        for (int i = 0; i < RETRY_ATTEMPTS; i++) {
            try {
                WebClientResponse response = WebClient.builder()
                        .keepAlive(false)
                        .baseUri(lraId.toASCIIString())
                        .build()
                        .put()
                        .path("/cancel")
                        .submit()
                        .await(coordinatorTimeout, coordinatorTimeoutUnit);

                switch (response.status().family()) {
                    case SUCCESSFUL:
                        return;
                    case CLIENT_ERROR:
                    default:
                        if (404 == response.status().code()) {
                            LOGGER.warning("Cancel LRA - Coordinator can't find LRAID: " + lraId.toASCIIString());
                            return;
                        }
                        LOGGER.warning("Cancelling LRA - Unable to call coordinator to cancel LRAID: " + lraId.toASCIIString());
                        throw new CoordinatorConnectionException("Unable to cancel lra " + lraId, response.status().code());
                }
            } catch (CompletionException | IllegalStateException e) {
                lastError = coordinationConnectionError("Unable to cancel LRA " + lraId.toASCIIString(), 500, e);
            }
        }
        throw lastError;
    }

    @Override
    public void close(URI lraId) {
        RuntimeException lastError = new IllegalStateException();
        for (int i = 0; i < RETRY_ATTEMPTS; i++) {
            try {
                WebClientResponse response = prepareWebClient(lraId)
                        .put()
                        .path("/close")
                        .submit()
                        .await(coordinatorTimeout, coordinatorTimeoutUnit);

                switch (response.status().family()) {
                    case SUCCESSFUL:
                        return;
                    case CLIENT_ERROR:
                    default:
                        if (410 == response.status().code()) {
                            // Already closed/cancelled
                            return;
                        }
                        LOGGER.warning("Closing LRA - Unable to call coordinator to close LRAID: " + lraId.toASCIIString());
                        throw new CoordinatorConnectionException("Unable to close lra " + lraId, response.status().code());
                }
            } catch (CoordinatorConnectionException e) {
                lastError = e;
            } catch (CompletionException | IllegalStateException e) {
                lastError = coordinationConnectionError("Unable to close LRA", 500, e);
            }
        }
        throw lastError;
    }

    @Override
    public Optional<URI> join(URI lraId,
                              long timeLimit,
                              Participant participant) {
        RuntimeException lastError = new IllegalStateException();
        for (int i = 0; i < RETRY_ATTEMPTS; i++) {
            try {
                String links = compensatorLinks(participant);
                WebClientResponse response = prepareWebClient(lraId)
                        .put()
                        .queryParam(QUERY_PARAM_TIME_LIMIT, String.valueOf(timeLimit))
                        .headers(h -> {
                            h.add(HEADER_LINK, links); // links are expected either in header
                            return h;
                        })
                        .submit(links)    // or as a body
                        .await(coordinatorTimeout, coordinatorTimeoutUnit);

                switch (response.status().code()) {
                    case 412:
                        throw new CoordinatorConnectionException("Too late to join LRA " + lraId, 412);
                    case 404:
                        throw new CoordinatorConnectionException("Not found " + lraId, 404);
                    case 200:
                        return response
                                .headers()
                                .first(LRA_HTTP_RECOVERY_HEADER)
                                .map(URI::create);
                    default:
                        throw new IllegalStateException("Unexpected coordinator response " + response.status());
                }

            } catch (CoordinatorConnectionException e) {
                lastError = e;
            } catch (CompletionException | IllegalStateException e) {
                lastError = coordinationConnectionError("Unable to join LRA " + lraId, 500, e);
            }
        }
        throw lastError;
    }

    @Override
    public void leave(URI lraId, Participant participant) {
        RuntimeException lastError = new IllegalStateException();
        for (int i = 0; i < RETRY_ATTEMPTS; i++) {
            try {
                WebClientResponse response = prepareWebClient(lraId)
                        .put()
                        .path("/remove")
                        .submit(compensatorLinks(participant))
                        .await(coordinatorTimeout, coordinatorTimeoutUnit);

                switch (response.status().code()) {
                    case 404:
                        LOGGER.warning("Leaving LRA - Coordinator can't find LRAID: " + lraId.toASCIIString());
                        break;
                    case 200:
                        LOGGER.log(Level.INFO, "Left LRA - " + lraId.toASCIIString());
                        return;
                    default:
                        throw new IllegalStateException("Unexpected coordinator response " + response.status());
                }

            } catch (CompletionException | IllegalStateException e) {
                lastError = coordinationConnectionError("Unable to leave LRA " + lraId, 500, e);
            }
        }
        throw lastError;
    }


    @Override
    public LRAStatus status(URI lraId) {
        RuntimeException lastError = new IllegalStateException();
        for (int i = 0; i < RETRY_ATTEMPTS; i++) {
            try {
                WebClientResponse response = prepareWebClient(lraId)
                        .get()
                        .path("/status")
                        .request()
                        .await(coordinatorTimeout, coordinatorTimeoutUnit);

                switch (response.status().code()) {
                    case 404:
                        LOGGER.warning("Status LRA - Coordinator can't find LRAID: " + lraId.toASCIIString());
                        return LRAStatus.Closed;
                    case 200:
                    case 202:
                        return response
                                .content()
                                .as(LRAStatus.class)
                                .await(coordinatorTimeout, coordinatorTimeoutUnit);
                    default:
                        throw new IllegalStateException("Unexpected coordinator response " + response.status());
                }

            } catch (CompletionException | IllegalStateException e) {
                lastError = new CoordinatorConnectionException("Unable to retrieve status of LRA " + lraId, e, 500);
            }
        }
        throw lastError;
    }

    private WebClient prepareWebClient(URI uri) {
        return WebClient.builder()
                .baseUri(uri)
                // Workaround for #3242
                .keepAlive(false)
                .addReader(new LraStatusReader())
                .build();
    }

    @Override
    public void preprocessHeaders(Headers headers) {
        // noop
    }

    /**
     * Narayana accepts participant's links as RFC 5988 {@code javax.ws.rs.core.Link}s delimited by commas.
     * <p>
     * Example:
     * <pre>{@code
     * <http://127.0.0.1:8080/lraresource/status>; rel="status"; title="status URI"; type="text/plain",
     * <http://127.0.0.1:8080/lraresource/compensate>; rel="compensate"; title="compensate URI"; type="text/plain",
     * <http://127.0.0.1:8080/lraresource/after>; rel="after"; title="after URI"; type="text/plain",
     * <http://127.0.0.1:8080/lraresource/complete>; rel="complete"; title="complete URI"; type="text/plain",
     * <http://127.0.0.1:8080/lraresource/forget>; rel="forget"; title="forget URI"; type="text/plain",
     * <http://127.0.0.1:8080/lraresource/leave>; rel="leave"; title="leave URI"; type="text/plain"
     * }</pre>
     *
     * @param p participant to serialize as links
     * @return links delimited by comma
     */
    private String compensatorLinks(Participant p) {
        return Map.of(
                        "compensate", p.compensate(),
                        "complete", p.complete(),
                        "forget", p.forget(),
                        "leave", p.leave(),
                        "after", p.after(),
                        "status", p.status()
                )
                .entrySet()
                .stream()
                .filter(e -> e.getValue().isPresent())
                // rfc 5988
                .map(e -> String.format("<%s>; rel=\"%s\"; title=\"%s\"; type=\"text/plain\"",
                        e.getValue().get(),
                        e.getKey(),
                        e.getKey() + " URI"))
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    static URI parseBaseUri(String lraUri) {
        Matcher m = LRA_ID_PATTERN.matcher(lraUri);
        if (!m.matches()) {
            //LRA id uri format
            throw new RuntimeException("Error when parsing lra uri: " + lraUri);
        }
        return URI.create(m.group(1));
    }

    private CoordinatorConnectionException coordinationConnectionError(String message, int status, Throwable cause) {
        LOGGER.log(Level.SEVERE, message, cause);
        return new CoordinatorConnectionException(message, cause, status);
    }
}

