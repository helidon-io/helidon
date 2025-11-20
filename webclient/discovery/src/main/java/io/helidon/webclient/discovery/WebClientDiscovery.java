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
 * <h2>Logging</h2>
 *
 * <p>Implementations returned by invocations of the {@link #create(WebClientDiscoveryConfig)} method, and any of their
 * internal supporting classes, will use {@link java.lang.System.Logger Logger}s {@linkplain
 * java.lang.System#getLogger(String) whose names begin with} {@code io.helidon.webclient.discovery.}.</p>
 *
 * <p>Logging output is particularly important to monitor because as a general rule {@linkplain
 * io.helidon.discovery.Discovery discovery} integrations must strive to be resilient in the presence of failures.</p>
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
     * <dfn>Handles</dfn> the supplied {@link WebClientServiceRequest} by using this {@link WebClientDiscovery}'s
     * {@linkplain io.helidon.discovery.Discovery Helidon Discovery}-related {@linkplain
     * WebClientDiscoveryConfig#prefixUris() configuration} to {@linkplain io.helidon.discovery.Discovery#uris(String,
     * java.net.URI) discover} a {@link java.net.URI URI} appropriate for the request, and altering the {@link
     * io.helidon.webclient.api.ClientUri} as necessary with the discovered information before continuing with the
     * request.
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
     * <p>The (default) behavior of this method, as implemented in instances of {@link WebClientDiscovery} returned from
     * the {@link #create(WebClientDiscoveryConfig)} method, conforms to the specification, and is described below. This
     * implementation-specific behavior may change in subsequent revisions of this interface and its cooperating
     * classes.</p>
     *
     * <p>Finding and using {@linkplain io.helidon.discovery.Discovery discovery}-related information suitable for the
     * supplied {@link WebClientServiceRequest} proceeds as follows, with examples:</p>
     *
     * <ol>
     *
     *  <li>Upon initial {@link WebClientDiscovery} implementation instantiation, the {@linkplain
     *  java.util.Map#entrySet() entries} of its {@linkplain WebClientDiscoveryConfig prototype}'s {@linkplain
     *  WebClientDiscoveryConfig#prefixUris() <code>prefixUris</code> <code>Map</code>} are {@linkplain
     *  java.net.URI#normalize() normalized} and sorted, such that entries containing longer (more specific) {@link
     *  java.net.URI}s precede shorter ones (less specific).  This operation happens exactly once.</li>
     *
     *  <li>If the {@linkplain WebClientDiscoveryConfig#prefixUris() <code>prefixUris</code> <code>Map</code>}
     *  {@linkplain java.util.Map#isEmpty() is empty}, then discovery will never be used, and {@link
     *  Chain#proceed(WebClientServiceRequest) chain.proceed(request)} is invoked, and the result is returned.</li>
     *
     *  <li>In a loop, for each such {@link java.net.URI} (<i>e.g.</i> <code><b>http://service1.example.com</b></code>),
     *  proceeding from the longest (most specific) one to the shortest (least specific) one:
     *
     *   <ol>
     *
     *    <li>a <dfn>prefix URI</dfn> is created from it, following the logic that Helidon WebClient uses elsewhere
     *    internally to create URIs, for overall fidelity.
     *
     *     <ol>
     *
     *      <li style="list-style-type: none">Note that this logic may add additional information, or change existing
     *      information; <i>e.g.</i> in this example the URI would be
     *      <code><b>http://service1.example.com:80/</b></code>; note the explicit port ({@code 80}) and non-empty path
     *      that is now {@code /} instead of the empty string. See {@link
     *      io.helidon.webclient.api.ClientUri#create(URI)} and {@link io.helidon.webclient.api.ClientUri#toUri()} for
     *      an explanation of this logic.</li>
     *
     *     </ol>
     *
     *    </li>
     *
     *    <li>For each such prefix URI, an attempt is made to relativize the {@linkplain WebClientServiceRequest#uri()
     *    <dfn>original <code>ClientUri</code></dfn>} against it:
     *
     *     <ol>
     *
     *      <li>The original {@link io.helidon.webclient.api.ClientUri ClientUri}'s {@link
     *      io.helidon.webclient.api.ClientUri#toUri() URI} (<i>e.g.</i>
     *      <code><b>http://service1.example.com:80/foo</b></code>) in {@linkplain java.net.URI#normalize() normal form}
     *      is {@linkplain java.net.URI#relativize(java.net.URI) <dfn>relativized</dfn> against} the {@linkplain
     *      java.net.URI#normalize() normalized} prefix URI (<i>e.g.</i>
     *      <code><b>http://service1.example.com:80/</b></code>).
     *
     *       <ol>
     *
     *        <li style="list-style-type: none">Note that for {@link
     *        io.helidon.webclient.api.ClientUri}-implementation-related reasons, the {@linkplain
     *        WebClientServiceRequest#uri() original <code>ClientUri</code>} will always be absolute, its host and port
     *        will always be defined, and its path will never be empty, even if the user did not specify any of this
     *        explicitly.</li>
     *
     *       </ol>
     *
     *      </li>
     *
     *      <li>If relativization yields anything other than a simple raw path, the prefix URI is skipped and the loop
     *      continues.</li>
     *
     *      <li>Otherwise, the loop exits with:
     *
     *       <ul>
     *
     *        <li>the prefix URI (<i>e.g.</i> <code><b>http://service1.example.com:80/</b></code>),</li>
     *
     *        <li>its corresponding discovery name (<i>e.g.</i> <code><b>S1</b></code>), and
     *
     *        <li>the result of relativization (the <dfn>remaining raw path</dfn>, <i>e.g.</i>
     *        <code><b>foo</b></code>).</li>
     *
     *       </ul>
     *
     *      </li>
     *
     *     </ol>
     *
     *    </li>
     *
     *   </ol>
     *
     *  </li>
     *
     *  <li>If no prefix URI was found for any reason, discovery is not needed, {@link
     *  Chain#proceed(WebClientServiceRequest) chain.proceed(request)} is invoked, and the result is returned.</li>
     *
     *  <li>The {@linkplain WebClientDiscoveryConfig#discovery() <code>Discovery</code> instance supplied} by the
     *  {@linkplain #prototype() prototype} is used to {@linkplain io.helidon.discovery.Discovery#uris(String,
     *  java.net.URI) issue a discovery request using the discovery name (<i>e.g.</i> <code><b>S1</b></code>) and prefix
     *  URI (<i>e.g.</i> <code><b>http://service1.example.com:80/</b></code>)}. The first of the URIs returned is the
     *  <dfn>discovered URI</dfn>; see {@link io.helidon.discovery.Discovery#uris(String, java.net.URI)} for more
     *  details. For this example, presume the discovered URI is, <i>e.g.</i>,
     *  <code><b>http://23.192.228.84:80/v1/</b></code>.</li>
     *
     *  <li>A <dfn>new raw path</dfn> is formed by first {@linkplain java.net.URI#resolve(String) <dfn>resolving</dfn>}
     *  the remaining raw path (<i>e.g.</i> <code><b>foo</b></code>) against the discovered URI (<i>e.g.</i>
     *  <code><b>http://23.192.228.84:80/v1/</b></code>), yielding, <i>e.g.</i>,
     *  <code>http://23.192.228.84:80/v1/foo</code>, and then {@linkplain java.net.URI#getRawPath() acquiring its raw
     *  path} (<i>e.g.</i> <code><b>/v1/foo</b></code>).</li>
     *
     *  <li>If defined, the {@linkplain java.net.URI#getScheme() scheme} (<i>e.g.</i> <code><b>http</b></code>),
     *  {@linkplain java.net.URI#getHost() host} (<i>e.g.</i> <code><b>23.192.228.84</b></code>), and {@linkplain
     *  java.net.URI#getPort() port} (<i>e.g.</i> <code><b>80</b></code>) of the discovered URI (<i>e.g.</i>
     *  <code><b>http://23.192.228.84:80/v1</b></code>) are installed on the {@linkplain WebClientServiceRequest#uri()
     *  original <code>ClientUri</code>} using its {@link io.helidon.webclient.api.ClientUri#scheme(String)
     *  scheme(String)}, {@link io.helidon.webclient.api.ClientUri#host(String) host(String)} and {@link
     *  io.helidon.webclient.api.ClientUri#port(int) port(int)} methods.</li>
     *
     *  <li>The new raw path (<i>e.g.</i> <code><b>/v1/foo</b></code>) is installed on the {@linkplain
     *  WebClientServiceRequest#uri() original <code>ClientUri</code>} using its {@link
     *  io.helidon.webclient.api.ClientUri#path(String) path(String)} method.</li>
     *
     *  <li>This results in the <dfn>final URI</dfn> (<i>e.g.</i> <code><b>http://23.192.228.84:80/v1/foo</b></code>).
     *
     * </ol>
     *
     * <p>Finally, including when any error is encountered, {@link Chain#proceed(WebClientServiceRequest)
     * chain.proceed(request)} is invoked, and the result is returned.</p>
     *
     * <h5>Configuration Examples</h5>
     *
     * <p>A minimal example of (YAML) configuration follows:</p>
     *
     * <blockquote style="background-color: var(--snippet-background-color);"><pre>client: # see the <a
     * href="https://helidon.io/docs/latest/config/io_helidon_webclient_api_WebClient">Helidon Configuration Reference for WebClient</a>
     *  services:
     *    {@linkplain WebClientDiscoveryConfig discovery}:
     *      {@linkplain WebClientDiscoveryConfig#prefixUris() prefix-uris}:
     *        <b>S1</b>: <b>http://service1.example.com</b></pre></blockquote>
     *
     * <p>In this example, original {@link io.helidon.webclient.api.ClientUri ClientUri}s beginning with <code>
     * <b>http://service1.example.com:80/</b></code> (note the added/explicit port and path) will be subject to
     * discovery, using <code><b>S1</b></code> as the discovery name; all others will be passed through with no
     * discovery-related action being taken.</p>
     *
     * @param chain a {@link Chain}
     * @param request a {@link WebClientServiceRequest}
     * @return the result of invoking the {@link Chain#proceed(WebClientServiceRequest)
     * proceed(WebClientServiceRequest)} method on the supplied {@link Chain}
     * @exception NullPointerException if any argument is {@code null}
     * @see WebClientDiscoveryConfig#prefixUris()
     * @see WebClientService#handle(Chain, WebClientServiceRequest)
     */
    @Override // WebClientService
    WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request);

    /**
     * Returns the determinate {@linkplain WebClientService#name() name of this <code>WebClientService</code>
     * implementation} (normally {@code discovery}).
     *
     * <p>The default implementation of this method returns the result of invoking the {@link
     * WebClientDiscoveryConfig#name() name()} method on this {@link WebClientDiscovery}'s {@linkplain #prototype()
     * prototype}.</p>
     *
     * @return this {@link WebClientDiscovery}'s determinate name
     * @see WebClientDiscoveryConfig#name()
     * @see WebClientService#name()
     * @see io.helidon.common.config.NamedService#name()
     */
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
     * @see io.helidon.common.config.NamedService#type()
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
