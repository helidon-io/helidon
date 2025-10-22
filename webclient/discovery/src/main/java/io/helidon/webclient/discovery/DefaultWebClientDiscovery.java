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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.uri.UriInfo;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;
import static java.lang.System.getLogger;
import static java.util.HashMap.newHashMap;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

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


    /*
     * Constructors.
     */


    DefaultWebClientDiscovery(WebClientDiscoveryConfig prototype) {
        super();
        this.prototype = requireNonNull(prototype, "prototype");
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
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "Initial ClientUri: " + clientUri);
            LOGGER.log(DEBUG, "Properties: " + request.properties());
        }

        DiscoveryRequest discoveryRequest = DiscoveryRequest.of(request.properties(), request.uri()).orElse(null);
        if (discoveryRequest == null) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "No discovery needed for " + request.uri());
            }
            return chain.proceed(request);
        }

        URI discoveredUri = this.prototype()
            .discovery()
            .uris(discoveryRequest.discoveryName(), discoveryRequest.defaultUri())
            .getFirst()
            .uri();
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "URI discovered for " + discoveryRequest.discoveryName() + ": " + discoveredUri);
        }

        // (Edge case. Eureka in particular does not contractually guarantee whether a URI it returns will be opaque or
        // not. An opaque URI could conceivably be OK in some possible worlds, but ClientUri doesn't handle opaque
        // URIs. Just skip it.)
        if (discoveredUri.isOpaque()) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "Discarding discovered URI "
                           + discoveredUri
                           + " because it is opaque and ClientUri does not support opaque URIs");
            }
            return chain.proceed(request);
        }

        // Resolve the extra path against the discovered URI, deliberately using fully defined
        // java.net.URI#resolve(String) semantics (which reifies RFC 2396 semantics
        // (https://www.rfc-editor.org/rfc/rfc2396#section-5.2)).
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "Resolving " + discoveryRequest.extraPath() + " against " + discoveredUri);
        }
        discoveredUri = discoveredUri.resolve(discoveryRequest.extraPath());
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "Resolution result: " + discoveredUri);
        }

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

        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "Final ClientUri: " + clientUri);
        }
        return chain.proceed(request);
    }


    /*
     * Static methods.
     */


    /**
     * Creates and returns a {@link URI} suitable for the supplied {@link String} representation.
     *
     * <p>For reasons described below, this method behaves as if it executes the following (error handling elided):</p>
     *
     * <blockquote><pre>return ClientUri.create(URI.create(s));</pre></blockquote>
     *
     * <p>That, in turn, is equivalent to:</p>
     *
     * <blockquote><pre>return ClientUri.create().resolve(URI.create(s));</pre></blockquote>
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
     * <li>a {@link String} representation of {@code http://example.com} when passed through this method will result in
     * a {@link URI} that is effectively {@code http://example.com:80/} (note the port and path)</li>
     *
     * <li>A {@link String} representation of {@code https://example.com} will become {@code https://example.com:443/}
     * (note the port and path)</li>
     *
     * <li>A {@link String representation of {@code path} will become {@code http://localhost:80/path} (note the scheme,
     * host, port and path)</li>
     *
     * </ul>
     *
     * <p>To ensure fidelity between these kinds of Helidon WebClient- and {@link ClientUri}-supplied {@link URI}s and
     * {@link URI}s supplied (effectively) by users, the {@link DefaultWebClientDiscovery} class needs to ensure {@link
     * String} representations of URIs are converted into {@link URI}s using the same effective mechanism that Helidon
     * WebClient uses internally. This method accomplishes this task.</p>
     *
     * @param s a valid URI representation; must not be {@code null}
     * @return a {@link URI} representing the supplied {@link String} following Helidon conventions; never {@code null}
     * @exception NullPointerException if {@code s} is {@code null}
     * @exception IllegalArgumentException if {@code s} is not a valid URI representation according to the contract of
     * the {@link URI#create(String)} method
     * @see ClientUri#create()
     * @see ClientUri#create(URI)
     * @see ClientUri#resolve(URI)
     * @see URI#create(String)
     * @see io.helidon.uri.common.uri.UriInfo.BuilderBase#scheme()
     * @see io.helidon.uri.common.uri.UriInfo.BuilderBase#host()
     * @see io.helidon.uri.common.uri.UriInfo.BuilderBase#port()
     * @see io.helidon.uri.common.uri.UriInfo.BuilderBase#path()
     */
    private static URI uri(String s) {
        return ClientUri.create(URI.create(s)).toUri();
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

        static Optional<DiscoveryRequest> of(Map<String, String> properties, UriInfo uriInfo) {
            Map<String, String> discoveryNamesById = newHashMap(properties.size());
            NavigableMap<String, String> idsByPrefixUri = new TreeMap<>();
            Set<String> prefixes;
            String idsProperty = properties.getOrDefault("helidon-discovery-ids", "").trim();
            if (idsProperty.isBlank()) {
                // The user didn't say explicitly which properties will be of interest. Scan.
                for (Entry<String, String> e : properties.entrySet()) {
                    Matcher m = KEY_PATTERN.matcher(e.getKey());
                    if (!m.find()) {
                        continue;
                    }
                    String id = m.group(1); // "ID" in helidon-discovery-ID-...
                    switch (m.group(2)) { // "name" or "prefix-uri"
                    case "name":
                        String discoveryName = requireNonNullElse(e.getValue(), "").trim();
                        discoveryNamesById.put(id, discoveryName.isBlank() ? id : discoveryName);
                        break;
                    case "prefix-uri":
                        discoveryNamesById.putIfAbsent(id, id);
                        String prefix = requireNonNullElse(e.getValue(), "").trim();
                        if (!prefix.isBlank()) {
                            idsByPrefixUri.putIfAbsent(prefix, id);
                        }
                        break;
                    default:
                        // (Won't happen; see #KEY_PATTERN.)
                        break;
                    }
                }
                prefixes = idsByPrefixUri.descendingKeySet();
            } else {
                // The user basically (indirectly) stated which properties will be of interest. Use only those, and in
                // that order.
                prefixes = new LinkedHashSet<>();
                String[] ids = WHITESPACE_AND_COMMAS_PATTERN.split(idsProperty, 0);
                for (String id : ids) {
                    if (id.isEmpty()) {
                        continue;
                    }
                    String discoveryName = properties.getOrDefault("helidon-discovery-" + id + "-name", "").trim();
                    discoveryNamesById.put(id, discoveryName.isBlank() ? id : discoveryName);
                    String prefix = properties.getOrDefault("helidon-discovery-" + id + "-prefix-uri", "").trim();
                    if (!prefix.isBlank()) {
                        prefixes.add(prefix);
                        idsByPrefixUri.computeIfAbsent(prefix, p -> id);
                    }
                }
            }
            // The only thing we need from the UriInfo is its notional URI. So why not just pass a java.net.URI to this
            // method instead of a UriInfo if that's all we actually need?  Because mutable UriInfo implementations,
            // like ClientUri, create (and supply, via toUri()) URIs in a very specific way throughout the WebClient
            // machinery, adding and altering values in some places (such as host and port and path), and we need to
            // maintain that behavior throughout this WebClientService implementation for overall fidelity with the rest
            // of WebClient. See #uri(String) above.
            URI uriInfoUri = uriInfo.toUri().normalize();
            for (String prefix : prefixes) {
                // Turn the properties-supplied prefix string into a URI.
                URI prefixUri;
                try {
                    prefixUri = uri(prefix).normalize();
                } catch (IllegalArgumentException e) {
                    if (LOGGER.isLoggable(WARNING)) {
                        LOGGER.log(WARNING, "Ignoring " + prefix + " because it is not a valid URI", e);
                    }
                    continue;
                }
                // Relativize the URI in flight against the prefix URI, using the well-defined semantics of
                // URI#relativize(String), yielding, if there is a match, in most cases, just a simple path.
                URI diff = prefixUri.relativize(uriInfoUri);
                if (diff == uriInfoUri) {
                    // Relativization "failed"; see
                    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html#relativize(java.net.URI). No
                    // problem; we just didn't match this particular prefix. Carry on to the next one.
                    continue;
                }
                // We matched a prefix and now have the raw materials to query Discovery. Return early.
                return
                    Optional.of(new DiscoveryRequest(discoveryNamesById.get(idsByPrefixUri.get(prefix)),
                                                     prefixUri,
                                                     diff.getRawPath()));
            }
            return Optional.empty();
        }

    }

}
