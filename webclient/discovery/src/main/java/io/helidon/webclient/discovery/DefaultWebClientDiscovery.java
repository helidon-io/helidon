/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.discovery;

import java.lang.System.Logger;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.common.uri.UriInfo;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.getLogger;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * A {@link WebClientDiscovery} built and returned by the {@link WebClientDiscovery#create(WebClientDiscoveryConfig)}
 * method.
 *
 * @see #handle(Chain, WebClientServiceRequest)
 * @see WebClientDiscovery#handle(Chain, WebClientServiceRequest)
 * @see WebClientDiscovery#create(WebClientDiscoveryConfig)
 */
final class DefaultWebClientDiscovery implements WebClientDiscovery {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = getLogger(DefaultWebClientDiscovery.class.getName());


    /*
     * Instance fields.
     */


    private final WebClientDiscoveryConfig prototype;

    // Immutable. Thread-safe.
    private final Collection<? extends Entry<? extends URI, ? extends String>> discoveryNames;


    /*
     * Constructors.
     */


    DefaultWebClientDiscovery(WebClientDiscoveryConfig prototype) {
        super();
        this.prototype = requireNonNull(prototype, "prototype");
        Map<String, URI> prefixUris = prototype.prefixUris();
        List<Entry<URI, String>> l = new ArrayList<>(prefixUris.size());
        for (Entry<String, URI> e : prefixUris.entrySet()) {
            l.add(new SimpleEntry<>(uri(e.getValue().normalize()), e.getKey()));
        }
        // Sort from most-specific hierarchical URI to least-specific. Total order.
        l.sort(Comparator.<Entry<URI, String>>comparingInt(e -> {
                    int slashCount = 0;
                    String path = e.getKey().getPath();
                    if (path != null) {
                        for (int i = 0; i < path.length(); i++) {
                            if (path.charAt(i) == '/') {
                                ++slashCount;
                            }
                        }
                    }
                    return slashCount;
                })
            .thenComparing(e -> e.getKey().getPath())
            .thenComparing(Entry::getValue)
            .reversed());
        this.discoveryNames = unmodifiableList(l);
        LOGGER.log(DEBUG, "Discovery names: {0}", this.discoveryNames);
    }


    /*
     * Instance methods.
     */


    /**
     * Returns the {@link WebClientDiscoveryConfig} supplied to the invocation of the {@link
     * WebClientDiscovery#create(WebClientDiscoveryConfig)} method that created this {@link DefaultWebClientDiscovery}.
     *
     * @return a non-{@code null} {@link WebClientDiscoveryConfig}
     * @see WebClientDiscovery#create(WebClientDiscoveryConfig)
     */
    @Override // WebClientDiscovery (RuntimeType.Api<WebClientDiscoveryConfig>)
    public WebClientDiscoveryConfig prototype() {
        return this.prototype;
    }

    @Override // WebClientDiscovery (WebClientService)
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        ClientUri clientUri = request.uri();
        LOGGER.log(DEBUG, "Initial ClientUri: {0}", clientUri);

        DiscoveryRequest discoveryRequest = DiscoveryRequest.of(this.discoveryNames, clientUri).orElse(null);
        if (discoveryRequest == null) {
            LOGGER.log(DEBUG, "No discovery needed for {0}", clientUri);
            return chain.proceed(request);
        }
        LOGGER.log(DEBUG, "DiscoveryRequest: {0}", discoveryRequest);

        URI discoveredUri = this.prototype()
            .discovery()
            .uris(discoveryRequest.discoveryName(), discoveryRequest.defaultUri())
            .getFirst()
            .uri();
        LOGGER.log(DEBUG, "URI discovered for {0}: {1}", discoveryRequest.discoveryName(), discoveredUri);

        // (Edge case. Eureka in particular does not contractually guarantee whether a URI it returns will be opaque or
        // not. An opaque URI could conceivably be OK in some possible worlds, but ClientUri doesn't handle opaque
        // URIs. Just skip it.)
        if (discoveredUri.isOpaque()) {
            LOGGER.log(DEBUG, "Discarding discovered opaque URI {0}; ClientUri does not support opaque URIs", discoveredUri);
            return chain.proceed(request);
        }

        // Resolve the extra path against the discovered URI, deliberately using fully defined
        // java.net.URI#resolve(String) semantics (which reifies RFC 2396 semantics
        // (https://www.rfc-editor.org/rfc/rfc2396#section-5.2)).
        LOGGER.log(DEBUG, "Resolving {0} against {1}", discoveryRequest.extraPath(), discoveredUri);
        discoveredUri = discoveredUri.resolve(discoveryRequest.extraPath());
        LOGGER.log(DEBUG, "Resolution result: {0}", discoveredUri);

        // Install (raw) path, (possibly) new scheme, host, and port.  Deliberately leave existing query and fragment
        // alone.
        clientUri.path(discoveredUri.getRawPath());
        String discoveredScheme = discoveredUri.getScheme();
        if (discoveredScheme != null) {
            clientUri.scheme(discoveredScheme);
        }
        String discoveredHost = discoveredUri.getHost();
        if (discoveredHost != null) {
            clientUri.host(discoveredHost);
        }
        int discoveredPort = discoveredUri.getPort();
        if (discoveredPort >= 0) {
            clientUri.port(discoveredPort);
        }

        LOGGER.log(DEBUG, "Final ClientUri: {0}", clientUri);
        return chain.proceed(request);
    }

    Collection<? extends Entry<? extends URI, ? extends String>> discoveryNames() {
        return this.discoveryNames;
    }


    /*
     * Static methods.
     */


    /**
     * Creates and returns a {@link URI} suitable for the supplied {@link URI} following logic used elsewhere within
     * Helidon Web Client.
     *
     * <p>For reasons described below, this method behaves as if it executes the following (error handling elided):</p>
     *
     * <blockquote><pre>return ClientUri.create(uri).toUri();</pre></blockquote>
     *
     * <p>That, in turn, is equivalent to:</p>
     *
     * <blockquote><pre>return ClientUri.create().resolve(uri);</pre></blockquote>
     *
     * <p>Internally, {@link ClientUri#create()} creates a new, "empty" {@link ClientUri} instance. The act of creating
     * a new {@link ClientUri} instance, in turn, internally creates a {@link UriInfo.Builder} that is initialized with
     * its default values. That {@linkplain UriInfo.Builder builder} is used during {@linkplain ClientUri#resolve(URI)
     * resolution}. This means that default values like {@linkplain io.helidon.common.uri.URIInfo.BuilderBase#port() the
     * default value for the <code>port</code>} can, and often do, appear in {@link URI}s {@linkplain
     * ClientUri#resolve(URI) resolved} from user-supplied {@link URI} instances even though those values were never
     * specified by the user.</p>
     *
     * <p>More concretely:</p>
     *
     * <ul>
     *
     * <li>A {@link URI} representing {@code http://example.com} passed to this method will result in a {@link URI}
     * representing {@code http://example.com:80/} (note the port and path)</li>
     *
     * <li>A {@link URI} representing {@code https://example.com} passed to this method will result in a {@link URI}
     * representing {@code https://example.com:443/} (note the port and path)</li>
     *
     * <li>A {@link URI} representing {@code path} passed to this method will result in a {@link URI} representing
     * {@code http://localhost:80/path} (note the scheme, host, port and path)</li>
     *
     * </ul>
     *
     * @param u a valid {@link URI}; must not be {@code null}
     * @return a {@link URI} representing the supplied {@link URI} following Helidon Web Client conventions; never
     * {@code null}
     * @exception NullPointerException if {@code u} is {@code null}
     * @exception IllegalArgumentException if {@code u} is not a valid URI representation
     * @see ClientUri#create()
     * @see ClientUri#create(URI)
     * @see ClientUri#resolve(URI)
     * @see io.helidon.uri.common.uri.UriInfo.BuilderBase#scheme()
     * @see io.helidon.uri.common.uri.UriInfo.BuilderBase#host()
     * @see io.helidon.uri.common.uri.UriInfo.BuilderBase#port()
     * @see io.helidon.uri.common.uri.UriInfo.BuilderBase#path()
     */
    private static URI uri(URI u) {
        return ClientUri.create(u).toUri();
    }


    /*
     * Inner and nested classes.
     */


    record DiscoveryRequest(String discoveryName, URI defaultUri, String extraPath) {

        static final Pattern KEY_PATTERN =
            Pattern.compile("^helidon-discovery-([^\\s]+)-(name|prefix-uri)$");

        static final Pattern WHITESPACE_AND_COMMAS_PATTERN = Pattern.compile("[\\s,]+");

        DiscoveryRequest {
            requireNonNull(discoveryName, "discoveryName");
            requireNonNull(defaultUri, "defaultUri");
            requireNonNull(extraPath, "extraPath");
        }

        static Optional<DiscoveryRequest> of(Collection<? extends Entry<? extends URI, ? extends String>> discoveryNames,
                                             UriInfo uriInfo) {
            if (discoveryNames.isEmpty()) {
                return Optional.empty();
            }
            // The only thing we need from the UriInfo is its notional URI. So why not just pass a java.net.URI to this
            // method instead of a UriInfo if that's all we actually need?  Because mutable UriInfo implementations,
            // like ClientUri, create (and supply, via toUri()) URIs in a very specific way throughout the WebClient
            // machinery, adding and altering values in some places (such as host and port and path), and we need to
            // maintain that behavior throughout this WebClientService implementation for overall fidelity with the rest
            // of WebClient.
            URI uriInfoUri = uriInfo.toUri().normalize();
            for (Entry<? extends URI, ? extends String> e : discoveryNames) {
                URI prefixUri = e.getKey();
                // Relativize the URI in flight against the prefix URI, using the well-defined semantics of
                // URI#relativize(String), yielding, if there is a match, in most cases, just a simple path.
                URI diff = prefixUri.relativize(uriInfoUri);
                if (diff == uriInfoUri) {
                    // Relativization "failed"; see
                    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html#relativize(java.net.URI). No
                    // problem; we just didn't match this particular prefix. Carry on to the next one.
                    LOGGER.log(DEBUG, "Ignoring {0} because it does not prefix {1}", prefixUri, uriInfoUri);
                    continue;
                }
                // We matched a prefix and now have the raw materials to query Discovery. Return early.
                return
                    Optional.of(new DiscoveryRequest(e.getValue(),
                                                     prefixUri,
                                                     diff.getRawPath()));
            }
            return Optional.empty();
        }

    }

}
