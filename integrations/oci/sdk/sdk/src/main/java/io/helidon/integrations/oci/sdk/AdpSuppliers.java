/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.auth.StringPrivateKeySupplier;

import static java.lang.System.Logger;

/**
 * A utility class containing methods that produce {@link Supplier}s of various kinds of {@link
 * BasicAuthenticationDetailsProvider} instances.
 *
 * @see #adpSupplier(Function)
 */
public final class AdpSuppliers {

    private static final Logger LOGGER = System.getLogger(AdpSuppliers.class.getName());

    private static final String DEFAULT_OCI_AUTH_PRIVATE_KEY_PATH =
        Paths.get(System.getProperty("user.home"), ".oci", "oci_api_key.pem").toString();

    private static final int DEFAULT_OCI_IMDS_TIMEOUT_MILLIS = 100;

    private static final String DEFAULT_OCI_CONFIG_PROFILE = "DEFAULT";

    private static final String OCI_AUTH_FINGERPRINT = "oci.auth.fingerprint";

    private static final String OCI_AUTH_PASSPHRASE = "oci.auth.passphrase"; // optional for simple

    private static final String OCI_AUTH_PRIVATE_KEY = "oci.auth.private-key"; // optional for simple

    private static final String OCI_AUTH_PRIVATE_KEY_PATH = OCI_AUTH_PRIVATE_KEY + "-path"; // optional for simple

    private static final String OCI_AUTH_REGION = "oci.auth.region";

    private static final String OCI_AUTH_TENANT_ID = "oci.auth.tenant-id";

    private static final String OCI_AUTH_USER_ID = "oci.auth.user-id";

    private static final String OCI_CONFIG_PATH = "oci.config.path";

    private static final String OCI_CONFIG_PROFILE = "oci.config.profile";

    private static final String OCI_IMDS_TIMEOUT_MILLIS = "oci.imds.timeout.milliseconds";

    private static final String OCI_RESOURCE_PRINCIPAL_VERSION = "OCI_RESOURCE_PRINCIPAL_VERSION";

    private AdpSuppliers() {
        super();
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link SimpleAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #simple(Function, Supplier) simple}(c, {@linkplain SimpleAuthenticationDetailsProvider#builder SimpleAuthenticationDetailsProvider}::{@linkplain SimpleAuthenticationDetailsProvider#builder() builder});</pre></blockquote>
     *
     * @param c a configuration accessor&mdash;that is, an accessor of {@link String}-typed configuration property
     * values which, when supplied with a configuration property name, supplies a (possibly {@linkplain
     * Optional#isEmpty() empty}) {@link Optional} housing its value; must not be {@code null}
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link SimpleAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #simple(Function, Supplier)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<SimpleAuthenticationDetailsProvider>> simple(Function<? super String, ? extends Optional<String>> c) {
        return simple(c, SimpleAuthenticationDetailsProvider::builder);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link SimpleAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #simple(Function, Supplier) simple}(c, bs, b -> b::{@linkplain SimpleAuthenticationDetailsProviderBuilder#build() build});</pre></blockquote>
     *
     * @param c a configuration accessor&mdash;that is, an accessor of {@link String}-typed configuration property
     * values which, when supplied with a configuration property name, supplies a (possibly {@linkplain
     * Optional#isEmpty() empty}) {@link Optional} housing its value; must not be {@code null}
     *
     * @param bs a {@link Supplier} of {@link SimpleAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; must not return
     * {@code null} from its {@link Supplier#get() get()} method; values of certain properties
     * of its resulting builders will be deliberately overwritten by values found by the supplied configuration accessor
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link SimpleAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #simple(Function, Supplier, Function)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<SimpleAuthenticationDetailsProvider>> simple(Function<? super String, ? extends Optional<String>> c,
                                                                                 Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> bs) {
        return simple(c, bs, b -> b::build);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link SimpleAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method uses the supplied configuration accessor ({@code c}) to acquire {@link
     * String}-typed values for the following logical configuration property names, in order, short-circuiting by
     * returning an {@linkplain Optional#isEmpty() empty} {@link Optional} as soon as a value is absent for a given
     * name:</p>
     *
     * <ol>
     *
     * <li>{@value OCI_AUTH_FINGERPRINT}</li>
     *
     * <li>{@value OCI_AUTH_REGION}</li>
     *
     * <li>{@value OCI_AUTH_TENANT_ID}</li>
     *
     * <li>{@value OCI_AUTH_USER_ID}</li>
     *
     * </ol>
     *
     * <p>If values for all of these configuration property names are acquired successfully, values for the following
     * configuration property names will subsequently be sought:</p>
     *
     * <ol>
     *
     * <li>{@value OCI_AUTH_PASSPHRASE} (optional; used as-is if present)</li>
     *
     * <li>{@value OCI_AUTH_PRIVATE_KEY} (optional; provided as an argument to the {@link
     * StringPrivateKeySupplier#StringPrivateKeySupplier(String)} constructor if present; see below for absence
     * behavior)</li>
     *
     * </ol>
     *
     * <p>If a value for the configuration property named {@value OCI_AUTH_PRIVATE_KEY} is absent, then a value for the
     * configuration property named {@value OCI_AUTH_PRIVATE_KEY_PATH} will be sought instead (if it is absent, then the
     * semantic equivalent of {@code Paths.get(System.getProperty("user.home"), ".oci", "oci_api_key.pem").toString()}
     * will be used instead) and provided as an argument to the {@link
     * SimplePrivateKeySupplier#SimplePrivateKeySupplier(String)} constructor.</p>
     *
     * <p>These configuration property values, properly converted where necessary, will be installed on the {@link
     * SimpleAuthenticationDetailsProviderBuilder} supplied by the supplied {@link Supplier
     * Supplier&lt;SimpleAuthenticationDetailsProviderBuilder&gt;} in idiomatic fashion. For example, the value for the
     * configuration property named {@value OCI_AUTH_FINGERPRINT} will be installed on the {@link
     * SimpleAuthenticationDetailsProviderBuilder} by calling its {@link
     * SimpleAuthenticationDetailsProviderBuilder#fingerprint(String)} method. Any prior values installed on the {@link
     * SimpleAuthenticationDetailsProviderBuilder} by the caller will be overwritten.</p>
     *
     * <p>The {@link SimpleAuthenticationDetailsProviderBuilder} that is configured in this manner will then be passed
     * to the supplied customization {@link Function}, whose responsibility is to use its supplied {@link
     * SimpleAuthenticationDetailsProviderBuilder} to {@linkplain SimpleAuthenticationDetailsProviderBuilder#build()
     * build} and return a {@link SimpleAuthenticationDetailsProvider}.  The return value of this {@link Function} will
     * be returned by this method unchanged.</p>
     *
     * @param c a configuration accessor&mdash;that is, an accessor of {@link String}-typed configuration property
     * values which, when supplied with a configuration property name, supplies a (possibly {@linkplain
     * Optional#isEmpty() empty}) {@link Optional} housing its value; must not be {@code null}
     *
     * @param bs a {@link Supplier} of {@link SimpleAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; must not return
     * {@code null} from its {@link Supplier#get() get()} method; values of certain properties
     * of its resulting builders will be deliberately overwritten by values found by the supplied configuration accessor
     *
     * @param f a {@link Function} that accepts a {@link SimpleAuthenticationDetailsProviderBuilder} and uses it to
     * {@linkplain SimpleAuthenticationDetailsProviderBuilder#build() build} and return {@link
     * SimpleAuthenticationDetailsProvider} instances; must not be {@code null}; is often simply, e.g., {@code b ->
     * b::build} if further customization of the configured builder is not necessary
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link SimpleAuthenticationDetailsProvider} instances.
     *
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<SimpleAuthenticationDetailsProvider>> simple(Function<? super String, ? extends Optional<String>> c,
                                                                                 Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> bs,
                                                                                 Function<? super SimpleAuthenticationDetailsProviderBuilder, ? extends Supplier<SimpleAuthenticationDetailsProvider>> f) {
        return c.apply(OCI_AUTH_FINGERPRINT)
            .flatMap(fingerprint -> c.apply(OCI_AUTH_REGION)
                     .flatMap(region -> c.apply(OCI_AUTH_TENANT_ID)
                              .flatMap(tenantId -> c.apply(OCI_AUTH_USER_ID)
                                       .map(userId -> {
                                               var b = bs.get();
                                               b.fingerprint(fingerprint);
                                               b.region(Region.valueOf(region));
                                               b.tenantId(tenantId);
                                               b.userId(userId);
                                               c.apply(OCI_AUTH_PASSPHRASE).ifPresent(b::passPhrase);
                                               c.apply(OCI_AUTH_PRIVATE_KEY)
                                                   .ifPresentOrElse(pk -> b.privateKeySupplier(new StringPrivateKeySupplier(pk)),
                                                                    () -> b.privateKeySupplier(new SimplePrivateKeySupplier(c.apply(OCI_AUTH_PRIVATE_KEY_PATH)
                                                                                                                            .orElse(DEFAULT_OCI_AUTH_PRIVATE_KEY_PATH))));
                                               return f.apply(b);
                                           }))));
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ConfigFileAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #configFile(String, String) configFile}(c.{@linkplain Function#apply(Object) apply}({@value OCI_CONFIG_PATH}).{@linkplain Optional#orElse(Object) orElse}(null), c.{@linkplain Function#apply(Object) apply}({@value OCI_CONFIG_PROFILE}).{@linkplain Optional#orElse(Object) orElse}({@value DEFAULT_OCI_CONFIG_PROFILE}));</pre></blockquote>
     *
     * @param c a configuration accessor&mdash;that is, an accessor of {@link String}-typed configuration property
     * values which, when supplied with a configuration property name, supplies a (possibly {@linkplain
     * Optional#isEmpty() empty}) {@link Optional} housing its value; must not be {@code null}
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ConfigFileAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if {@code c} is {@code null}
     *
     * @exception UncheckedIOException if an input/output error occurs
     *
     * @see #configFile(String, String)
     */
    public static Optional<Supplier<ConfigFileAuthenticationDetailsProvider>> configFile(Function<? super String, ? extends Optional<String>> c) {
        return
            configFile(c.apply(OCI_CONFIG_PATH).orElse(null),
                       c.apply(OCI_CONFIG_PROFILE).orElse(DEFAULT_OCI_CONFIG_PROFILE));
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ConfigFileAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #configFile(String) configFile}((String) null);</pre></blockquote>
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ConfigFileAuthenticationDetailsProvider} instances
     *
     * @exception UncheckedIOException if an input/output error occurs
     *
     * @see #configFile(String)
     */
    public static Optional<Supplier<ConfigFileAuthenticationDetailsProvider>> configFile() {
        return configFile((String) null);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ConfigFileAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #configFile(String) configFile}(null, "DEFAULT");</pre></blockquote>
     *
     * @param ociConfigPath a path suitable for passing to the {@link
     * ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(String, String)} constructor, or
     * {@code null} to indicate that an underlying {@link ConfigFileAuthenticationDetailsProvider} should use a default
     * path
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ConfigFileAuthenticationDetailsProvider} instances
     *
     * @exception UncheckedIOException if an input/output error occurs
     *
     * @see #configFile(String, String)
     *
     * @see ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(String)
     *
     * @see ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(String, String)
     */
    public static Optional<Supplier<ConfigFileAuthenticationDetailsProvider>> configFile(String ociConfigPath) {
        return configFile(ociConfigPath, DEFAULT_OCI_CONFIG_PROFILE);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ConfigFileAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method will return an {@linkplain Optional#isEmpty() empty} {@link Optional} if a
     * <a href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm">valid OCI configuration file</a>
     * could not be found at either the location designated by the supplied {@code ociConfigPath} as interpreted by the
     * {@link ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(String, String)}
     * constructor, or, if the supplied {@code ociConfigPath} is {@code null}, at the default location as determined by
     * the {@link ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(String)}
     * constructor.</p>
     *
     * @param ociConfigPath a path suitable for passing to the {@link
     * ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(String, String)} constructor, or
     * {@code null} to indicate that an underlying {@link ConfigFileAuthenticationDetailsProvider} should use a default
     * path
     *
     * @param ociConfigProfile the configuration profile within the OCI configuration file to use; may be {@code null}
     * in which case "{@code DEFAULT}" will be used instead
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ConfigFileAuthenticationDetailsProvider} instances
     *
     * @exception UncheckedIOException if an input/output error occurs
     *
     * @see ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(String)
     *
     * @see ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(String, String)
     */
    public static Optional<Supplier<ConfigFileAuthenticationDetailsProvider>> configFile(String ociConfigPath,
                                                                                         String ociConfigProfile) {
        if (ociConfigProfile == null) {
            ociConfigProfile = DEFAULT_OCI_CONFIG_PROFILE;
        }
        ConfigFileAuthenticationDetailsProvider adp;
        try {
            if (ociConfigPath == null) {
                adp = new ConfigFileAuthenticationDetailsProvider(ociConfigProfile);
            } else {
                adp = new ConfigFileAuthenticationDetailsProvider(ociConfigPath, ociConfigProfile);
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            // The underlying ConfigFileReader that does the real work does not throw a FileNotFoundException (as it
            // probably should) when it cannot find the configuration file. To distinguish this "ordinary" IOException
            // from other IOExceptions, we therefore have no choice but to parse the error message. See
            // https://github.com/oracle/oci-java-sdk/blob/v3.21.0/bmc-common/src/main/java/com/oracle/bmc/ConfigFileReader.java#L91-L95.
            String message = e.getMessage();
            if (message != null
                && message.startsWith("Can't load the default config from ")
                && message.endsWith(" because it does not exist or it is not a file.")) {
                return Optional.empty();
            }
            // It's not a "file not found" case; it's some other exception.
            throw new UncheckedIOException(message, e);
        }
        return Optional.of(() -> adp);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #instancePrincipals(Function, Supplier) instancePrincipals}(c, {@linkplain InstancePrincipalsAuthenticationDetailsProvider InstancePrincipalsAuthenticationDetailsProvider}::{@linkplain InstancePrincipalsAuthenticationDetailsProvider#builder() builder});</pre></blockquote>
     *
     * @param c a configuration accessor&mdash;that is, an accessor of {@link String}-typed configuration property
     * values which, when supplied with a configuration property name, supplies a (possibly {@linkplain
     * Optional#isEmpty() empty}) {@link Optional} housing its value; must not be {@code null}
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #instancePrincipals(int, Supplier)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(Function<? super String, ? extends Optional<String>> c) {
        return instancePrincipals(c, InstancePrincipalsAuthenticationDetailsProvider::builder);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #instancePrincipals(Function, Supplier, Function) instancePrincipals}(c, bs, b -> b::{@linkplain InstancePrincipalsAuthenticationDetailsProviderBuilder#build() build});</pre></blockquote>
     *
     * @param c a configuration accessor&mdash;that is, an accessor of {@link String}-typed configuration property
     * values which, when supplied with a configuration property name, supplies a (possibly {@linkplain
     * Optional#isEmpty() empty}) {@link Optional} housing its value; must not be {@code null}
     *
     * @param bs a {@link Supplier} of {@link InstancePrincipalsAuthenticationDetailsProviderBuilder} instances; must
     * not be {@code null}; is often simply, e.g., {@code InstancePrincipalsAuthenticationDetailsProvider::build}
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #instancePrincipals(int, Supplier, Function)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(Function<? super String, ? extends Optional<String>> c,
                                                                                                         Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs) {
        return instancePrincipals(c, bs, b -> b::build);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method uses the supplied configuration accessor ({@code c}) to acquire a {@link
     * String}-typed value for the logical configuration property named {@value OCI_IMDS_TIMEOUT_MILLIS}. This value, if
     * present and valid, will be interpreted as the number of milliseconds to wait for the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm">IMDS</a> service to be found;
     * if this time elapses and the service is not found, an {@linkplain Optional#isEmpty() empty} {@link Optional} will
     * be returned. (If the value is absent, {@value DEFAULT_OCI_IMDS_TIMEOUT_MILLIS} will be used instead.)</p>
     *
     * @param c a configuration accessor&mdash;that is, an accessor of {@link String}-typed configuration property
     * values which, when supplied with a configuration property name, supplies a (possibly {@linkplain
     * Optional#isEmpty() empty}) {@link Optional} housing its value; must not be {@code null}
     *
     * @param bs a {@link Supplier} of {@link InstancePrincipalsAuthenticationDetailsProviderBuilder} instances; must
     * not be {@code null}; is often simply, e.g., {@code InstancePrincipalsAuthenticationDetailsProvider::build}
     *
     * @param f a {@link Function} that accepts an {@link InstancePrincipalsAuthenticationDetailsProviderBuilder} and
     * uses it to {@linkplain InstancePrincipalsAuthenticationDetailsProviderBuilder#build() build} and return {@link
     * InstancePrincipalsAuthenticationDetailsProvider} instances; must not be {@code null}; is often simply, e.g.,
     * {@code b -> b::build} if further customization of the configured builder is not necessary
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #instancePrincipals(int, Supplier, Function)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(Function<? super String, ? extends Optional<String>> c,
                                                                                                         Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs,
                                                                                                         Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder, ? extends Supplier<InstancePrincipalsAuthenticationDetailsProvider>> f) {
        int timeoutPositiveMillis = DEFAULT_OCI_IMDS_TIMEOUT_MILLIS;
        try {
            timeoutPositiveMillis =
                Math.max(0,
                         c.apply(OCI_IMDS_TIMEOUT_MILLIS)
                             .map(Integer::valueOf)
                             .orElse(DEFAULT_OCI_IMDS_TIMEOUT_MILLIS));
        } catch (NumberFormatException e) {
        }
        return instancePrincipals(timeoutPositiveMillis, bs, f);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #instancePrincipals(int) instancePrincipals}({@value DEFAULT_OCI_IMDS_TIMEOUT_MILLIS});</pre></blockquote>
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances
     *
     * @see #instancePrincipals(int)
     */
    public static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals() {
        return instancePrincipals(DEFAULT_OCI_IMDS_TIMEOUT_MILLIS);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #instancePrincipals(int, Supplier) instancePrincipals}(timeoutPositiveMillis, {@linkplain InstancePrincipalsAuthenticationDetailsProvider InstancePrincipalsAuthenticationDetailsProvider}::{@linkplain InstancePrincipalsAuthenticationDetailsProvider#builder() builder});</pre></blockquote>
     *
     * @param timeoutPositiveMillis a positive {@code int} representing the number of milliseconds to wait for the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm">IMDS</a> service to be found;
     * if this time elapses and the service is not found, an {@linkplain Optional#isEmpty() empty} {@link Optional} will
     * be returned
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances
     *
     * @see #instancePrincipals(int, Supplier)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(int timeoutPositiveMillis) {
        return instancePrincipals(timeoutPositiveMillis, InstancePrincipalsAuthenticationDetailsProvider::builder);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #instancePrincipals(int, Supplier, Function) instancePrincipals}(timeoutPositiveMillis, bs, b -> b::{@linkplain InstancePrincipalsAuthenticationDetailsProviderBuilder#build() build});</pre></blockquote>
     *
     * @param timeoutPositiveMillis a positive {@code int} representing the number of milliseconds to wait for the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm">IMDS</a> service to be found;
     * if this time elapses and the service is not found, an {@linkplain Optional#isEmpty() empty} {@link Optional} will
     * be returned
     *
     * @param bs a {@link Supplier} of {@link InstancePrincipalsAuthenticationDetailsProviderBuilder} instances; must
     * not be {@code null}; is often simply, e.g., {@code InstancePrincipalsAuthenticationDetailsProvider::build}
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances
     *
     * @see #instancePrincipals(int, Supplier, Function)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(int timeoutPositiveMillis,
                                                                                                         Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs) {
        return instancePrincipals(timeoutPositiveMillis, bs, b -> b::build);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances.
     *
     * @param timeoutPositiveMillis a positive {@code int} representing the number of milliseconds to wait for the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm">IMDS</a> service to be found;
     * if this time elapses and the service is not found, an {@linkplain Optional#isEmpty() empty} {@link Optional} will
     * be returned
     *
     * @param bs a {@link Supplier} of {@link InstancePrincipalsAuthenticationDetailsProviderBuilder} instances; must
     * not be {@code null}; is often simply, e.g., {@code InstancePrincipalsAuthenticationDetailsProvider::build}
     *
     * @param f a {@link Function} that accepts an {@link InstancePrincipalsAuthenticationDetailsProviderBuilder} and
     * uses it to {@linkplain InstancePrincipalsAuthenticationDetailsProviderBuilder#build() build} and return {@link
     * InstancePrincipalsAuthenticationDetailsProvider} instances; must not be {@code null}; is often simply, e.g.,
     * {@code b -> b::build} if further customization of the configured builder is not necessary
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(int timeoutPositiveMillis,
                                                                                                         Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs,
                                                                                                         Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder, ? extends Supplier<InstancePrincipalsAuthenticationDetailsProvider>> f) {
        var b = bs.get();
        try {
            if (InetAddress.getByName(URI.create(b.getMetadataBaseUrl()).getHost()).isReachable(timeoutPositiveMillis)) {
                return Optional.of(f.apply(b));
            }
        } catch (ConnectException e) {
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ResourcePrincipalAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #resourcePrincipal(Supplier) resourcePrincipal}({@linkplain ResourcePrincipalAuthenticationDetailsProvider ResourcePrincipalAuthenticationDetailsProvider}::{@linkplain ResourcePrincipalAuthenticationDetailsProvider#builder() builder});</pre></blockquote>
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ResourcePrincipalAuthenticationDetailsProvider} instances
     *
     * @see #resourcePrincipal(Supplier)
     */
    public static Optional<Supplier<ResourcePrincipalAuthenticationDetailsProvider>> resourcePrincipal() {
        return resourcePrincipal(ResourcePrincipalAuthenticationDetailsProvider::builder);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ResourcePrincipalAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #resourcePrincipal(Supplier, Function) resourcePrincipal}(bs, b -> b::{@linkplain ResourcePrincipalAuthenticationDetailsProviderBuilder#build() build});</pre></blockquote>
     *
     * @param bs a {@link Supplier} of {@link ResourcePrincipalAuthenticationDetailsProviderBuilder} instances; must
     * not be {@code null}; is often simply, e.g., {@code ResourcePrincipalAuthenticationDetailsProvider::build}
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ResourcePrincipalAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if {@code bs} is {@code null}
     *
     * @see #resourcePrincipal(Supplier, Function)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<ResourcePrincipalAuthenticationDetailsProvider>> resourcePrincipal(Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> bs) {
        return resourcePrincipal(bs, b -> b::build);
    }

    /**
     * Returns a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ResourcePrincipalAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #resourcePrincipal(Supplier, Function) resourcePrincipal}(bs, b -> b::{@linkplain ResourcePrincipalAuthenticationDetailsProviderBuilder#build() build});</pre></blockquote>
     *
     * @param bs a {@link Supplier} of {@link ResourcePrincipalAuthenticationDetailsProviderBuilder} instances; must
     * not be {@code null}; is often simply, e.g., {@code ResourcePrincipalAuthenticationDetailsProvider::build}
     *
     * @param f a {@link Function} that accepts an {@link ResourcePrincipalAuthenticationDetailsProviderBuilder} and
     * uses it to {@linkplain ResourcePrincipalAuthenticationDetailsProviderBuilder#build() build} and return {@link
     * ResourcePrincipalAuthenticationDetailsProvider} instances; must not be {@code null}; is often simply, e.g.,
     * {@code b -> b::build} if further customization of the configured builder is not necessary
     *
     * @return a non-{@code null} (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} housing a {@link
     * Supplier} of {@link ResourcePrincipalAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Optional<Supplier<ResourcePrincipalAuthenticationDetailsProvider>> resourcePrincipal(Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> bs,
                                                                                                       Function<? super ResourcePrincipalAuthenticationDetailsProviderBuilder, ? extends Supplier<ResourcePrincipalAuthenticationDetailsProvider>> f) {
        return Optional.ofNullable(System.getenv(OCI_RESOURCE_PRINCIPAL_VERSION) == null ? null : f.apply(bs.get()));
    }

    /**
     * Returns a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #adpSupplier(Stream) adpSupplier}({@linkplain Stream Stream}.{@linkplain Stream#of(Object...) of}({@linkplain #simple(Function) simple}(c), {@linkplain #configFile(Function) configFile}(c), {@linkplain #instancePrincipals(Function) instancePrincipals}(c), {@linkplain resourcePrincipal() resourcePrincipal}()));</pre></blockquote>
     *
     * <p>Please see documentation for the following methods to see how the supplied configuration accessor may be
     * used:</p>
     *
     * <ul>
     *
     * <li>{@link #simple(Function)}</li>
     *
     * <li>{@link #configFile(Function)}</li>
     *
     * <li>{@link #instancePrincipals(Function)}</li>
     *
     * </ul>
     *
     * @param c a configuration accessor&mdash;that is, an accessor of {@link String}-typed configuration property
     * values which, when supplied with a configuration property name, supplies a (possibly {@linkplain
     * Optional#isEmpty() empty}) {@link Optional} housing its value; must not be {@code null}
     *
     * @return a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if {@code c} is {@code null}
     *
     * @exception java.util.NoSuchElementException if no {@link BasicAuthenticationDetailsProvider} is logically
     * available
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Function<? super String, ? extends Optional<String>> c) {
        return adpSupplier(Stream.of(simple(c), configFile(c), instancePrincipals(c), resourcePrincipal()));
    }

    /**
     * Returns a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #adpSupplier(Stream) adpSupplier}({@linkplain Stream Stream}.{@linkplain Stream#of(Object...) of}(o0, o1));</pre></blockquote>
     *
     * @param o0 the first {@link Optional}; must not be {@code null}
     *
     * @param o1 the second {@link Optional}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @exception java.util.NoSuchElementException if all of the {@link Optional}s in the supplied {@link Collection} are
     * {@linkplain Optional#isEmpty() empty}
     *
     * @see #adpSupplier(Stream)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o0,
                                                                                     Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o1) {
        return adpSupplier(Stream.of(o0, o1));
    }

    /**
     * Returns a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #adpSupplier(Stream) adpSupplier}({@linkplain Stream Stream}.{@linkplain Stream#of(Object...) of}(o0, o1, o2));</pre></blockquote>
     *
     * @param o0 the first {@link Optional}; must not be {@code null}
     *
     * @param o1 the second {@link Optional}; must not be {@code null}
     *
     * @param o2 the third {@link Optional}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @exception java.util.NoSuchElementException if all of the {@link Optional}s in the supplied {@link Collection} are
     * {@linkplain Optional#isEmpty() empty}
     *
     * @see #adpSupplier(Stream)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o0,
                                                                                     Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o1,
                                                                                     Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o2) {
        return adpSupplier(Stream.of(o0, o1, o2));
    }

    /**
     * Returns a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #adpSupplier(Stream) adpSupplier}({@linkplain Stream Stream}.{@linkplain Stream#of(Object...) of}(o0, o1, o2, o3));</pre></blockquote>
     *
     * @param o0 the first {@link Optional}; must not be {@code null}
     *
     * @param o1 the second {@link Optional}; must not be {@code null}
     *
     * @param o2 the third {@link Optional}; must not be {@code null}
     *
     * @param o3 the fourth {@link Optional}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @exception java.util.NoSuchElementException if all of the {@link Optional}s in the supplied {@link Collection} are
     * {@linkplain Optional#isEmpty() empty}
     *
     * @see #adpSupplier(Stream)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o0,
                                                                                     Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o1,
                                                                                     Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o2,
                                                                                     Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o3) {
        return adpSupplier(Stream.of(o0, o1, o2, o3));
    }

    /**
     * Returns a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return {@linkplain #adpSupplier(Stream) adpSupplier}(c.{@linkplain Collection#stream() stream()});</pre></blockquote>
     *
     * @param c a {@link Collection} of {@link Optional}s, each element of which houses no or one instance of {@link
     * BasicAuthenticationDetailsProvider}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if {@code s} is {@code null}
     *
     * @exception java.util.NoSuchElementException if all of the {@link Optional}s in the supplied {@link Collection} are
     * {@linkplain Optional#isEmpty() empty}
     *
     * @see #adpSupplier(Stream)
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Collection<? extends Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>>> c) {
        return adpSupplier(c.stream());
    }

    /**
     * Returns a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances.
     *
     * <p>The implementation of this method is semantically equal to:</p>
     *
     * <blockquote><pre>return s.{@linkplain Stream#flatMap(Function) flatMap}({@linkplain Optional Optional}::{@linkplain Optional#stream() stream}).{@linkplain Stream#findFirst() findFirst()}.orElseThrow();</pre></blockquote>
     *
     * @param s a {@link Stream} of {@link Optional}s, each element of which houses no or one instance of {@link
     * BasicAuthenticationDetailsProvider}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances
     *
     * @exception NullPointerException if {@code s} is {@code null}
     *
     * @exception java.util.NoSuchElementException if all of the {@link Optional}s in the supplied {@link Stream} are
     * {@linkplain Optional#isEmpty() empty}
     */
    @SuppressWarnings("checkstyle:linelength")
    public static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Stream<? extends Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>>> s) {
        return s
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow();
    }

}
