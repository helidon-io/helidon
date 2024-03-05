/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.sdk.cdi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.bmc.ConfigFileReader.ConfigFile;
import com.oracle.bmc.Service;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.SessionTokenAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.common.ClientBuilderBase;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;
import static java.lang.invoke.MethodType.methodType;

/**
 * A <a href="https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#spi" target="_top">CDI 3.0 portable
 * extension</a> that enables the {@linkplain jakarta.inject.Inject injection} of any <a
 * href="#service-interface"><em>service interface</em></a>, <a href="#service-client"><em>service client</em></a>, <a
 * href="#service-client-builder"><em>service client builder</em></a>, <a
 * href="#async-service-interface"><em>asynchronous service interface</em></a>, <a
 * href="#async-service-client"><em>asynchronous service client</em></a>, or <a
 * href="#async-service-client-builder"><em>asynchronous service client builder</em></a> from the <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/index.html" target="_top">Oracle Cloud Infrastructure Java
 * SDK</a>.
 *
 * <h2>Terminology</h2>
 *
 * <p>The terms <em>service interface</em>, <em>service client</em>, <em>service client builder</em>, <em>asynchronous
 * service interface</em>, <em>asynchronous service client</em>, and <em>asynchronous service client builder</em> are
 * defined as follows:</p>
 *
 * <dl>
 *
 * <dt><a id="service">Service</a></dt>
 *
 * <dd>An <a href="https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm#Services_Supported"
 * target="_top">Oracle Cloud Infrastructure service supported by the Oracle Cloud Infrastructure Java SDK</a>.</dd>
 *
 * <dt><a id="service-interface">Service interface</a></dt>
 *
 * <dd>A Java interface describing the functionality of a <a href="#service">service</a>. Distinguished from an <a
 * href="#async-service-interface"><em>asynchronous service interface</em></a>.
 *
 * <p>For a hypothetical service named <strong>Cloud Example</strong>, the corresponding service interface will be in a
 * package named <code>com.oracle.bmc.</code><strong><code>cloudexample</code></strong>. The service interface's
 * {@linkplain Class#getSimpleName() simple name} is often also named after the service,
 * e.g. <strong><code>CloudExample</code></strong>, but need not be.</p></dd>
 *
 * <dt><a id="service-client">Service client</a></dt>
 *
 * <dd>A concrete Java class that implements the <a href="#service-interface">service interface</a> and has the same
 * {@linkplain Class#getPackageName() package name} as it. Distinguished from an <a
 * href="#async-service-client"><em>asynchronous service client</em></a>.
 *
 * <p>The service client's {@linkplain Class#getSimpleName() simple name} is formed by appending the {@linkplain
 * Class#getSimpleName() simple name} of the service interface with <code>Client</code>. The {@linkplain Class#getName()
 * class name} for the service client for the hypothetical {@code com.oracle.bmc.cloudexample.CloudExample} service
 * interface described above will thus be
 * <code>com.oracle.bmc.</code><strong><code>cloudexample</code></strong><code>.</code><strong><code>CloudExampleClient</code></strong>.</p></dd>
 *
 * <dt><a id="service-client-builder">Service client builder</a></dt>
 *
 * <dd>A concrete Java "builder" class that creates possibly customized instances of its corresponding <a
 * href="#service-client">service client</a>.  Distinguished from an <a
 * href="#async-service-client-builder"><em>asynchronous service client builder</em></a>.
 *
 * <p>The service client builder is nearly always a nested class of the service client whose instances it builds with a
 * {@linkplain Class#getSimpleName() simple name} of {@code Builder}. (In the <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/streaming/StreamClientBuilder.html"
 * target="_top">single case in the entirety of the Oracle Cloud Infrastructure Java SDK where this pattern is not
 * followed</a>, the service client builder's {@linkplain Class#getSimpleName() simple name} is formed by adding {@code
 * Builder} to the service client's {@linkplain Class#getSimpleName() simple name}.)  The {@linkplain Class#getName()
 * class name} for the service client builder for the hypothetical {@code
 * com.oracle.bmc.cloudexample.CloudExampleClient} service client described above will thus be
 * <code>com.oracle.bmc.</code><strong><code>cloudexample</code></strong><code>.</code><strong><code>CloudExampleClient$Builder</code></strong>.</p></dd>
 *
 * <dt><a id="async-service-interface">Asynchronous service interface</a></dt>
 *
 * <dd>A Java interface describing the functionality of a <a href="#service">service</a>. Distinguished from a <a
 * href="#service-interface"><em>service interface</em></a>.
 *
 * <p>For a hypothetical service named <strong>Cloud Example</strong>, the corresponding service interface will be in
 * the same package as that of the service interface. The asynchronous service interface's {@linkplain
 * Class#getSimpleName() simple name} is formed by adding {@code Async} to the service interface's {@linkplain
 * Class#getSimpleName() simple name}. The {@linkplain Class#getName() class name} for the asynchronous service
 * interface for the hypothetical {@code com.oracle.bmc.cloudexample.CloudExample} service interface described above
 * will thus be
 * <code>com.oracle.bmc.</code><strong><code>cloudexample</code></strong><code>.</code><strong><code>CloudExampleAsync</code></strong>.</p></dd>
 *
 * <dt><a id="async-service-client">Asynchronous service client</a></dt>
 *
 * <dd>A concrete Java class that implements the <a href="#async-service-interface">asynchronous service interface</a>
 * and has the same {@linkplain Class#getPackageName() package name} as it. Distinguised from a <a
 * href="#service-client"><em>service client</em></a>.
 *
 * <p>The asynchronous service client's {@linkplain Class#getSimpleName() simple name} is formed by appending the
 * {@linkplain Class#getSimpleName() simple name} of the asynchronous service interface with <code>Client</code>. The
 * {@linkplain Class#getName() class name} for the asynchronous service client for the hypothetical {@code
 * com.oracle.bmc.cloudexample.CloudExample} asynchronous service interface described above will thus be
 * <code>com.oracle.bmc.</code><strong><code>cloudexample</code></strong><code>.</code><strong><code>CloudExampleAsyncClient</code></strong>.</p></dd>
 *
 * <dt><a id="async-service-client-builder">Asynchronous service client builder</a></dt>
 *
 * <dd>A concrete Java "builder" class that creates possibly customized instances of its corresponding <a
 * href="#async-service-client">asynchronous service client</a>. Distinguished from a <a
 * href="#service-client-builder"><em>service client builder</em></a>.
 *
 * <p>The asynchronous service client builder is nearly always a nested class of the asynchronous service client whose
 * instances it builds with a {@linkplain Class#getSimpleName() simple name} of {@code Builder}. (In the <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/streaming/StreamAsyncClientBuilder.html"
 * target="_top">single case in the entirety of the Oracle Cloud Infrastructure Java SDK where this pattern is not
 * followed</a>, the asynchronous service client builder's {@linkplain Class#getName() class name} is formed by adding
 * {@code Builder} to the asynchronous service client's {@linkplain Class#getName() class name}.)  The {@linkplain
 * Class#getName() class name} for the service client builder for the hypothetical {@code
 * com.oracle.bmc.cloudexample.CloudExampleAsyncClient} service client described above will thus be
 * <code>com.oracle.bmc.</code><strong><code>cloudexample</code></strong><code>.</code><strong><code>CloudExampleAsyncClient$Builder</code></strong>.</p></dd>
 *
 * </dl>
 *
 * <p>Additionally, for any given service interface, service client, service client builder, asynchronous service
 * interface, asynchronous service client, or asynchronous service client builder, this {@linkplain Extension extension}
 * also enables the {@linkplain jakarta.inject.Inject injection} of an appropriate {@link
 * AbstractAuthenticationDetailsProvider}, which allows the corresponding service client to authenticate with the
 * service.</p>
 *
 * <p>In all cases, user-supplied beans will be preferred over any otherwise installed by this {@linkplain Extension
 * extension}.</p>
 *
 * <h2>Basic Usage</h2>
 *
 * <p>To use this extension, make sure it is on your project's runtime classpath. To {@linkplain jakarta.inject.Inject
 * inject} a <a href="#service-interface">service interface</a> named
 * <code>com.oracle.bmc.</code><strong><code>cloudexample</code></strong><code>.CloudExample</code> (or an analogous <a
 * href="#async-service-interface">asynchronous service interface</a>), you will also need to ensure that its containing
 * artifact is on your compile classpath (e.g. <a href="https://search.maven.org/search?q=oci-java-sdk-"
 * target="_top"><code>oci-java-sdk-</code><strong><code>cloudexample</code></strong><code>-$VERSION.jar</code></a>,
 * where {@code $VERSION} should be replaced by a suitable version number).</p>
 *
 * <h2>Advanced Usage</h2>
 *
 * <p>In the course of providing {@linkplain jakarta.inject.Inject injection support} for a <a
 * href="#service-interface">service interface</a> or an <a href="#async-service-interface">asynchronous service
 * interface</a>, this {@linkplain Extension extension} will also create <a href="#service-client-builder">service
 * client builder</a> and <a href="#async-service-client-builder">asynchronous service client builder</a> instances by
 * invoking the {@code static} {@code builder()} method that is present on all <a href="#service-client">service
 * client</a> classes, and will then add those instances as beans. The resulting service client or asynchronous service
 * client will be built by that builder's {@link ClientBuilderBase#build(AbstractAuthenticationDetailsProvider)
 * build(AbstractAuthenticationDetailsProvider)} method and will itself be added as a bean.</p>
 *
 * <p>A user may wish to customize this builder so that the resulting service client or asynchronous service client
 * reflects the customization. She has two options:</p>
 *
 * <ol>
 *
 * <li>She may supply her own bean with the service client builder type (or asynchronous client builder type) as one of
 * its <a href="https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#bean_types" target="_top">bean
 * types</a>. In this case, this {@linkplain Extension extension} does not supply the service client builder (or
 * asynchronous service client builder) and the user is in full control of how her service client (or asynchronous
 * service client) is constructed.</li>
 *
 * <li>She may customize the service client builder (or asynchronous service client builder) supplied by this
 * {@linkplain Extension extension}. To do this, she <a
 * href="https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#observes" target="_top">declares an
 * observer method</a> that observes the service client builder object (or asynchronous service client builder object)
 * that is returned from the {@code static} service client (or asynchronous service client) {@code builder()} method.
 * In her observer method, she may call any method on the supplied service client builder (or asynchronous service
 * client builder) and her customizations will be retained.</li>
 *
 * </ol>
 *
 * <h2>Configuration</h2>
 *
 * <p>This extension uses the following <a
 * href="https://github.com/eclipse/microprofile-config#configuration-for-microprofile" target="_top">MicroProfile
 * Config</a> property names (note, however, that no configuration is required):</p>
 *
 * <table>
 *
 *   <caption><a href="https://github.com/eclipse/microprofile-config#configuration-for-microprofile"
 *   target="_top">MicroProfile Config</a> property names</caption>
 *
 *   <thead>
 *
 *     <tr>
 *
 *       <th scope="col">Name</th>
 *
 *       <th scope="col">Type</th>
 *
 *       <th scope="col">Description</th>
 *
 *       <th scope="col">Default Value</th>
 *
 *       <th scope="col">Notes</th>
 *
 *     </tr>
 *
 *   </thead>
 *
 *   <tbody>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.auth-strategies}</th>
 *
 *       <td>{@link String String[]}</td>
 *
 *       <td>A comma-separated list of descriptors describing the strategy, or strategies, to use to select an
 *       appropriate {@link AbstractAuthenticationDetailsProvider} when one is called for.</td>
 *
 *       <td>{@code auto}</td>
 *
 *       <td>Zero or more of the following:
 *
 *         <ul>
 *           <li>{@code auto}</li>
 *           <li>{@code config}</li>
 *           <li>{@code config-file}</li>
 *           <li>{@code instance-principals}</li>
 *           <li>{@code resource-principal}</li>
 *           <li>{@code session-token-config-file}</li>
 *         </ul>
 *
 *         <p>A strategy descriptor of {@code config} will cause a {@link
 *         com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider} to be used, populated with other MicroProfile Config
 *         properties described here.</p>
 *
 *         <p>A strategy descriptor of {@code config-file} will cause a {@link
 *         com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider} to be used, {@linkplain
 *         SessionTokenAuthenticationDetailsProvider#SessionTokenAuthenticationDetailsProvider(ConfigFile) loaded from}
 *         an <a href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm">OCI configuration
 *         file</a>, with a loading process customized with other MicroProfile Config properties described here.</p>
 *
 *         <p>A strategy descriptor of {@code instance-principals} will cause an {@link
 *         com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider} to be used.</p>
 *
 *         <p>A strategy descriptor of {@code resource-principal} will cause a {@link
 *         com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider} to be used.</p>
 *
 *         <p>A strategy descriptor of {@code session-token-config-file} will cause a {@link
 *         com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider} to be used, loaded from an <a
 *         href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm">OCI configuration file</a>, with
 *         a loading process customized with other MicroProfile Config properties described here.</p>
 *
 *         <p>If there are many strategy descriptors supplied, the first one that is deemed to be available or suitable
 *         will be used and all others will be ignored.</p>
 *
 *         <p>If {@code auto} is present in the list, or if no value for this property exists, the behavior will be as
 *         if {@code config,config-file,session-token-config-file,instance-principals,resource-principal} were supplied
 *         instead.</p></td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.config.path}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>A {@link String} that is {@linkplain com.oracle.bmc.ConfigFileReader#parse(String) a path to a valid OCI
 *       configuration file}</td> <td>A {@linkplain com.oracle.bmc.ConfigFileReader#parseDefault() default
 *       location}</td> <td>This configuration property has an effect only when either {@code config-file} or {@code
 *       session-token-config-file} is, explicitly or implicitly, present in the value for the {@code
 *       oci.auth-strategies} configuration property described elsewhere in this table.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.config.profile}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>An <a href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#File_Entries"
 *       target="_top">OCI configuration file profile</a>.</td>
 *
 *       <td>{@link com.oracle.bmc.ConfigFileReader#DEFAULT_PROFILE_NAME DEFAULT}</td>
 *
 *       <td>This configuration property has an effect only when either {@code config-file} or {@code
 *       session-token-config-file} is, explicitly or implicitly, present in the value for the {@code
 *       oci.auth-strategies} configuration property described elsewhere in this table.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.auth.fingerprint}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>An <a href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#four"
 *       target="_top">API signing key's fingerprint</a>.</td>
 *
 *       <td></td>
 *
 *       <td>This configuration property has an effect only when {@code config} is, explicitly or implicitly, present in
 *       the value for the {@code oci.auth-strategies} configuration property described elsewhere in this table.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.auth.region}</th>
 *
 *       <td>{@link com.oracle.bmc.Region} ({@link String} representation)</td>
 *
 *       <td>A <a href="https://docs.oracle.com/en-us/iaas/Content/General/Concepts/regions.htm#About__The"
 *       target="_top">region identifier</a>.</td>
 *
 *       <td></td>
 *
 *       <td>This configuration property has an effect only when {@code config} is, explicitly or implicitly, present in
 *       the value for the {@code oci.auth-strategies} configuration property described elsewhere in this table.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.auth.tenant-id}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>An <a href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#five"
 *       target="_top">OCID of a tenancy</a>.</td>
 *
 *       <td></td>
 *
 *       <td>This configuration property has an effect only when {@code config} is, explicitly or implicitly, present in
 *       the value for the {@code oci.auth-strategies} configuration property described elsewhere in this table.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.auth.user-id}</th>
 *
 *       <td>{@link String}</td>
 *
 *       <td>An <a href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#five"
 *       target="_top">OCID of a user</a>.</td>
 *
 *       <td></td>
 *
 *       <td>This configuration property has an effect only when {@code config} is, explicitly or implicitly, present in
 *       the value for the {@code oci.auth-strategies} configuration property described elsewhere in this table.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.extension.classname-vetoes}</th>
 *
 *       <td>{@link String String[]}</td>
 *
 *       <td>A comma-separated list of {@linkplain Class#getName() class names} beginning with {@code com.oracle.bmc.}
 *       that should be skipped, even if they match the service pattern described above.</td>
 *
 *       <td></td>
 *
 *       <td>It is recommended not to supply a value for this property name except in extraordinary circumstances.</td>
 *
 *     </tr>
 *
 *     <tr>
 *
 *       <th scope="row">{@code oci.extension.lenient-classloading}</th>
 *
 *       <td>{@link Boolean boolean}</td>
 *
 *       <td>If {@code true}, classes that cannot be loaded will not cause a definition error and will simply be skipped
 *       (recommended).</td>
 *
 *       <td>{@code true}</td>
 *
 *       <td></td>
 *
 *     </tr>
 *
 *   </tbody>
 *
 * </table>
 *
 * @see Extension
 *
 * @see <a href="https://docs.oracle.com/en-us/iaas/tools/java/latest/index.html" target="_top">Oracle Cloud
 * Infrastructure Java SDK</a>
 */
public final class OciExtension implements Extension {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = System.getLogger(OciExtension.class.getName());

    // Evaluates to "com.oracle.bmc." (yes, bmc, not oci) as of the current version of the public OCI Java SDK.
    private static final String OCI_PACKAGE_PREFIX = Service.class.getPackageName() + ".";

    // For any OCI service conceptually named "Example" in an OCI_PACKAGE_PREFIX subpackage named "example":
    //
    // Match Strings expected to be class names that start with OCI_PACKAGE_PREFIX...
    //
    // (1) ...followed by the service client package fragment ("example")...
    // (2) ...followed by a period (".")...
    // (3) ...followed by one or more of the following:
    //       "Example",
    //       "ExampleAsync",
    //       "ExampleAsyncClient",
    //       "ExampleAsyncClientBuilder", // ...bmc.streaming mistakenly doesn't use a nested Builder class; all other services do
    //       "ExampleAsyncClient$Builder",
    //       "ExampleClient",
    //       "ExampleClientBuilder",
    //       "ExampleClient$Builder"...
    // (4) ...followed by the end of String.
    //
    // Capturing group 0: the matched substring ("example.ExampleClientBuilder")
    // Capturing group 1: Capturing group 2 and base noun ("example.Example")
    // Capturing group 2: "example"
    private static final Pattern SERVICE_CLIENT_CLASS_NAME_SUBSTRING_PATTERN =
        Pattern.compile("^(([^.]+)" // (1) (as many non-periods as possible)
                        + "\\." // (2) (a single period)
                        + ".+?)(?:Async)?(?:Client(?:\\$?Builder)?)?" // (3)
                        + "$"); // (4)

    private static final TypeLiteral<AdpSupplier<BasicAuthenticationDetailsProvider>>
        ADPSUPPLIER_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType ADPSUPPLIER_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE =
        (ParameterizedType) ADPSUPPLIER_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL.getType();

    private static final TypeLiteral<AdpSupplier<ConfigFileAuthenticationDetailsProvider>>
        ADPSUPPLIER_CONFIGFILEAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType ADPSUPPLIER_CONFIGFILEAUTHENTICATIONDETAILSPROVIDER_TYPE =
        (ParameterizedType) ADPSUPPLIER_CONFIGFILEAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL.getType();

    private static final TypeLiteral<AdpSupplier<InstancePrincipalsAuthenticationDetailsProvider>>
        ADPSUPPLIER_INSTANCEPRINCIPALSAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType ADPSUPPLIER_INSTANCEPRINCIPALSAUTHENTICATIONDETAILSPROVIDER_TYPE =
        (ParameterizedType) ADPSUPPLIER_INSTANCEPRINCIPALSAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL.getType();

    private static final TypeLiteral<AdpSupplier<ResourcePrincipalAuthenticationDetailsProvider>>
        ADPSUPPLIER_RESOURCEPRINCIPALAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType ADPSUPPLIER_RESOURCEPRINCIPALAUTHENTICATIONDETAILSPROVIDER_TYPE =
        (ParameterizedType) ADPSUPPLIER_RESOURCEPRINCIPALAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL.getType();

    private static final TypeLiteral<AdpSupplier<SessionTokenAuthenticationDetailsProvider>>
        ADPSUPPLIER_SESSIONTOKENAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType ADPSUPPLIER_SESSIONTOKENAUTHENTICATIONDETAILSPROVIDER_TYPE =
        (ParameterizedType) ADPSUPPLIER_SESSIONTOKENAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL.getType();

    private static final TypeLiteral<AdpSupplier<SimpleAuthenticationDetailsProvider>>
        ADPSUPPLIER_SIMPLEAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType ADPSUPPLIER_SIMPLEAUTHENTICATIONDETAILSPROVIDER_TYPE =
        (ParameterizedType) ADPSUPPLIER_SIMPLEAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL.getType();

    private static final TypeLiteral<AdpSupplier<? extends BasicAuthenticationDetailsProvider>>
        ADPSUPPLIER_WILDCARD_EXTENDS_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType ADPSUPPLIER_WILDCARD_EXTENDS_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE =
        (ParameterizedType) ADPSUPPLIER_WILDCARD_EXTENDS_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL.getType();

    private static final TypeLiteral<CascadingAdpSupplier<BasicAuthenticationDetailsProvider>>
        CASCADINGADPSUPPLIER_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType CASCADINGADPSUPPLIER_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE =
        (ParameterizedType) CASCADINGADPSUPPLIER_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL.getType();

    private static final TypeLiteral<Event<Object>> EVENT_OBJECT_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final TypeLiteral<Supplier<ConfigFile>> SUPPLIER_CONFIGFILE_TYPE_LITERAL = new TypeLiteral<>() {};

    private static final ParameterizedType SUPPLIER_CONFIGFILE_TYPE =
        (ParameterizedType) SUPPLIER_CONFIGFILE_TYPE_LITERAL.getType();

    private static final Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

    private static final Set<Class<?>> ADP_BUILDER_CLASSES =
        Set.of(InstancePrincipalsAuthenticationDetailsProviderBuilder.class,
               ResourcePrincipalAuthenticationDetailsProviderBuilder.class,
               SessionTokenAuthenticationDetailsProviderBuilder.class,
               SimpleAuthenticationDetailsProviderBuilder.class);

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];


    /*
     * Instance fields.
     */


    private boolean lenientClassloading;

    private final Set<ServiceTaqs> serviceTaqs;

    private final Set<String> additionalVetoes;

    private final Set<String> unloadableClassNames;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link OciExtension}.
     *
     * @deprecated For {@link java.util.ServiceLoader} use only.
     */
    @Deprecated // for java.util.ServiceLoader use only
    public OciExtension() {
        super();
        this.lenientClassloading = true;
        this.serviceTaqs = new HashSet<>();
        this.additionalVetoes = new HashSet<>(7);
        this.unloadableClassNames = new HashSet<>(7);
    }


    /*
     * Container lifecycle observer methods.
     */


    private void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        Config c = ConfigProvider.getConfig();
        try {
            this.lenientClassloading =
                c.getOptionalValue("oci.extension.lenient-classloading", Boolean.class)
                .orElse(Boolean.TRUE)
                .booleanValue();
        } catch (IllegalArgumentException conversionException) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, conversionException.getMessage(), conversionException);
            }
            this.lenientClassloading = true;
        }
        this.additionalVetoes.addAll(c.getOptionalValue("oci.extension.classname-vetoes", String[].class)
                                     .map(Set::<String>of)
                                     .orElse(Set.of()));
    }

    private void processInjectionPoint(@Observes ProcessInjectionPoint<?, ?> event) {
        InjectionPoint ip = event.getInjectionPoint();
        processInjectionPoint(ip.getAnnotated().getBaseType(),
                              ip.getQualifiers(),
                              event::addDefinitionError);
    }

    private void processProviderInjectionPoint(@Observes ProcessInjectionPoint<?, ? extends Provider<?>> event) {
        InjectionPoint ip = event.getInjectionPoint();
        processInjectionPoint(((ParameterizedType) ip.getAnnotated().getBaseType()).getActualTypeArguments()[0],
                              ip.getQualifiers(),
                              event::addDefinitionError);
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager bm) {
        if (!this.serviceTaqs.isEmpty()) {
            for (ServiceTaqs serviceTaqs : this.serviceTaqs) {
                Annotation[] qualifiers = serviceTaqs.qualifiers();
                if (serviceTaqs.isEmpty()) {
                    installAdpMachinery(event, bm, qualifiers);
                } else {
                    TypeAndQualifiers serviceClientBuilder = serviceTaqs.serviceClientBuilder();
                    TypeAndQualifiers serviceClient = serviceTaqs.serviceClient();
                    installServiceClientBuilder(event, bm, serviceClientBuilder, serviceClient, this.lenientClassloading);
                    installServiceClient(event, bm, serviceClient, serviceTaqs.serviceInterface(), serviceClientBuilder);
                }
            }
        }
    }

    private void afterDeploymentValidation(@Observes AfterDeploymentValidation event) {
        this.unloadableClassNames.clear();
        this.additionalVetoes.clear();
        this.serviceTaqs.clear();
    }


    /*
     * Additional instance methods.
     */


    /**
     * Returns {@code true} if the supplied {@link Class} is known to not be directly related to an Oracle Cloud
     * Infrastructure service.
     *
     * <p>The check is fast and deliberately not exhaustive.</p>
     *
     * @param c the {@link Class} in question; must not be {@code null}; will be a {@link Class} whose {@linkplain
     * Class#getPackageName() package name} starts with the value of the {@link #OCI_PACKAGE_PREFIX} field
     *
     * @return {@code true} if the supplied {@link Class} is known to not be directly related to an Oracle Cloud
     * Infrastructure service
     *
     * @exception NullPointerException if {@code c} is {@code null}
     */
    private boolean isVetoed(Class<?> c) {
        // See
        // https://docs.oracle.com/en-us/iaas/tools/java/latest/overview-summary.html#:~:text=Oracle%20Cloud%20Infrastructure%20Common%20Runtime.
        // None of these packages contains OCI service clients or service client interfaces or service client
        // builders. There are other packages (com.oracle.bmc.encryption, as an arbitrary example) that should also
        // conceptually be vetoed. This method does not currently veto all of them, nor is it clear that it ever could.
        // The strategy employed here, however, vetoes quite a large number of them correctly and very efficiently
        // before more sophisticated tests are employed.
        //
        // "Veto" in this context means only that this extension will not further process the class in question. The
        // class remains eligible for further processing; i.e. this is not a CDI veto.
        if (equals(Service.class.getProtectionDomain(), c.getProtectionDomain())
                || this.additionalVetoes.contains(c.getName())) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "Vetoed " + c);
            }
            return true;
        }
        return false;
    }

    private void processInjectionPoint(Type type,
                                       Set<Annotation> qualifiers,
                                       Consumer<? super ClassNotFoundException> errorHandler) {
        if (!(type instanceof Class)) {
            // Optimization: all OCI constructs we're interested in are non-generic classes (and not therefore
            // ParameterizedTypes or GenericArrayTypes).
            return;
        }
        Class<?> c = (Class<?>) type;
        String className = c.getName();
        if (!className.startsWith(OCI_PACKAGE_PREFIX)) {
            // Optimization: the set of classes we're interested in is a subset of general OCI-related classes.
            return;
        }
        if (AbstractAuthenticationDetailsProvider.class.isAssignableFrom(c) || ADP_BUILDER_CLASSES.contains(c)) {
            // Register an "empty" ServiceTaqs as an indicator of demand for some kind of
            // AbstractAuthenticationDetailsProvider (or a relevant builder).
            this.serviceTaqs.add(new ServiceTaqs(qualifiers.toArray(EMPTY_ANNOTATION_ARRAY)));
            return;
        }
        Matcher m = SERVICE_CLIENT_CLASS_NAME_SUBSTRING_PATTERN.matcher(className.substring(OCI_PACKAGE_PREFIX.length()));
        if (!m.matches() || this.isVetoed(c)) {
            return;
        }
        this.processServiceClientInjectionPoint(errorHandler,
                                                c,
                                                qualifiers,
                                                OCI_PACKAGE_PREFIX + m.group(1));
    }

    private void processServiceClientInjectionPoint(Consumer<? super ClassNotFoundException> errorHandler,
                                                    Class<?> c,
                                                    Set<Annotation> qualifiers,
                                                    String serviceInterfaceName) {
        Annotation[] qualifiersArray = qualifiers.toArray(EMPTY_ANNOTATION_ARRAY);
        ServiceTaqs serviceTaqsForAuth = null;
        boolean lenient = this.lenientClassloading;
        // Create types-and-qualifiers for, e.g.:
        //   ....example.Example
        //   ....example.ExampleClient
        //   ....example.ExampleClient$Builder
        Class<?> serviceInterfaceClass = toClassUnresolved(errorHandler, c, serviceInterfaceName, lenient);
        if (serviceInterfaceClass != null && serviceInterfaceClass.isInterface()) {
            String serviceClient = serviceInterfaceName + "Client";
            Class<?> serviceClientClass = toClassUnresolved(errorHandler, c, serviceClient, lenient);
            if (serviceClientClass != null && serviceInterfaceClass.isAssignableFrom(serviceClientClass)) {
                Class<?> serviceClientBuilderClass = toClassUnresolved(errorHandler, c, serviceClient + "$Builder", true);
                if (serviceClientBuilderClass == null) {
                    serviceClientBuilderClass = toClassUnresolved(errorHandler, serviceClient + "Builder", lenient);
                }
                if (serviceClientBuilderClass != null
                    && ClientBuilderBase.class.isAssignableFrom(serviceClientBuilderClass)) {
                    this.serviceTaqs.add(new ServiceTaqs(qualifiersArray,
                                                         serviceInterfaceClass,
                                                         serviceClientClass,
                                                         serviceClientBuilderClass));
                    // Use an "empty" ServiceTaqs as an indicator of demand for some kind of
                    // AbstractAuthenticationDetailsProvider (or a relevant builder).
                    serviceTaqsForAuth = new ServiceTaqs(qualifiersArray);
                    this.serviceTaqs.add(serviceTaqsForAuth);
                }
            }
        }
        // Create types-and-qualifiers for, e.g.:
        //   ....example.ExampleAsync
        //   ....example.ExampleAsyncClient
        //   ....example.ExampleAsyncClient$Builder
        String serviceAsyncInterface = serviceInterfaceName + "Async";
        Class<?> serviceAsyncInterfaceClass = toClassUnresolved(errorHandler, c, serviceAsyncInterface, lenient);
        if (serviceAsyncInterfaceClass != null && serviceAsyncInterfaceClass.isInterface()) {
            String serviceAsyncClient = serviceAsyncInterface + "Client";
            Class<?> serviceAsyncClientClass = toClassUnresolved(errorHandler, c, serviceAsyncClient, lenient);
            if (serviceAsyncClientClass != null
                && serviceAsyncInterfaceClass.isAssignableFrom(serviceAsyncClientClass)) {
                Class<?> serviceAsyncClientBuilderClass =
                    toClassUnresolved(errorHandler, c, serviceAsyncClient + "$Builder", true);
                if (serviceAsyncClientBuilderClass == null) {
                    serviceAsyncClientBuilderClass = toClassUnresolved(errorHandler, serviceAsyncClient + "Builder", lenient);
                }
                if (serviceAsyncClientBuilderClass != null
                    && ClientBuilderBase.class.isAssignableFrom(serviceAsyncClientBuilderClass)) {
                    this.serviceTaqs.add(new ServiceTaqs(qualifiersArray,
                                                         serviceAsyncInterfaceClass,
                                                         serviceAsyncClientClass,
                                                         serviceAsyncClientBuilderClass));
                    if (serviceTaqsForAuth == null) {
                        // Use an "empty" ServiceTaqs as an indicator of demand for some kind of
                        // AbstractAuthenticationDetailsProvider (or a relevant builder).
                        this.serviceTaqs.add(new ServiceTaqs(qualifiersArray));
                    }
                }
            }
        }
    }

    private Class<?> toClassUnresolved(Consumer<? super ClassNotFoundException> errorHandler,
                                       String name,
                                       boolean lenient) {
        return toClassUnresolved(errorHandler, null, name, lenient);
    }

    private Class<?> toClassUnresolved(Consumer<? super ClassNotFoundException> errorHandler,
                                       Class<?> referenceClass,
                                       String name,
                                       boolean lenient) {
        if (referenceClass != null && referenceClass.getName().equals(name)) {
            return referenceClass;
        }
        try {
            return loadClassUnresolved(name);
        } catch (ClassNotFoundException classNotFoundException) {
            if (lenient) {
                if (this.unloadableClassNames.add(name)) {
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG, "class " + name + " not found");
                    }
                }
            } else {
                errorHandler.accept(classNotFoundException);
            }
            return null;
        }
    }


    /*
     * Static methods.
     */


    private static void installAdpMachinery(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {

        // ConfigAccessor
        installConfigAccessor(event, bm, qualifiers);

        // Supplier<ConfigFile>/ConfigFile
        installConfigFile(event, bm, qualifiers);

        // ConfigFileAuthenticationDetailsProvider
        installConfigFileAdp(event, bm, qualifiers);

        // InstancePrincipalsAuthenticationDetailsProvider
        installInstancePrincipalsAdp(event, bm, qualifiers);

        // ResourcePrincipalAuthenticationDetailsProvider
        installResourcePrincipalAdp(event, bm, qualifiers);

        // SessionTokenAuthenticationDetailsProvider
        installSessionTokenAdps(event, bm, qualifiers);

        // SimpleAuthenticationDetailsProvider
        installSimpleAdp(event, bm, qualifiers);

        // CascadingAdpSupplier<BasicAuthenticationDetailsProvider>
        installAutoAdp(event, bm, qualifiers);

        // AbstractAuthenticationDetailsProvider, BasicAuthenticationDetailsProvider
        installBasic(event, bm, qualifiers);
    }

    private static void installConfigAccessor(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // ConfigAccessor
        if (isUnsatisfied(bm, ConfigAccessor.class, qualifiers)) {
            event.addBean()
                .addTransitiveTypeClosure(MicroProfileConfigConfigAccessor.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             produceConfigAccessor(i, qualifiers));
        }
    }

    private static void installConfigFile(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // Supplier<ConfigFile>
        if (isUnsatisfied(bm, SUPPLIER_CONFIGFILE_TYPE, qualifiers)) {
            event.addBean()
                .types(SUPPLIER_CONFIGFILE_TYPE)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             ConfigFiles.configFileSupplier(i.select(ConfigAccessor.class, qualifiers).get()));
        }
        // ConfigFile
        if (isUnsatisfied(bm, ConfigFile.class, qualifiers)) {
            event.addBean()
                .types(ConfigFile.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             i.select(SUPPLIER_CONFIGFILE_TYPE_LITERAL, qualifiers).get());
        }
    }

    private static void installConfigFileAdp(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // AdpSupplier<ConfigFileAuthenticationDetailsProvider>, ConfigFileAdpSupplier
        if (isUnsatisfied(bm, ADPSUPPLIER_CONFIGFILEAUTHENTICATIONDETAILSPROVIDER_TYPE, qualifiers)) {
            event.addBean()
                .types(ADPSUPPLIER_CONFIGFILEAUTHENTICATIONDETAILSPROVIDER_TYPE, ConfigFileAdpSupplier.class)
                .qualifiers(withName(qualifiers, "config-file"))
                .scope(Singleton.class)
                .produceWith(i ->
                             new ConfigFileAdpSupplier(i.select(SUPPLIER_CONFIGFILE_TYPE_LITERAL, qualifiers).get()));
        }
        // ConfigFileAuthenticationDetailsProvider
        if (isUnsatisfied(bm, ConfigFileAuthenticationDetailsProvider.class, qualifiers)) {
            event.addBean()
                .types(ConfigFileAuthenticationDetailsProvider.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             new ConfigFileAuthenticationDetailsProvider(i.select(ConfigFile.class, qualifiers).get()));
        }
    }

    private static void installInstancePrincipalsAdp(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // AdpSupplier<InstancePrincipalsAuthenticationDetailsProvider>, InstancePrincipalsAdpSupplier
        if (isUnsatisfied(bm, ADPSUPPLIER_INSTANCEPRINCIPALSAUTHENTICATIONDETAILSPROVIDER_TYPE, qualifiers)) {
            event.addBean()
                .types(ADPSUPPLIER_INSTANCEPRINCIPALSAUTHENTICATIONDETAILSPROVIDER_TYPE, InstancePrincipalsAdpSupplier.class)
                .qualifiers(withName(qualifiers, "instance-principals"))
                .scope(Singleton.class) // or Dependent?
                .produceWith(i ->
                             new InstancePrincipalsAdpSupplier(
                                 i.select(InstancePrincipalsAuthenticationDetailsProviderBuilder.class, qualifiers)::get,
                                 i.select(ConfigAccessor.class, qualifiers).get()));
        }
        // InstancePrincipalsAuthenticationDetailsProviderBuilder
        if (isUnsatisfied(bm, InstancePrincipalsAuthenticationDetailsProviderBuilder.class, qualifiers)) {
            event.addBean()
                .types(InstancePrincipalsAuthenticationDetailsProviderBuilder.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             fire(i, InstancePrincipalsAuthenticationDetailsProvider.builder(), qualifiers));
        }
        // InstancePrincipalsAuthenticationDetailsProvider
        if (isUnsatisfied(bm, InstancePrincipalsAuthenticationDetailsProvider.class, qualifiers)) {
            event.addBean()
                .types(InstancePrincipalsAuthenticationDetailsProvider.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             i.select(InstancePrincipalsAuthenticationDetailsProviderBuilder.class, qualifiers).get()
                             .build());
        }
    }

    private static void installResourcePrincipalAdp(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // AdpSupplier<ResourcePrincipalAuthenticationDetailsProvider>, ResourcePrincipalAdpSupplier
        if (isUnsatisfied(bm, ADPSUPPLIER_RESOURCEPRINCIPALAUTHENTICATIONDETAILSPROVIDER_TYPE, qualifiers)) {
            event.addBean()
                .types(ADPSUPPLIER_RESOURCEPRINCIPALAUTHENTICATIONDETAILSPROVIDER_TYPE, ResourcePrincipalAdpSupplier.class)
                .qualifiers(withName(qualifiers, "resource-principal"))
                .scope(Singleton.class) // or Dependent?
                .produceWith(i ->
                             new ResourcePrincipalAdpSupplier(
                                 i.select(ResourcePrincipalAuthenticationDetailsProviderBuilder.class, qualifiers)::get));
        }
        // ResourcePrincipalAuthenticationDetailsProviderBuilder
        if (isUnsatisfied(bm, ResourcePrincipalAuthenticationDetailsProviderBuilder.class, qualifiers)) {
            event.addBean()
                .types(ResourcePrincipalAuthenticationDetailsProviderBuilder.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             fire(i,
                                  ResourcePrincipalAuthenticationDetailsProvider.builder(),
                                  qualifiers));
        }
        // ResourcePrincipalAuthenticationDetailsProvider
        if (isUnsatisfied(bm, ResourcePrincipalAuthenticationDetailsProvider.class, qualifiers)) {
            event.addBean()
                .types(ResourcePrincipalAuthenticationDetailsProvider.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i -> i.select(ResourcePrincipalAuthenticationDetailsProviderBuilder.class, qualifiers).get()
                             .build());
        }
    }

    private static void installSessionTokenAdps(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // SessionTokenAuthenticationDetailsProviderBuilder
        if (isUnsatisfied(bm, SessionTokenAuthenticationDetailsProviderBuilder.class, qualifiers)) {
            event.addBean()
                .types(SessionTokenAuthenticationDetailsProviderBuilder.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             fire(i, SessionTokenAuthenticationDetailsProvider.builder(), qualifiers));
        }
        // AdpSupplier<SessionTokenAuthenticationDetailsProvider>, SessionTokenAdpSupplier (from builder)
        if (isUnsatisfied(bm, ADPSUPPLIER_SESSIONTOKENAUTHENTICATIONDETAILSPROVIDER_TYPE, qualifiers)) {
            event.addBean()
                .types(ADPSUPPLIER_SESSIONTOKENAUTHENTICATIONDETAILSPROVIDER_TYPE, SessionTokenAdpSupplier.class)
                .qualifiers(withName(qualifiers, "session-token-builder"))
                .scope(Singleton.class) // or Dependent?
                .produceWith(i ->
                             SessionTokenAdpSupplier
                             .ofBuilder(i.select(SessionTokenAuthenticationDetailsProviderBuilder.class, qualifiers).get()));
        }
        Annotation[] qualifiersPlusNamePlusOciConfig;
        if (qualifiers.length == 0) {
            qualifiersPlusNamePlusOciConfig =
                new Annotation[] {OciConfig.Literal.INSTANCE, NamedLiteral.of("session-token-config-file")};
        } else {
            qualifiersPlusNamePlusOciConfig = new Annotation[qualifiers.length + 2];
            System.arraycopy(qualifiers, 0, qualifiersPlusNamePlusOciConfig, 0, qualifiers.length);
            qualifiersPlusNamePlusOciConfig[qualifiers.length] = OciConfig.Literal.INSTANCE;
            qualifiersPlusNamePlusOciConfig[qualifiers.length + 1] = NamedLiteral.of("session-token-config-file");
        }
        // AdpSupplier<SessionTokenAuthenticationDetailsProvider>, SessionTokenAdpSupplier (from ConfigFile)
        if (isUnsatisfied(bm, ADPSUPPLIER_SESSIONTOKENAUTHENTICATIONDETAILSPROVIDER_TYPE, qualifiersPlusNamePlusOciConfig)) {
            event.addBean()
                .types(ADPSUPPLIER_SESSIONTOKENAUTHENTICATIONDETAILSPROVIDER_TYPE, SessionTokenAdpSupplier.class)
                .qualifiers(qualifiersPlusNamePlusOciConfig)
                .scope(Singleton.class) // or Dependent?
                .produceWith(i ->
                             SessionTokenAdpSupplier.ofConfigFileSupplier(i.select(SUPPLIER_CONFIGFILE_TYPE_LITERAL,
                                                                                   qualifiers).get()));
        }
        // SessionTokenAuthenticationDetailsProvider (from builder)
        if (isUnsatisfied(bm, SessionTokenAuthenticationDetailsProvider.class, qualifiers)) {
            event.addBean()
                .types(SessionTokenAuthenticationDetailsProvider.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i -> {
                        try {
                            return
                                fire(i,
                                     i.select(SessionTokenAuthenticationDetailsProviderBuilder.class, qualifiers).get().build(),
                                     qualifiers);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e.getMessage(), e);
                        }
                    });
        }
        // SessionTokenAuthenticationDetailsProvider (from ConfigFile)
        if (isUnsatisfied(bm, SessionTokenAuthenticationDetailsProvider.class, qualifiersPlusNamePlusOciConfig)) {
            event.addBean()
                .types(SessionTokenAuthenticationDetailsProvider.class)
                .qualifiers(qualifiersPlusNamePlusOciConfig)
                .scope(Singleton.class)
                .produceWith(i -> {
                        try {
                            return
                                fire(i,
                                     new SessionTokenAuthenticationDetailsProvider(i.select(SUPPLIER_CONFIGFILE_TYPE_LITERAL,
                                                                                            qualifiers).get()
                                                                                   .get()),
                                     qualifiers);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e.getMessage(), e);
                        }
                    });
        }
    }

    private static void installSimpleAdp(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // AdpSupplier<SimpleAuthenticationDetailsProvider>, SimpleAdpSupplier
        if (isUnsatisfied(bm, ADPSUPPLIER_SIMPLEAUTHENTICATIONDETAILSPROVIDER_TYPE, qualifiers)) {
            event.addBean()
                .types(ADPSUPPLIER_SIMPLEAUTHENTICATIONDETAILSPROVIDER_TYPE, SimpleAdpSupplier.class)
                .qualifiers(withName(qualifiers, "config"))
                .scope(Singleton.class)
                .produceWith(i ->
                             new SimpleAdpSupplier(i.select(SimpleAuthenticationDetailsProviderBuilder.class,
                                                            qualifiers)::get,
                                                   i.select(ConfigAccessor.class, qualifiers).get()));
        }
        // SimpleAuthenticationDetailsProviderBuilder
        if (isUnsatisfied(bm, SimpleAuthenticationDetailsProviderBuilder.class, qualifiers)) {
            event.addBean()
                .types(SimpleAuthenticationDetailsProviderBuilder.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             fire(i, SimpleAuthenticationDetailsProvider.builder(), qualifiers));
        }
        // SimpleAuthenticationDetailsProvider
        if (isUnsatisfied(bm, SimpleAuthenticationDetailsProvider.class, qualifiers)) {
            event.addBean()
                .types(SimpleAuthenticationDetailsProvider.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             i.select(SimpleAuthenticationDetailsProviderBuilder.class, qualifiers).get().build());
        }
    }

    private static void installAutoAdp(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // CascadingAdpSupplier<BasicAuthenticationDetailsProvider>
        if (isUnsatisfied(bm, CASCADINGADPSUPPLIER_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE, qualifiers)) {
            event.addBean()
                .addTransitiveTypeClosure(AutoAdpSupplier.class)
                .qualifiers(withName(qualifiers, "auto"))
                .scope(Singleton.class)
                .produceWith(i ->
                             new AutoAdpSupplier(i.select(ConfigAccessor.class, qualifiers).get(),
                                                 n -> adpSupplierSelector(i, qualifiers, n)));
        }
    }

    private static void installBasic(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiers) {
        // AbstractAuthenticationDetailsProvider, BasicAuthenticationDetailsProvider
        if (isUnsatisfied(bm, BasicAuthenticationDetailsProvider.class, qualifiers)) {
            event.addBean()
                .types(AbstractAuthenticationDetailsProvider.class, BasicAuthenticationDetailsProvider.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i ->
                             i.select(ADPSUPPLIER_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL,
                                      qualifiers).get()
                             .get()
                             .orElseThrow(UnsatisfiedResolutionException::new));
        }
    }

    /**
     * A method called by reference that selects an {@link AdpSupplier AdpSupplier&lt;? extends
     * BasicAuthenticationDetailsProvider&gt;} by name (and additional qualifiers).
     *
     * @param i an {@link Instance}; must not be {@code null}
     *
     * @param qualifiers additional qualifier {@link Annotation}s; must not be {@code null}
     *
     * @param n the name; must not be {@code null}
     *
     * @return an {@link AdpSupplier AdpSupplier&lt;? extends BasicAuthenticationDetailsProvider&gt;}; never {@code
     * null}
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @exception UnsatisfiedResolutionException if there is no such {@link AdpSupplier}
     */
    private static AdpSupplier<? extends BasicAuthenticationDetailsProvider> adpSupplierSelector(Instance<Object> i,
                                                                                                 Annotation[] qualifiers,
                                                                                                 String n) {
        Instance<AdpSupplier<? extends BasicAuthenticationDetailsProvider>> adpss =
            i.select(ADPSUPPLIER_WILDCARD_EXTENDS_BASICAUTHENTICATIONDETAILSPROVIDER_TYPE_LITERAL, withName(qualifiers, n));
        return adpss.isUnsatisfied() ? EmptyAdpSupplier.of() : adpss.get();
    }

    private static boolean installServiceClientBuilder(AfterBeanDiscovery event,
                                                       BeanManager bm,
                                                       TypeAndQualifiers serviceClientBuilder,
                                                       TypeAndQualifiers serviceClient,
                                                       boolean lenientClassloading) {
        if (serviceClient == null) {
            return false;
        }
        return installServiceClientBuilder(event, bm, serviceClientBuilder, serviceClient.toClass(), lenientClassloading);
    }

    private static boolean installServiceClientBuilder(AfterBeanDiscovery event,
                                                       BeanManager bm,
                                                       TypeAndQualifiers serviceClientBuilder,
                                                       Class<?> serviceClientClass,
                                                       boolean lenientClassloading) {
        if (serviceClientBuilder != null && isUnsatisfied(bm, serviceClientBuilder)) {
            Class<?> serviceClientBuilderClass = serviceClientBuilder.toClass();
            MethodHandle builderMethod;
            try {
                builderMethod = PUBLIC_LOOKUP.findStatic(serviceClientClass, "builder", methodType(serviceClientBuilderClass));
            } catch (IllegalAccessException | NoSuchMethodException reflectiveOperationException) {
                if (lenientClassloading) {
                    if (LOGGER.isLoggable(WARNING)) {
                        LOGGER.log(WARNING, serviceClientClass.getName() + ".builder() not found");
                    }
                } else {
                    event.addDefinitionError(reflectiveOperationException);
                }
                return false;
            }
            Set<Type> types = Set.of(serviceClientBuilderClass);
            Annotation[] qualifiers = serviceClientBuilder.qualifiers();
            event.addBean()
                .addTransitiveTypeClosure(serviceClientBuilderClass)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i -> produceClientBuilder(i, builderMethod, qualifiers));
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "Added synthetic bean: " + Arrays.toString(qualifiers) + " " + types);
            }
            return true;
        }
        return false;
    }

    private static boolean installServiceClient(AfterBeanDiscovery event,
                                                BeanManager bm,
                                                TypeAndQualifiers serviceClient,
                                                TypeAndQualifiers serviceInterface,
                                                TypeAndQualifiers serviceClientBuilder) {
        if (serviceInterface == null || serviceClientBuilder == null) {
            return false;
        }
        return installServiceClient(event, bm, serviceClient, serviceInterface.type(), serviceClientBuilder.toClass());
    }

    private static boolean installServiceClient(AfterBeanDiscovery event,
                                                BeanManager bm,
                                                TypeAndQualifiers serviceClient,
                                                Type serviceInterfaceType,
                                                Class<?> serviceClientBuilderClass) {
        if (serviceClient != null) {
            Annotation[] qualifiers = serviceClient.qualifiers();
            Type serviceClientType = serviceClient.type();
            if (isUnsatisfied(bm, serviceClientType, qualifiers)) {
                Set<Type> types = isUnsatisfied(bm, serviceInterfaceType, qualifiers)
                    ? Set.of(AutoCloseable.class, Object.class, serviceClientType, serviceInterfaceType)
                    : Set.of(AutoCloseable.class, Object.class, serviceClientType);
                event.addBean()
                    .types(types)
                    .qualifiers(qualifiers)
                    .scope(Singleton.class)
                    .produceWith(i -> produceClient(i, serviceClientBuilderClass, qualifiers))
                    .disposeWith(OciExtension::disposeClient);
                return true;
            }
        }
        return false;
    }

    private static MicroProfileConfigConfigAccessor produceConfigAccessor(Instance<? super Object> instance,
                                                                          Annotation[] qualifiers) {
        Instance<Config> configInstance = instance.select(Config.class, qualifiers);
        if (configInstance.isUnsatisfied()) {
            configInstance = instance.select(Config.class);
        }
        return new MicroProfileConfigConfigAccessor(configInstance.get());
    }

    private static Object produceClientBuilder(Instance<? super Object> instance,
                                               MethodHandle builderMethod,
                                               Annotation[] qualifiers) {
        ClientBuilderBase<?, ?> builderInstance;
        try {
            builderInstance = (ClientBuilderBase<?, ?>) builderMethod.invoke();
        } catch (RuntimeException | Error runtimeExceptionOrError) {
            throw runtimeExceptionOrError;
        } catch (Throwable exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CreationException(exception.getMessage(), exception);
        }
        // Permit arbitrary customization.
        return fire(instance, builderInstance, qualifiers);
    }

    private static Object produceClient(Instance<? super Object> instance, Class<?> builderClass, Annotation[] qualifiers) {
        return
            ((ClientBuilderBase<?, ?>) instance.select(builderClass, qualifiers).get())
            .build(instance.select(AbstractAuthenticationDetailsProvider.class, qualifiers).get());
    }

    private static void disposeClient(Object client, Object ignored) {
        if (client instanceof AutoCloseable) {
            close((AutoCloseable) client);
        }
    }

    private static Annotation[] withName(Annotation[] qualifiers, String name) {
        Annotation[] qualifiersWithName = new Annotation[qualifiers.length + 1];
        System.arraycopy(qualifiers, 0, qualifiersWithName, 0, qualifiers.length);
        qualifiersWithName[qualifiers.length] = NamedLiteral.of(name);
        return qualifiersWithName;
    }

    private static void close(AutoCloseable autoCloseable) {
        if (autoCloseable != null) {
            try {
                autoCloseable.close();
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException(exception.getMessage(), exception);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T fire(Instance<? super Object> instance, T payload, Annotation[] qualifiers) {
        instance.select(EVENT_OBJECT_TYPE_LITERAL).get().select((Class<T>) payload.getClass(), qualifiers).fire(payload);
        return payload;
    }

    private static boolean isUnsatisfied(BeanManager bm, TypeAndQualifiers taq) {
        return isUnsatisfied(bm, taq.type(), taq.qualifiers());
    }

    private static boolean isUnsatisfied(BeanManager bm, Type type, Annotation[] qualifiers) {
        Set<Bean<?>> beans = bm.getBeans(type, qualifiers);
        if (beans == null || beans.isEmpty()) {
            return true;
        }
        try {
            return bm.resolve(beans) == null;
        } catch (AmbiguousResolutionException ambiguousResolutionException) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, ambiguousResolutionException.getMessage(), ambiguousResolutionException);
            }
            return false;
        }
    }

    private static boolean equals(ProtectionDomain pd0, ProtectionDomain pd1) {
        if (pd0 == null) {
            return pd1 == null;
        } else if (pd1 == null) {
            return false;
        }
        return equals(pd0.getCodeSource(), pd1.getCodeSource());
    }

    private static boolean equals(CodeSource cs0, CodeSource cs1) {
        if (cs0 == null) {
            return cs1 == null;
        } else if (cs1 == null) {
            return false;
        }
        return equals(cs0.getLocation(), cs1.getLocation());
    }

    private static boolean equals(URL url0, URL url1) {
        if (url0 == null) {
            return url1 == null;
        } else if (url1 == null) {
            return false;
        }
        try {
            return Objects.equals(url0.toURI(), url1.toURI());
        } catch (URISyntaxException uriSyntaxException) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, uriSyntaxException.getMessage(), uriSyntaxException);
            }
            // Use URL#equals(Object) only as a last resort, since it involves DNS lookups (!).
            return url0.equals(url1);
        }
    }

    private static Class<?> loadClassUnresolved(String name) throws ClassNotFoundException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return Class.forName(name, false, cl == null ? OciExtension.class.getClassLoader() : cl);
    }


    /*
     * Inner and nested classes.
     */


    private record TypeAndQualifiers(Type type, Annotation[] qualifiers) {

        private TypeAndQualifiers {
            Objects.requireNonNull(type, "type");
            qualifiers = qualifiers == null ? EMPTY_ANNOTATION_ARRAY : qualifiers.clone();
        }

        private Class<?> toClass() {
            Type t = this.type();
            if (t instanceof Class<?> c) {
                return c;
            } else if (t instanceof ParameterizedType pt) {
                return (Class<?>) pt.getRawType();
            } else {
                return null;
            }
        }

        // Record's implicit equals() Overridden to ensure Arrays.equals() is used on qualifiers.
        @Override // Record
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (other != null && other.getClass() == this.getClass()) {
                TypeAndQualifiers her = (TypeAndQualifiers) other;
                return Objects.equals(this.type(), her.type()) && Arrays.equals(this.qualifiers(), her.qualifiers());
            } else {
                return false;
            }
        }

        // Record's implicit hashCode() Overridden to ensure Arrays.hashCode() is used on qualifiers.
        @Override // Record
        public int hashCode() {
            int hashCode = 17;
            int c = this.type().hashCode();
            hashCode = 37 * hashCode + c;
            c = Arrays.hashCode(this.qualifiers());
            hashCode = 37 * hashCode + c;
            return hashCode;
        }

        @Override // Record
        public String toString() {
            return Arrays.asList(this.qualifiers()).toString() + " " + this.type().toString();
        }

    }

    private record ServiceTaqs(Annotation[] qualifiers,
                               Type serviceInterfaceType,
                               Type serviceClientType,
                               Type serviceClientBuilderType) {

        private ServiceTaqs {
            if (qualifiers == null) {
                qualifiers = EMPTY_ANNOTATION_ARRAY;
            }
        }

        private ServiceTaqs(Annotation[] qualifiers) {
            this(qualifiers, null, null, null);
        }

        private TypeAndQualifiers serviceInterface() {
            return new TypeAndQualifiers(this.serviceInterfaceType(), this.qualifiers());
        }

        private TypeAndQualifiers serviceClient() {
            return new TypeAndQualifiers(this.serviceClientType(), this.qualifiers());
        }

        private TypeAndQualifiers serviceClientBuilder() {
            return new TypeAndQualifiers(this.serviceClientBuilderType(), this.qualifiers());
        }

        private boolean isEmpty() {
            return this.serviceInterfaceType() == null
                && this.serviceClientType() == null
                && this.serviceClientBuilderType() == null;
        }

        // Record's implicit equals() overridden to ensure Arrays.equals() is used on qualifiers.
        @Override // Record
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (other != null && other.getClass() == this.getClass()) {
                ServiceTaqs her = (ServiceTaqs) other;
                return
                    Arrays.equals(this.qualifiers(), her.qualifiers())
                    && Objects.equals(this.serviceInterfaceType(), her.serviceInterfaceType())
                    && Objects.equals(this.serviceClientType(), her.serviceClientType())
                    && Objects.equals(this.serviceClientBuilderType(), her.serviceClientBuilderType());
            } else {
                return false;
            }
        }

        // Record's implicit equals() overridden to ensure Arrays.hashCode() is used on qualifiers.
        @Override // Record
        public int hashCode() {
            int hashCode = 17;
            Annotation[] qualifiers = this.qualifiers();
            int c = qualifiers == null ? 0 : Arrays.hashCode(qualifiers);
            hashCode = 37 * hashCode + c;
            Type x = this.serviceInterfaceType();
            c = x == null ? 0 : x.hashCode();
            hashCode = 37 * hashCode + c;
            x = this.serviceClientType();
            c = x == null ? 0 : x.hashCode();
            hashCode = 37 * hashCode + c;
            x = this.serviceClientBuilderType();
            c = x == null ? 0 : x.hashCode();
            hashCode = 37 * hashCode + c;
            return hashCode;
        }

    }

}
