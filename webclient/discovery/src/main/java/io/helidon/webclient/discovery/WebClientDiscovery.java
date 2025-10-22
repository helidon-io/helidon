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

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * A {@link WebClientService} that {@linkplain #handle(Chain, WebClientServiceRequest) intercepts} certain {@linkplain
 * WebClientServiceRequest requests} and uses {@linkplain io.helidon.discovery.Discovery Helidon Discovery} where
 * appropriate to discover endpoints for those requests, rerouting them as necessary.
 *
 * <p>{@link WebClientDiscovery} instances are normally {@linkplain
 * WebClientDiscoveryProvider#create(io.helidon.commonconfig.Config, String) created by} {@link
 * WebClientDiscoveryProvider} instances, and are not typically used directly by end users.</p>
 *
 * <p>The specification for the {@link #handle(Chain, WebClientServiceRequest)} method fully specifies the required
 * behavior of conforming implementations of this interface, and further describes the implementation-specific behavior
 * of {@link WebClientDiscovery} instances returned from invocations of the {@link #create(WebClientDiscoveryConfig)}
 * method.</p>
 *
 * @see #handle(Chain, WebClientServiceRequest)
 * @see #builder()
 * @see WebClientDiscoveryConfig#builder()
 * @see io.helidon.discovery.Discovery
 * @see WebClientDiscoveryProvider
 * @see WebClientDiscoveryProvider#create(io.helidon.common.config.Config, String)
 */
@RuntimeType.PrototypedBy(WebClientDiscoveryConfig.class)
public interface WebClientDiscovery extends RuntimeType.Api<WebClientDiscoveryConfig>, WebClientService {

    /**
     * <dfn>Handles</dfn> the supplied {@link WebClientServiceRequest} by finding {@linkplain
     * io.helidon.discovery.Discovery Helidon Discovery}-related information in {@linkplain
     * WebClientServiceRequest#properties() its properties}, using that information, if found, to {@linkplain
     * io.helidon.discovery.Discovery#uris(String, java.net.URI) discover} a {@link java.net.URI URI} appropriate for
     * the request, and altering the {@link io.helidon.webclient.api.ClientUri} as necessary with the discovered
     * information before continuing with the request.
     *
     * <p>The specification of this method is described below, followed by a description of its default
     * implementation.</p>
     *
     * <h4>Specification</h4>
     *
     * <p>Any implementation of this method must:</p>
     *
     * <ol>
     *
     * <li>Deterministically decide whether the supplied {@link WebClientServiceRequest} is one for which {@linkplain
     * io.helidon.discovery.Discovery Helidon Discovery} is appropriate. If it is not, the implementation must call
     * {@link Chain#proceed(WebClientRequest) chain.proceed(request)} and return the result.</li>
     *
     * <li>Deterministically produce a <dfn>discovery name</dfn> and a <dfn>default URI</dfn> appropriate for the
     * supplied {@link WebClientServiceRequest} to use during the discovery process. If either cannot be produced for
     * any reason, the implementation must call {@link Chain#proceed(WebClientRequest) chain.proceed(request)} and
     * return the result.</li>
     *
     * <li>Use the discovery name and default URI in a {@linkplain io.helidon.discovery.Discovery#uris(String, URI)
     * discovery request}. If this cannot happen for any reason, the implementation must call {@link
     * Chain#proceed(WebClientRequest) chain.proceed(request)} and return the result.</li>
     *
     * <li>Select a URI from the discovered response (the <dfn>discovered URI</dfn>). If this cannot happen for any
     * reason, the implementation must call {@link Chain#proceed(WebClientRequest) chain.proceed(request)} and return
     * the result.</li>
     *
     * <li>Deterministically alter the {@linkplain WebClientServiceRequest#uri() supplied
     * <code>WebClientServiceRequest</code>'s <code>ClientUri</code>} with information supplied by the discovered
     * URI. If this cannot happen for any reason, the implementation must call {@link Chain#proceed(WebClientRequest)
     * chain.proceed(request)} and return the result.</li>
     *
     * <li>Call {@link Chain#proceed(WebClientRequest) chain.proceed(request)} and return the result.</li>
     *
     * </ol>
     *
     * <h4>Implementation Behavior</h4>
     *
     * <p>The (default) behavior of this method as implemented in instances of {@link WebClientDiscovery} returned from
     * the {@link #create(WebClientDiscoveryConfig)} method is described below. This implementation-specific behavior
     * may change in subsequent revisions of this interface and its cooperating classes.</p>
     *
     * <p>Finding and using {@linkplain io.helidon.discovery.Discovery discovery}-related information in the supplied
     * {@link WebClientServiceRequest} proceeds as follows, with examples:</p>
     *
     * <ol>
     *
     * <li>The <dfn>id set</dfn> is determined from the {@linkplain WebClientServiceRequest#properties()
     * <dfn>properties</dfn>}. The id set helps identify properties of interest that correlate URIs with discovery
     * names. The id set is initially empty.<ol>
     *
     * <li>The id set may be <dfn>explicit</dfn>:<ol>
     *
     * <li>If the {@code helidon-discovery-ids} property exists, its value (<i>e.g.</i> <code><b>SERVICE1,
     * SERVICE2</b></code>) is split into <dfn>tokens</dfn> around commas, ignoring whitespace. Each distinct token is
     * an <dfn>id</dfn> (<i>e.g.</i> <code><b>SERVICE1</b></code> and <code><b>SERVICE2</b></code>).</li>
     *
     * <li>The id set is then simply this ordered set of distinct tokens.</li></ol></li>
     *
     * <li>If, instead, after the step immediately above, the id set is empty for any reason, then it must be
     * <dfn>inferred</dfn>. For any <code><i>TOKEN</i></code>:<ol>
     *
     * <li>If the <code>helidon-discovery-<i>TOKEN</i>-prefix-uri</code> property exists, then <code><i>TOKEN</i></code>
     * is an <dfn>id</dfn> (<i>e.g.</i> <code><b>SERVICE1</b></code> in {@code helidon-discovery-SERVICE1-prefix-uri})
     * and is added, if not already present, to the id set.</li>
     *
     * <li>If the <code>helidon-discovery-<i>TOKEN</i>-name</code> property exists, then <code><i>TOKEN</i></code> is an
     * <dfn>id</dfn> (<i>e.g.</i> <code><b>SERVICE1</b></code> in {@code helidon-discovery-SERVICE1-name}) and is added,
     * if not already present, to the id set.</li></ol></li>
     *
     * <li>If, after these steps, the id set remains empty, discovery is not needed, and {@link
     * Chain#proceed(WebClientServiceRequest) chain.proceed(request)} is invoked and the result is
     * returned.</li></ol></li>
     *
     * <li>For each id <code><i>ID</i></code> (<i>e.g.</i> <code><b>SERVICE1</b></code>) in the (now non-empty) id
     * set:<ol>
     *
     * <li>If present, the value of the <code>helidon-discovery-<i>ID</i>-prefix-uri</code> property (<i>e.g.</i> the
     * value of the <code><b>helidon-discovery-SERVICE1-prefix-uri</b></code> property) is treated as a text
     * representation of a URI (<i>e.g.</i> <code><b>http://service1.example.com</b></code>). If it is not a valid
     * potential argument to the {@link java.net.URI#create(String)} method, then the id is skipped.</li>
     *
     * <li>Otherwise, a <dfn>prefix URI</dfn> is created from the value, following the logic that Helidon WebClient uses
     * elsewhere internally to create URIs, for overall fidelity.<ol>
     *
     * <li style="list-style-type: none">Note that this logic may add additional information, or change existing
     * information; <i>e.g.</i> in this example the URI would be <code><b>http://service1.example.com:80/</b></code>;
     * note the explicit port ({@code 80}) and non-empty path that is now {@code /} instead of the empty
     * string.</li></ol></li>
     *
     * <li>If present, the value of the <code>helidon-discovery-<i>ID</i>-name</code> property (<i>e.g.</i> the value of
     * the <code><b>helidon-discovery-SERVICE1-name</b></code> property) is treated as a <dfn>discovery name</dfn>
     * corresponding to that id. If the value is not present, the discovery name is, instead, the id itself. (For
     * example purposes we will presume this property assignment is not present. Therefore the discovery name will be
     * <code><b>SERVICE1</b></code>.)</li></ol></li>
     *
     * <li>For every prefix URI found following the process described above, an attempt is made to relativize the
     * {@linkplain WebClientServiceRequest#uri() <dfn>original <code>ClientUri</code></dfn>} against it:<ol>
     *
     * <li>The original {@link io.helidon.webclient.api.ClientUri ClientUri}'s {@link
     * io.helidon.webclient.api.ClientUri#toUri() URI} (<i>e.g.</i>
     * <code><b>http://service1.example.com:80/foo</b></code>) in {@linkplain java.net.URI#normalize() normal form} is
     * {@linkplain java.net.URI#relativize(java.net.URI) <dfn>relativized</dfn> against} the {@linkplain
     * java.net.URI#normalize() normalized} prefix URI (<i>e.g.</i>
     * <code><b>http://service1.example.com:80/</b></code>).<ol>
     *
     * <li style="list-style-type: none">Note that for {@link io.helidon.webclient.api.ClientUri}-implementation-related
     * reasons, the {@linkplain WebClientServiceRequest#uri() original <code>ClientUri</code>} will always be absolute,
     * its host and port will always be defined, and its path will never be empty, even if the user did not specify any
     * of this explicitly.</li></ol></li>
     *
     * <li>If relativization yields anything other than a simple raw path, the prefix URI is skipped and the loop
     * continues.</li>
     *
     * <li>Otherwise, the loop exits with the prefix URI (<i>e.g.</i>
     * <code><b>http://service1.example.com:80/</b></code>), discovery name (<i>e.g.</i> <code><b>SERVICE1</b></code>),
     * and the result of relativization (the <dfn>remaining raw path</dfn>, <i>e.g.</i>
     * <code><b>foo</b></code>)).</li></ol></li>
     *
     * <li>If no prefix URI was found for any reason, discovery is not needed, {@link
     * Chain#proceed(WebClientServiceRequest) chain.proceed(request)} is invoked and the result is returned.</li>
     *
     * <li>The {@linkplain WebClientDiscoveryConfig#discovery() <code>Discovery</code> instance supplied} by the
     * {@linkplain #prototype() prototype} is used to {@linkplain io.helidon.discovery.Discovery#uris(String,
     * java.net.URI) issue a discovery request using the discovery name (<i>e.g.</i> <code><b>SERVICE1</b></code>) and
     * prefix URI (<i>e.g.</i> <code><b>http://service1.example.com:80/</b></code>)}. The first of the URIs returned is
     * the <dfn>discovered URI</dfn>; see {@link io.helidon.discovery.Discovery#uris(String, java.net.URI)} for more
     * details. For this example, presume the discovered URI is, <i>e.g.</i>,
     * <code><b>http://23.192.228.84:80/v1/</b></code>.</li>
     *
     * <li>A <dfn>new raw path</dfn> is formed by first {@linkplain java.net.URI#resolve(String) <dfn>resolving</dfn>}
     * the remaining raw path (<i>e.g.</i> <code><b>foo</b></code>) against the discovered URI (<i>e.g.</i>
     * <code><b>http://23.192.228.84:80/v1/</b></code>), yielding, <i>e.g.</i>,
     * <code>http://23.192.228.84:80/v1/foo</code>, and then {@linkplain java.net.URI#getRawPath() acquiring its raw
     * path} (<i>e.g.</i> <code><b>/v1/foo</b></code>).</li>
     *
     * <li>If defined, the {@linkplain java.net.URI#getScheme() scheme} (<i>e.g.</i> <code><b>http</b></code>),
     * {@linkplain java.net.URI#getHost() host} (<i>e.g.</i> <code><b>23.192.228.84</b></code>), and {@linkplain
     * java.net.URI#getPort() port} (<i>e.g.</i> <code><b>80</b></code>) of the discovered URI (<i>e.g.</i>
     * <code><b>http://23.192.228.84:80/v1</b></code>) are installed on the {@linkplain WebClientServiceRequest#uri()
     * original <code>ClientUri</code>} using its {@link io.helidon.webclient.api.ClientUri#scheme(String)
     * scheme(String)}, {@link io.helidon.webclient.api.ClientUri#host(String) host(String)} and {@link
     * io.helidon.webclient.api.ClientUri#port(int) port(int)} methods.</li>
     *
     * <li>The new raw path (<i>e.g.</i> <code><b>/v1/foo</b></code>) is installed on the {@linkplain
     * WebClientServiceRequest#uri() original <code>ClientUri</code>} using its {@link
     * io.helidon.webclient.api.ClientUri#path(String) path(String)} method.</li>
     *
     * <li>This results in the <dfn>final URI</dfn> (<i>e.g.</i> <code><b>http://23.192.228.84:80/v1/foo</b></code>).
     *
     * </ol>
     *
     * <p>Finally, including when any error is encountered, {@link Chain#proceed(WebClientServiceRequest)
     * chain.proceed(request)} is invoked and the result is returned.</p>
     *
     * <h5>Configuration Examples</h5>
     *
     * <p>A minimal example of (YAML) configuration might look like this:</p>
     *
     * <blockquote style="background-color: var(--snippet-background-color);"><pre>client: # see the <a
     * href="https://helidon.io/docs/latest/config/io_helidon_webclient_api_WebClient">Helidon Configuration Reference for WebClient</a>
     *  properties:
     *    helidon-discovery-<b>myservice</b>-prefix-uri: "<b>http://example.com</b>"
     *  services:
     *    discovery:</pre></blockquote>
     *
     * <p>In this example, original {@link io.helidon.webclient.api.ClientUri ClientUri}s beginning with <code>
     * <b>http://example.com:80/</b></code> (note the added/explicit port and path) will be subject to discovery, using
     * <code><b>myservice</b></code> as the discovery name; all others will be passed through with no discovery-related
     * action being taken.</p>
     *
     * <p>A more explicit example of (YAML) configuration might look like this:</p>
     *
     * <blockquote style="background-color: var(--snippet-background-color);"><pre>client: # see the <a
     * href="https://helidon.io/docs/latest/config/io_helidon_webclient_api_WebClient">Helidon Configuration Reference for WebClient</a>
     *  properties:
     *    helidon-discovery-ids: "<b>service1, service2</b>"
     *    helidon-discovery-<b>service1</b>-name: "<b>ServiceOne</b>"
     *    helidon-discovery-<b>service1</b>-prefix-uri: "<b>http://s1.example.com/</b>"
     *    helidon-discovery-<b>service2</b>-name: "<b>ServiceTwo</b>"
     *    helidon-discovery-<b>service2</b>-prefix-uri: "<b>http://s2.example.com/</b>"
     *  services:
     *    discovery:</pre></blockquote>
     *
     * <p>In this example, original {@link io.helidon.webclient.api.ClientUri ClientUri}s beginning with
     * <code><b>http://s1.example.com:80/</b></code> (note the added/explicit port) will be subject to discovery, using
     * <code><b>ServiceOne</b></code> as the discovery name, and original {@link io.helidon.webclient.api.ClientUri
     * ClientUri}s beginning with <code><b>http://s2.example.com:80/</b></code> (note the added/explicit port) will be
     * subject to discovery, using <code><b>ServiceTwo</b></code> as the discovery name. All others will be passed
     * through with no discovery-related action being taken.</p>
     *
     * @param chain a {@link Chain}
     * @param request a {@link WebClientServiceRequest}
     * @return the result of invoking the {@link Chain#proceed(WebClientServiceRequest)
     * proceed(WebClientServiceRequest)} method on the supplied {@link Chain}
     * @exception NullPointerException if any argument is {@code null}
     * @see WebClientService#handle(Chain, WebClientServiceRequest)
     */
    @Override // WebClientService
    WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request);

    @Override // WebClientService (NamedService)
    default String name() {
        return prototype().name();
    }

    /**
     * Returns the determinate {@linkplain WebClientService#type() type of this <code>WebClientService</code>
     * implementation} ({@code discovery} by default).
     *
     * <p>The default implementation of this method returns {@code discovery}.</p>
     *
     * @return {@code discovery} when invoked
     * @see WebClientService#type()
     */
    @Override // WebClientService (NamedService)
    default String type() {
        return "discovery";
    }


    /*
     * Static methods.
     */


    /**
     * A convenience method that returns the result of invoking the {@link WebClientDiscoveryConfig#builder()} method.
     *
     * @return a non-{@code null} {@link WebClientDiscoveryConfig.Builder}
     * @see WebClientDiscoveryConfig#builder()
     * @see <a href="https://helidon.io/docs/latest/se/builder#_specification_3">Helidon Builder</a>
     */
    static WebClientDiscoveryConfig.Builder builder() {
        return WebClientDiscoveryConfig.builder();
    }

    /**
     * A convenience method that calls the {@link #builder()} method, {@linkplain
     * WebClientDiscoveryConfig.Builder#update(Consumer) updates it} using the supplied {@link Consumer}, invokes its
     * {@link WebClientDiscoveryConfig.Builder#build() build()} method, and returns the result.
     *
     * @param consumer a {@link Consumer} of {@link WebClientDiscoveryConfig.Builder} instances; must not be {@code null}
     * @return a new {@link WebClientDiscovery} implementation; never {@code null}
     * @exception NullPointerException if {@code consumer} is {@code null}
     * @see #builder()
     * @see WebClientDiscoveryConfig.Builder#update(Consumer)
     * @see WebClientDiscoveryConfig.Builder#build()
     * @see <a href="https://helidon.io/docs/latest/se/builder#_specification_3">Helidon Builder</a>
     */
    static WebClientDiscovery create(Consumer<WebClientDiscoveryConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Returns a new {@link WebClientDiscovery} implementation configured with the supplied {@code prototype}.
     *
     * <p>This method is most commonly called by the {@link WebClientDiscoveryConfig.Builder#build()} method, which
     * supplies it with the return value of an invocation of its {@link
     * WebClientDiscoveryConfig.Builder#buildPrototype()} method.</p>
     *
     * @param prototype a {@link WebClientDiscoveryConfig}; must not be {@code null}
     * @return a new {@link WebClientDiscovery} implementation
     * @exception NullPointerException if {@code prototype} is {@code null}
     * @see WebClientDiscoveryConfig.Builder#build()
     * @see WebClientDiscoveryConfig.Builder#buildPrototype()
     * @see <a href="https://helidon.io/docs/latest/se/builder#_specification_3">Helidon Builder</a>
     */
    static WebClientDiscovery create(WebClientDiscoveryConfig prototype) {
        return new DefaultWebClientDiscovery(prototype);
    }

}
