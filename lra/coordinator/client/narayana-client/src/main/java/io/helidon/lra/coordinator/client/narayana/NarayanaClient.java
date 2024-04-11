/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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
package io.helidon.lra.coordinator.client.narayana;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.socket.SocketOptions;
import io.helidon.faulttolerance.Retry;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.CoordinatorConnectionException;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.lra.coordinator.client.PropagatedHeaders;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Narayana LRA coordinator client.
 */
public class NarayanaClient implements CoordinatorClient {
    private static final HeaderName LRA_HTTP_CONTEXT_HEADER = HeaderNames.create(LRA.LRA_HTTP_CONTEXT_HEADER);
    private static final HeaderName LRA_HTTP_RECOVERY_HEADER = HeaderNames.create(LRA.LRA_HTTP_RECOVERY_HEADER);

    private static final System.Logger LOGGER = System.getLogger(NarayanaClient.class.getName());

    private static final int RETRY_ATTEMPTS = 5;
    private static final String QUERY_PARAM_CLIENT_ID = "ClientID";
    private static final String QUERY_PARAM_TIME_LIMIT = "TimeLimit";
    private static final String QUERY_PARAM_PARENT_LRA = "ParentLRA";
    private static final String HEADER_LINK = "Link";
    private static final Pattern LRA_ID_PATTERN = Pattern.compile("(.*)/([^/?]+).*");

    private Supplier<URI> coordinatorUriSupplier;
    private Duration coordinatorTimeout;
    private Retry retry;

    static URI parseBaseUri(String lraUri) {
        Matcher m = LRA_ID_PATTERN.matcher(lraUri);
        if (!m.matches()) {
            //LRA id uri format
            throw new RuntimeException("Error when parsing lra uri: " + lraUri);
        }
        return URI.create(m.group(1));
    }

    @Override
    public void init(Supplier<URI> coordinatorUriSupplier, Duration timeout) {
        this.coordinatorUriSupplier = coordinatorUriSupplier;
        this.coordinatorTimeout = timeout;
        this.retry = Retry.builder()
                .overallTimeout(timeout)
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                        .calls(RETRY_ATTEMPTS)
                        .build())
                .build();
    }

    @Override
    public URI start(String clientID, PropagatedHeaders headers, long timeout) {
        return startInternal(null, clientID, headers, timeout);
    }

    @Override
    public URI start(URI parentLRAUri, String clientID, PropagatedHeaders headers, long timeout) {
        return startInternal(parentLRAUri, clientID, headers, timeout);
    }

    private URI startInternal(URI parentLRA, String clientID, PropagatedHeaders headers, long timeout) {
        // We need to call coordinator which knows parent LRA
        URI baseUri = Optional.ofNullable(parentLRA)
                .map(p -> parseBaseUri(p.toASCIIString()))
                .orElse(coordinatorUriSupplier.get());

        logF("Starting LRA, coordinator: {0}/start, clientId: {1}, timeout: {2}", baseUri, clientID, timeout);
        return retry.invoke(() -> {
            HttpClientRequest req = prepareWebClient(baseUri)
                    .post()
                    .path("start")
                    .headers(copyHeaders(headers)) // header propagation
                    .queryParam(QUERY_PARAM_CLIENT_ID, Optional.ofNullable(clientID).orElse(""))
                    .queryParam(QUERY_PARAM_TIME_LIMIT, String.valueOf(timeout))
                    .queryParam(QUERY_PARAM_PARENT_LRA, parentLRA == null ? "" : parentLRA.toASCIIString());

            try (HttpClientResponse res = req.request()) {
                Status status = res.status();
                if (status.code() != 201) {
                    throw connectionError("Unexpected response " + status + " from coordinator "
                            + req.resolvedUri() + ": " + res.as(String.class), null);
                }
                //propagate supported headers from coordinator
                headers.scan(res.headers().toMap());
                URI lraId = res.headers().first(HeaderNames.LOCATION)
                        // TMM doesn't send lraId as LOCATION
                        .or(() -> res.headers().first(LRA_HTTP_CONTEXT_HEADER))
                        .map(URI::create)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Coordinator needs to return lraId either as 'Location' or "
                                                + "'Long-Running-Action' header."));
                logF("LRA started - LRAID: {0} parent: {1}", lraId, parentLRA);
                return lraId;

            } catch (Exception e) {
                throw connectionError("Unable to start LRA", e);
            }
        });
    }

    @Override
    public void cancel(URI lraId, PropagatedHeaders headers) {
        logF("Cancelling LRA {0}", lraId);
        retry.<Void>invoke(() -> {
            var req = prepareWebClient(lraId)
                    .put()
                    .path("/cancel")
                    .headers(copyHeaders(headers)); // header propagation

            try (var res = req.request()) {
                switch (res.status().family()) {
                    case SUCCESSFUL:
                        logF("LRA cancelled - LRAID: {0}", lraId);
                        return null;
                    case CLIENT_ERROR:
                        logF("Unexpected client error during LRA cancel - LRAID: {0}, Status: {1}", lraId, res.status().code());
                        return null;
                    default:
                        throw connectionError("Unable to cancel lra " + lraId, res.status().code());
                }
            } catch (Exception e) {
                throw connectionError("Unable to cancel LRA " + lraId, e);
            }
        });
    }

    @Override
    public void close(URI lraId, PropagatedHeaders headers) {
        logF("Closing LRA {0}", lraId);
        retry.invoke(() -> {
                    var req = prepareWebClient(lraId)
                            .put()
                            .path("/close")
                            .headers(copyHeaders(headers)); // header propagation

                    try (var res = req.request()) {
                        switch (res.status().family()) {
                            case SUCCESSFUL:
                                logF("LRA closed - LRAID: {0}", lraId);
                                return null;
                            case CLIENT_ERROR:
                            default:
                                // 404 can happen when coordinator already cleaned terminated lra's
                                if (List.of(410, 404).contains(res.status().code())) {
                                    logF("LRA already closed - LRAID: {0}", lraId);
                                }
                                return connectionError("Unable to close lra - LRAID: " + lraId, res.status().code());
                        }
                    } catch (Exception e) {
                        throw connectionError("Unable to close LRA " + lraId, e);
                    }
                }
        );
    }

    @Override
    public Optional<URI> join(URI lraId,
                              PropagatedHeaders headers,
                              long timeLimit,
                              Participant p) {
        String links = compensatorLinks(p);

        logF("Joining LRA {0} with links: {1}", lraId, links);
        return retry.invoke(() -> {
            var req = prepareWebClient(lraId)
                    .put()
                    .queryParam(QUERY_PARAM_TIME_LIMIT, String.valueOf(timeLimit))
                    .headers(h -> {
                        // links are expected either in header
                        h.add(HeaderValues.createCached(HEADER_LINK, links));
                        // header propagation
                        headers.toMap().forEach((name, value) -> h.set(HeaderNames.create(name), value));
                    });

            try (var res = req.submit(links)) {
                switch (res.status().code()) {
                    case 412:
                        throw connectionError(req.resolvedUri()
                                + " Too late to join LRA - LRAID: " + lraId, 412);
                    case 404:
                        // Narayana returns 404 for already terminated lras
                        throw connectionError("Not found " + lraId, 404);
                    case 410:
                        throw connectionError("Not found " + lraId, 410);
                    case 200:
                        logF("Participant {0} joined - LRAID: {1}", p, lraId);
                        return res.headers()
                                .first(LRA_HTTP_RECOVERY_HEADER)
                                .map(URI::create);
                    default:
                        throw connectionError("Unexpected coordinator response ", res.status().code());
                }
            } catch (Exception e) {
                throw connectionError("Unable to join LRA " + lraId, e);
            }
        });
    }

    @Override
    public void leave(URI lraId, PropagatedHeaders headers, Participant p) {
        logF("Leaving LRA {0} participant: {1}", lraId, p);
        retry.invoke(() -> {
            var req = prepareWebClient(lraId)
                    .put()
                    .path("/remove")
                    .headers(copyHeaders(headers)); // header propagation

            try (var res = req.submit(compensatorLinks(p))) {
                switch (res.status().code()) {
                    case 404:
                        LOGGER.log(Level.WARNING,
                                "Participant {0} leaving LRA - Coordinator can't find id - LRAID: {1}", p, lraId);
                        return null;
                    case 200:
                        logF("Participant {0} left - LRAID: {1}", p, lraId);
                        return null;
                    default:
                        throw new IllegalStateException("Unexpected coordinator response " + res.status());
                }
            } catch (Exception e) {
                throw connectionError("Unable to leave LRA " + lraId, e);
            }
        });
    }

    @Override
    public LRAStatus status(URI lraId, PropagatedHeaders headers) {
        logF("Checking status of LRA {0}", lraId);
        return retry.invoke(() -> {
            var req = prepareWebClient(lraId)
                    .get()
                    .path("/status")
                    .headers(copyHeaders(headers)); // header propagation
            try (var res = req.request()) {
                switch (res.status().code()) {
                    case 404:
                        LOGGER.log(Level.WARNING, "Status LRA - Coordinator can't find id - LRAID: " + lraId);
                        return LRAStatus.Closed;
                    case 200:
                    case 202:
                        var status = res.as(LRAStatus.class);
                        logF("LRA status {0} retrieved - LRAID: {1}", status, lraId);
                        return status;
                    default:
                        throw new IllegalStateException("Unexpected coordinator response " + res.status());
                }
            } catch (Exception e) {
                throw connectionError("Unable to retrieve LRA status of " + lraId, e);
            }
        });
    }

    private WebClient prepareWebClient(URI uri) {
        return WebClient.builder()
                .baseUri(uri)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(coordinatorTimeout)
                        .readTimeout(coordinatorTimeout)
                        .build())
                .mediaContext(MediaContext.builder()
                        .addMediaSupport(new LraStatusSupport())
                        .build())
                .build();
    }

    /**
     * Narayana accepts participant's links as RFC 5988 {@code jakarta.ws.rs.core.Link}s delimited by commas.
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

    private Consumer<ClientRequestHeaders> copyHeaders(PropagatedHeaders headers) {
        return wcHeaders -> {
            headers.toMap().forEach((key, value) -> wcHeaders.set(HeaderNames.create(key), value));
        };
    }

    private CoordinatorConnectionException connectionError(String message, int status) {
        LOGGER.log(Level.WARNING, message);
        return new CoordinatorConnectionException(message, status);
    }

    private CoordinatorConnectionException connectionError(String message, Throwable cause) {
        LOGGER.log(Level.WARNING, message, cause);
        if (cause instanceof CoordinatorConnectionException) {
            return (CoordinatorConnectionException) cause;
        }
        return new CoordinatorConnectionException(message, cause, 500);
    }

    private void logF(String msg, Object... params) {
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, msg, params);
        }
    }
}

