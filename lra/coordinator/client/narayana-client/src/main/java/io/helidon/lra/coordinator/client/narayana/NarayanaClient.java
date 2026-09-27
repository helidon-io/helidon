/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.LruCache;
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

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static io.helidon.lra.coordinator.client.CoordinatorClient.CONF_KEY_COORDINATOR_TRUSTED_URLS;
import static java.lang.System.Logger.Level.DEBUG;

/**
 * Narayana LRA coordinator client.
 */
public class NarayanaClient implements CoordinatorClient {
    private static final HeaderName LRA_HTTP_CONTEXT_HEADER = HeaderNames.create(LRA.LRA_HTTP_CONTEXT_HEADER);
    private static final HeaderName LRA_HTTP_RECOVERY_HEADER = HeaderNames.create(LRA.LRA_HTTP_RECOVERY_HEADER);

    private static final System.Logger LOGGER = System.getLogger(NarayanaClient.class.getName());

    private static final int RETRY_ATTEMPTS = 5;
    private static final int LEARNED_LRA_CAPACITY = 10_000;
    private static final String QUERY_PARAM_CLIENT_ID = "ClientID";
    private static final String QUERY_PARAM_TIME_LIMIT = "TimeLimit";
    private static final String QUERY_PARAM_PARENT_LRA = "ParentLRA";
    private static final String HEADER_LINK = "Link";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private final Set<CoordinatorBase> configuredTrustedCoordinatorBases = ConcurrentHashMap.newKeySet();
    private final LruCache<CoordinatorTarget, Boolean> trustedLraIds = LruCache.create(LEARNED_LRA_CAPACITY);
    private Supplier<URI> coordinatorUriSupplier;
    private Duration coordinatorTimeout;
    private Retry retry;

    static URI parseBaseUri(String lraUri) {
        CoordinatorTarget target = CoordinatorTarget.fromLraId(URI.create(lraUri));
        return coordinatorUri(target.scheme(), target.host(), target.port(), target.parentPath(), null);
    }

    @Override
    public void init(Supplier<URI> coordinatorUriSupplier, Duration timeout) {
        this.coordinatorUriSupplier = coordinatorUriSupplier;
        this.coordinatorTimeout = timeout;
        this.configuredTrustedCoordinatorBases.clear();
        this.trustedLraIds.clear();
        CoordinatorBase.fromBaseUri(coordinatorUriSupplier.get());
        ConfigProvider.getConfig()
                .getOptionalValues(CONF_KEY_COORDINATOR_TRUSTED_URLS, String.class)
                .orElseGet(List::of)
                .stream()
                .map(String::trim)
                .map(URI::create)
                .map(CoordinatorBase::fromBaseUri)
                .forEach(configuredTrustedCoordinatorBases::add);
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
                .map(p -> {
                    validateCoordinator(p);
                    return parseBaseUri(p.toASCIIString());
                })
                .orElseGet(coordinatorUriSupplier);

        logF("Starting LRA, coordinator: {0}/start, clientId: {1}, timeout: {2}", baseUri, clientID, timeout);
        return retry.invoke(() -> {
            HttpClientRequest req = prepareWebClient(baseUri)
                    .post()
                    .followRedirects(false)
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
                trustedLraIds.put(CoordinatorTarget.fromLraId(lraId), Boolean.TRUE);
                logF("LRA started - LRAID: {0} parent: {1}", lraId, parentLRA);
                return lraId;

            } catch (Exception e) {
                throw connectionError("Unable to start LRA", e);
            }
        });
    }

    @Override
    public void cancel(URI lraId, PropagatedHeaders headers) {
        validateCoordinator(lraId);
        logF("Cancelling LRA {0}", lraId);
        retry.<Void>invoke(() -> {
            var req = prepareWebClient(lraId)
                    .put()
                    .followRedirects(false)
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
        validateCoordinator(lraId);
        logF("Closing LRA {0}", lraId);
        retry.invoke(() -> {
                    var req = prepareWebClient(lraId)
                            .put()
                            .followRedirects(false)
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
                                    return null;
                                }
                                throw connectionError("Unable to close lra - LRAID: " + lraId, res.status().code());
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
        validateCoordinator(lraId);
        String links = compensatorLinks(p);

        logF("Joining participant with LRA {0}", lraId);
        return retry.invoke(() -> {
            var req = prepareWebClient(lraId)
                    .put()
                    .followRedirects(false)
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
        validateCoordinator(lraId);
        logF("Leaving LRA {0} participant: {1}", lraId, p);
        retry.invoke(() -> {
            var req = prepareWebClient(lraId)
                    .put()
                    .followRedirects(false)
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
        validateCoordinator(lraId);
        logF("Checking status of LRA {0}", lraId);
        return retry.invoke(() -> {
            var req = prepareWebClient(lraId)
                    .get()
                    .followRedirects(false)
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

    private void validateCoordinator(URI lraId) {
        final CoordinatorTarget target;
        try {
            target = CoordinatorTarget.fromLraId(lraId);
        } catch (IllegalArgumentException e) {
            throw connectionError("Untrusted LRA coordinator", 412);
        }

        final CoordinatorBase currentCoordinatorBase;
        try {
            currentCoordinatorBase = CoordinatorBase.fromBaseUri(coordinatorUriSupplier.get());
        } catch (IllegalArgumentException e) {
            throw connectionError("Invalid LRA coordinator", e);
        }
        if (trustedLraIds.get(target).isEmpty()
                && !currentCoordinatorBase.matches(target)
                && configuredTrustedCoordinatorBases.stream().noneMatch(base -> base.matches(target))) {
            throw connectionError("Untrusted LRA coordinator", 412);
        }
    }

    private WebClient prepareWebClient(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort() == -1 ? (HTTPS.equals(scheme) ? 443 : 80) : uri.getPort();
        URI coordinatorUri = coordinatorUri(scheme,
                                            normalizeHost(uri.getHost()),
                                            port,
                                            Optional.ofNullable(uri.getRawPath()).filter(path -> !path.isEmpty()).orElse("/"),
                                            uri.getRawQuery());
        return WebClient.builder()
                .baseUri(coordinatorUri)
                .followRedirects(false)
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

    private static CoordinatorTarget coordinatorTarget(URI uri, boolean lraId) {
        if (uri == null || !uri.isAbsolute() || uri.isOpaque()) {
            throw new IllegalArgumentException("Coordinator URI must be absolute and hierarchical");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int defaultPort = switch (scheme) {
            case HTTP -> 80;
            case HTTPS -> 443;
            default -> throw new IllegalArgumentException("Coordinator URI must use HTTP or HTTPS");
        };

        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Coordinator URI has an unsupported authority or fragment");
        }
        if (!lraId && uri.getRawQuery() != null) {
            throw new IllegalArgumentException("Coordinator base URI must not contain a query");
        }

        String path = Optional.ofNullable(uri.getRawPath()).filter(p -> !p.isEmpty()).orElse("/");
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (path.indexOf('\\') >= 0
                || path.indexOf(';') >= 0
                || lowerPath.contains("%2e")
                || lowerPath.contains("%2f")
                || lowerPath.contains("%3b")
                || lowerPath.contains("%5c")
                || !path.equals(URI.create(path).normalize().getRawPath())) {
            throw new IllegalArgumentException("Coordinator URI has an ambiguous path");
        }
        if (lraId && path.endsWith("/")) {
            throw new IllegalArgumentException("LRA identifier must have a final path segment");
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return new CoordinatorTarget(scheme,
                                     normalizeHost(host),
                                     uri.getPort() == -1 ? defaultPort : uri.getPort(),
                                     path,
                                     uri.getRawQuery());
    }

    private static String normalizeHost(String host) {
        if (!host.startsWith("[")) {
            return host.toLowerCase(Locale.ROOT);
        }
        int zoneStart = host.indexOf('%');
        if (zoneStart < 0) {
            return host.toLowerCase(Locale.ROOT);
        }
        return host.substring(0, zoneStart).toLowerCase(Locale.ROOT) + host.substring(zoneStart);
    }

    private static URI coordinatorUri(String scheme, String host, int port, String rawPath, String rawQuery) {
        return URI.create(scheme + "://" + host + ":" + port + rawPath
                                  + (rawQuery == null ? "" : "?" + rawQuery));
    }

    private record CoordinatorTarget(String scheme, String host, int port, String path, String query) {
        private static CoordinatorTarget fromLraId(URI lraId) {
            return coordinatorTarget(lraId, true);
        }

        private String parentPath() {
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash < 0) {
                throw new IllegalArgumentException("LRA identifier must have a coordinator path");
            }
            return lastSlash == 0 ? "/" : path.substring(0, lastSlash);
        }
    }

    private record CoordinatorBase(String scheme, String host, int port, String path) {
        private static CoordinatorBase fromBaseUri(URI uri) {
            CoordinatorTarget target = coordinatorTarget(uri, false);
            return new CoordinatorBase(target.scheme(), target.host(), target.port(), target.path());
        }

        private boolean matches(CoordinatorTarget target) {
            if (!scheme.equals(target.scheme()) || !host.equals(target.host()) || port != target.port()) {
                return false;
            }
            return path.equals(target.parentPath());
        }
    }
}
