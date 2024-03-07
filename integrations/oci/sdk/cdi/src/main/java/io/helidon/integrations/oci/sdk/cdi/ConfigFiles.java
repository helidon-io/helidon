/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.ConfigFileReader.ConfigFile;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * A utility class for working with {@link ConfigFile ConfigFile} instances, {@link ConfigFileReader} instances, and
 * their many idiosyncracies.
 *
 * <p>{@link ConfigFile ConfigFile} production is ultimately controlled by the {@link ConfigFileReader#parseDefault()},
 * {@link ConfigFileReader#parseDefault(String)}, and {@link ConfigFileReader#parse(String, String)} methods. They are
 * subtly different from one another.</p>
 *
 * <p>The {@link #parseDefault()}, {@link #parseDefault(String)}, and {@link #parse(String, String)} methods in this
 * class are convenience methods that invoke their canonical counterparts and wrap any {@link IOException}s thrown in
 * {@link UncheckedIOException}s, but otherwise perform no additional logic.</p>
 *
 * @see #parseDefault()
 *
 * @see #parseDefault(String)
 *
 * @see #parse(String, String)
 *
 * @see ConfigFileReader#parseDefault()
 *
 * @see ConfigFileReader#parseDefault(String)
 *
 * @see ConfigFileReader#parse(String, String)
 */
final class ConfigFiles {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = System.getLogger(ConfigFiles.class.getName());


    /*
     * Constructors.
     */


    private ConfigFiles() {
        super();
    }


    /*
     * Static methods.
     */


    /**
     * A convenience method that calls the {@link #configFile(Supplier)} method, passing it a method reference to the
     * {@link #parseDefault()} method, and returns the result.
     *
     * @@return a possibly {@linkplain Optional#isEmpty() empty} {@link Optional} housing a {@link ConfigFile
     * ConfigFile}; never {@code null}
     *
     * @exception UncheckedIOException if there was a problem parsing a configuration file
     *
     * @see #configFile(Supplier)
     */
    public static Optional<ConfigFile> configFile() {
        return configFile(ConfigFiles::parseDefault);
    }

    /**
     * A convenience method that returns a possibly {@linkplain Optional#isEmpty() empty} {@link Optional} housing a
     * {@link ConfigFile ConfigFile}, using the supplied {@link Supplier} as a source of {@link ConfigFile ConfigFile}
     * instances.
     *
     * @param cfs a {@link Supplier} of {@link ConfigFile ConfigFile} instances; must not be {@code null}
     *
     * @return a possibly {@linkplain Optional#isEmpty() empty} {@link Optional} housing a {@link ConfigFile
     * ConfigFile}; never {@code null}
     *
     * @exception UncheckedIOException if there was a problem parsing a configuration file
     *
     * @see #configFile(Supplier, Predicate)
     */
    public static Optional<ConfigFile> configFile(Supplier<? extends ConfigFile> cfs) {
        return configFile(cfs, ConfigFiles::indicatesConfigFileAbsence);
    }

    /**
     * A convenience method that returns a possibly {@linkplain Optional#isEmpty() empty} {@link Optional} housing a
     * {@link ConfigFile ConfigFile}, using the supplied {@link Supplier} as a source of {@link ConfigFile ConfigFile}
     * instances.
     *
     * @param cfs a {@link Supplier} of {@link ConfigFile ConfigFile} instances; must not be {@code null}
     *
     * @param indicatesAbsence a {@link Predicate} that tests a {@link RuntimeException} for whether it (and its
     * {@linkplain Throwable#getCause() causal chain}) merely indicates the absence of a readable {@link ConfigFile
     * ConfigFile} (versus a truly exceptional condition); must not be {@code null}
     *
     * @return a possibly {@linkplain Optional#isEmpty() empty} {@link Optional} housing a {@link ConfigFile
     * ConfigFile}; never {@code null}
     *
     * @exception UncheckedIOException if there was a problem parsing a configuration file
     *
     * @see #configFileSupplier(ConfigAccessor)
     *
     * @see #parseDefault()
     *
     * @see #parseDefault(String)
     *
     * @see #parse(String, String)
     *
     * @see #indicatesConfigFileAbsence(RuntimeException)
     */
    public static Optional<ConfigFile> configFile(Supplier<? extends ConfigFile> cfs,
                                                  Predicate<? super RuntimeException> indicatesAbsence) {
        try {
            return Optional.ofNullable(cfs.get());
        } catch (RuntimeException e) {
            if (indicatesAbsence.test(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * A convenience method, suitable mostly for using, via method reference, as a {@link Predicate
     * Predicate&lt;RuntimeException&gt;} that returns {@code true} if the supplied {@link RuntimeException} indicates,
     * logically, usually via its {@linkplain Throwable#getCause() causal chain}, a non-exceptional condition, e.g. that
     * a {@link ConfigFile ConfigFile} simply does not exist or is otherwise unavailable for reading.
     *
     * <p>Certain exceptions do not warrant being thrown from {@link ConfigFile ConfigFile} {@link Supplier}s when they
     * are simply conveying that, for example, a file does not exist. Others, of course, indicate truly exceptional
     * conditions. This method returns {@code true} when the supplied {@link RuntimeException} is one of the former, and
     * {@code false} when it is one of the latter.</p>
     *
     * <p>More specifically, this method in particular understands how to interpret exceptions that can be thrown during
     * an invocation of {@link ConfigFileReader#parseDefault()} (and similar methods), which underlies the {@link
     * #parseDefault()} method elsewhere in this class. {@link ConfigFileReader#parseDefault()} silently consumes {@link
     * FileNotFoundException}s that might otherwise relatively obviously convey the absence of a configuration file, and
     * throws generic {@link IOException}s with very specific messages instead to indicate the same failure
     * condition. This method understands when such {@link IOException}s truly indicate configuration file absence and
     * returns {@code true} in such cases.</p>
     *
     * @param e the {@link RuntimeException} to test (including its {@linkplain Throwable#getCause() causal chain})
     *
     * @return {@code true} if and only if the supplied {@link RuntimeException} indicates only that a file could not be
     * found or is (for example) not the kind of file that can be opened and read as a {@link ConfigFile ConfigFile};
     * {@code false} if the supplied {@link RuntimeException} indicates a truly exceptional condition
     *
     * @see #configFile(Supplier, Predicate)
     */
    public static boolean indicatesConfigFileAbsence(RuntimeException e) {
        Throwable t = e instanceof UncheckedIOException ? e.getCause() : null;
        while (t != null) {
            switch (t) {
            case FileNotFoundException fnf:
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, fnf.getMessage(), fnf);
                }
                return true;
            case NoSuchFileException nsf:
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, nsf.getMessage(), nsf);
                }
                return true;
            case IOException ioe:
                // Special case: com.oracle.bmc.ConfigFileReader, which does the real work, which often backs the
                // supplied Supplier, and which embeds a lot of special business logic, so we don't want to read the
                // file another way, does not throw a FileNotFoundException when it cannot find the default or fallback
                // config file (but it probably should). We have no choice but to parse the error message to see if the
                // IOException we are looking at resulted from the ConfigFileReader #parseDefault(String) operation and,
                // if so, if it is trying to indicate either a "file doesn't exist" or "file isn't a regular file"
                // condition. For the purposes of this method, in such cases we want to return an empty Optional rather
                // than throwing an exception.
                //
                // See
                // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-common/src/main/java/com/oracle/bmc/ConfigFileReader.java#L91-L94
                String message = ioe.getMessage();
                if (message != null
                    && message.startsWith("Can't load the default config from '")
                    && message.endsWith("' because it does not exist or it is not a file.")) {
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG, message, ioe);
                    }
                    return true;
                }
                break;
            default:
                break;
            }
        }
        return false;
    }

    /**
     * A convenience method that simply returns a method reference to the {@link #parseDefault()} method.
     *
     * @return a method reference to the {@link #parseDefault()} method; never {@code null}
     *
     * @see #parse(String, String)
     *
     * @see #parseDefault(String)
     *
     * @see #parseDefault()
     */
    public static Supplier<ConfigFile> configFileSupplier() {
        return ConfigFiles::parseDefault;
    }

    /**
     * A convenience method that returns a {@link Supplier} of {@link ConfigFile ConfigFile} instances based on either
     * the {@link #parse(String, String)}, {@link #parseDefault(String)}, or {@link #parseDefault()} methods.
     *
     * <p>The supplied {@link ConfigAccessor} will be {@linkplain ConfigAccessor#get(String) queried} for {@link
     * String}-typed values named {@code oci.config.path}, {@code oci.config.profile}, and, for backwards compatibility,
     * {@code oci.auth.profile}. Depending on the values, one of {@link #parseDefault()}, {@link #parseDefault(String)},
     * or {@link #parse(String, String)} will be used to back the {@link Supplier} that is returned.</p>
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @return a {@link Supplier} of {@link ConfigFile ConfigFile} instances; never {@code null}
     *
     * @exception NullPointerException if {@code ca} is {@code null}
     *
     * @see #configFileSupplier(String, String)
     *
     * @see #parse(String, String)
     *
     * @see #parseDefault(String)
     *
     * @see #parseDefault()
     */
    public static Supplier<ConfigFile> configFileSupplier(ConfigAccessor ca) {
        return
            configFileSupplier(ca.get("oci.config.path").orElse(null),
                               ca.get("oci.config.profile").or(() -> ca.get("oci.auth.profile")).orElse(null));
    }

    /**
     * A convenience method that returns a method reference to the {@link #parseDefault()} method if the supplied {@code
     * profile} is {@code null}, or a {@link Supplier} based on the {@link #parseDefault(String)} method if it is not.
     *
     * <p>This logic mirrors that used by the {@link ConfigFileReader} class' analogous methods.</p>
     *
     * @param profile the argument to be supplied to the {@link ConfigFileReader#parseDefault(String)} method; may be
     * {@code null} in which case the {@link #parseDefault()} method will be used instead
     *
     * @return a method reference to the {@link #parseDefault()} method if the supplied {@code profile} is {@code null},
     * or a {@link Supplier} based on the {@link #parseDefault(String)} method if it is not
     *
     * @see #parse(String, String)
     *
     * @see #parseDefault(String)
     *
     * @see #parseDefault()
     */
    public static Supplier<ConfigFile> configFileSupplier(String profile) {
        return profile == null ? ConfigFiles::parseDefault : () -> parseDefault(profile);
    }

    /**
     * A convenience method that returns a method reference to the {@link #parseDefault()} method if both the supplied
     * {@code configurationFilePath} and the supplied {@code profile} are {@code null}, or a {@link Supplier} based on
     * the {@link #parseDefault(String)} method if the supplied {@code configurationFilePath} is {@code null} and the
     * supplied {@code profile} is not, or a {@link Supplier} based on the {@link #parse(String, String)} method if both
     * arguments are non-{@code null}.
     *
     * <p>This logic mirrors that used by the {@link ConfigFileReader} class' analogous methods.</p>
     *
     * @param configurationFilePath the first argument to be supplied to the {@link #parse(String, String)} method; may
     * be {@code null} in which case the {@link #parseDefault()} or {@link #parseDefault(String)} method will be used
     * instead, based on the value of the supplied {@code profile} argument
     *
     * @param profile the second argument to be supplied to the {@link #parse(String, String)} method; may be {@code
     * null} in which case the {@link #parseDefault(String)} or {@link #parseDefault()} method will be used instead,
     * based on the value of the supplied {@code configurationFilePath}
     *
     * @return a {@link Supplier} of {@link ConfigFile ConfigFile} instances, never {@code null}
     *
     * @see #parse(String, String)
     *
     * @see #parseDefault(String)
     *
     * @see #parseDefault()
     */
    public static Supplier<ConfigFile> configFileSupplier(String configurationFilePath, String profile) {
        if (configurationFilePath == null) {
            return profile == null ? ConfigFiles::parseDefault : () -> parseDefault(profile);
        }
        return () -> parse(configurationFilePath, profile);
    }

    /**
     * A convenience method that invokes {@link ConfigFileReader#parseDefault()} and returns its result, converting any
     * {@link IOException}s to {@link UncheckedIOException}s.
     *
     * <p>This is a "pass-through" method that adds no additional logic to its invocation of {@link
     * ConfigFileReader#parseDefault()} beyond exception type conversion.</p>
     *
     * @return a {@link ConfigFile ConfigFile}; never {@code null}
     *
     * @exception UncheckedIOException if the {@link ConfigFileReader#parseDefault()} method throws an {@link
     * IOException}; its {@linkplain Throwable#getCause() cause} will be the {@link IOException}
     *
     * @see ConfigFileReader#parseDefault()
     */
    public static ConfigFile parseDefault() {
        try {
            return ConfigFileReader.parseDefault();
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    /**
     * A convenience method that invokes {@link ConfigFileReader#parseDefault(String)} with the supplied argument and
     * returns its result, converting any {@link IOException}s to {@link UncheckedIOException}s.
     *
     * <p>This is a "pass-through" method that adds no additional logic to its invocation of {@link
     * ConfigFileReader#parseDefault(String)} beyond exception type conversion.</p>
     *
     * @param profile the argument to be supplied to the {@link ConfigFileReader#parseDefault(String)} method
     *
     * @return a {@link ConfigFile ConfigFile}; never {@code null}
     *
     * @exception UncheckedIOException if the {@link ConfigFileReader#parseDefault(String)} method throws an {@link
     * IOException}; its {@linkplain Throwable#getCause() cause} will be the {@link IOException}
     *
     * @see ConfigFileReader#parseDefault(String)
     */
    public static ConfigFile parseDefault(String profile) {
        try {
            return ConfigFileReader.parseDefault(profile);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    /**
     * A convenience method that invokes {@link ConfigFileReader#parse(String, String)} with the supplied arguments and
     * returns its result, converting any {@link IOException}s to {@link UncheckedIOException}s.
     *
     * <p>This is a "pass-through" method that adds no additional logic to its invocation of {@link
     * ConfigFileReader#parse(String, String)} beyond exception type conversion.</p>
     *
     * @param configurationFilePath the first argument to be supplied to the {@link ConfigFileReader#parse(String,
     * String)} method
     *
     * @param profile the second argument to be supplied to the {@link ConfigFileReader#parse(String, String)} method
     *
     * @return a {@link ConfigFile ConfigFile}; never {@code null}
     *
     * @exception UncheckedIOException if the {@link ConfigFileReader#parse(String, String)} method throws an {@link
     * IOException}; its {@linkplain Throwable#getCause() cause} will be the {@link IOException}
     *
     * @see ConfigFileReader#parse(String, String)
     */
    public static ConfigFile parse(String configurationFilePath, String profile) {
        try {
            return ConfigFileReader.parse(configurationFilePath, profile);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    /**
     * A convenience method that returns {@code true} if and only if the supplied {@link ConfigFile ConfigFile} contains
     * at least the information required by all of its usage scenarios.
     *
     * <p>A {@link ConfigFile ConfigFile} is, itself, agnostic with respect to requirements. It is a simple store that
     * maps {@link String}-typed keys to {@link String}-typed values. Asking a {@link ConfigFile ConfigFile} for a key
     * that it does not know about will return {@code null}.</p>
     *
     * <p>Different OCI Java SDK usages of {@link ConfigFile ConfigFile} impose additional requirements upon any of its
     * instances' contents. The rules are fairly complicated and detailed below. Other OCI language SDKs parse the
     * underlying configuration file differently. This method follows the OCI Java SDK logic.</p>
     *
     * <ul>
     *
     * <li>{@code configFile.}{@link ConfigFile#get(String) get}{@code ("authentication_type")} is called. If the return
     * value is {@linkplain String#equals(Object) equal to} "{@code instance_principal}" (singular) or "{@code
     * resource_principal}", this method returns {@code true}.</li>
     *
     * <li>If the value for the {@code authentication_type} is any other value, including {@code null}, then additional
     * values are checked:
     *
     * <ul>
     *
     * <li>If {@code configFile.}{@link ConfigFile#get(String) get}{@code ("key_file")} returns {@code null}, {@code
     * false} is returned.</li>
     *
     * <li>If {@code configFile.}{@link ConfigFile#get(String) get}{@code ("tenancy")} returns {@code null}, {@code
     * false} is returned.</li>
     *
     * <li>If {@code configFile.}{@link ConfigFile#get(String) get}{@code ("security_token_file")} returns {@code null},
     * then {@code configFile.}{@link ConfigFile#get(String) get}{@code ("fingerprint")} and {@code configFile.}{@link
     * ConfigFile#get(String) get}{@code ("user")} must both return non-{@code null} values. If they do not, {@code
     * false} is returned.</li>
     *
     * </ul></li>
     *
     * </ul>
     *
     * <p>In all other cases, {@code true} is returned.</p>
     *
     * @param cf the {@link ConfigFile ConfigFile} in question; must not be {@code null}
     *
     * @return {@code true} if and only if the supplied {@link ConfigFile ConfigFile} contains at least the information
     * required by all of its usage scenarios
     *
     * @exception NullPointerException if {@code cf} is {@code null}
     *
     * @see ConfigFileAdpSupplier
     *
     * @see SessionTokenAdpSupplier
     */
    public static boolean containsRequiredValues(ConfigFile cf) {

        // There are various things in the OCI Java ecosystem that use ConfigFiles and require them to have certain
        // information.
        //
        // For one: apparently, a ConfigFileAuthenticationDetailsProvider can function effectively as one of three
        // possible BasicAuthenticationDetailsProviders:
        //
        // * It can be what amounts to a SimpleAuthenticationDetailsProvider (probably its original purpose)
        // * It can behave mostly like an InstancePrincipalsAuthenticationDetailsProvider and has built-in support for
        //   this use case
        // * It can behave mostly like a ResourcePrincipalAuthenticationDetailsProvider and has built-in support for
        //   this use case
        //
        // Different information is required for each behavior.
        //
        // The documented requirements do not match, one-for-one, with any of the actual enforced requirements
        // (https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#File_Entries).
        //
        // Furthermore, each language SDK (Python, Go, Java, etc.) parse the configuration file in different ways, with
        // different requirements, that do not match the documented requirements in all cases. Somewhat arbitrary
        // references: https://github.com/oracle/oci-go-sdk/blob/v65.60.0/common/configuration.go#L598-L621,
        // https://github.com/oracle/oci-python-sdk/blob/v2.123.0/src/oci/util.py#L214-L215
        //
        // Anyway, ultimately, a ConfigFileAuthenticationDetailsProvider is backed by a ConfigFile (an in-memory
        // key-value store produced from an OCI configuration file by ConfigFileReader#parse(String, String) or the
        // equivalent).
        //
        // To learn what purpose a ConfigFile is serving, you get a value for the (undocumented) "authentication_type"
        // key. That has two possible well-known values that the Java SDK knows about:
        //
        // * instance_principal (yes, singular)
        // * resource_principal
        //
        // Reference:
        // https://github.com/oracle/oci-java-sdk/blob/v3.36.0/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java#L72-L87
        //
        // (Note that other OCI language SDKs seem to omit support for resource_principal, which means if
        // authentication_type=resource_principal appears in your configuration file, if you parse it with the OCI Java
        // SDK, you will be OK, but if you parse it with the OCI Python SDK you will not. There may be other strange
        // cases here.)
        //
        // Anyhow, if the value for authentication_type is instance_principal (singular) or resource_principal, then
        // authentication is carried out by built-in AbstractAuthenticationDetailsProvider objects that behave kind of
        // like their "real" cousins.
        //
        // So, for example, the nested
        // ConfigFileAuthenticationDetailsProvider.ConfigFileInstancePrincipalAuthenticationDetailsProvider class (yes,
        // singular) uses a "real" InstancePrincipalsAuthenticationDetailsProvider (yes, plural) under the covers, but
        // also arranges for HTTP request customization (which the "real"
        // InstancePrincipalsAuthenticationDetailsProvider does not do natively). Reference:
        // https://github.com/oracle/oci-java-sdk/blob/v3.36.0/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java#L272-L340
        //
        // If its value does not exist or is anything else, then authentication is carried out by a built-in
        // AuthenticationDetailsProvider object that behaves mostly like a SimpleAuthenticationDetailsProvider. This ADP
        // enforces requirements that almost conform to the documented ones, but, notably, region is not in fact
        // required. (Note as well that other language runtimes may treat an unknown value as an error.) Reference:
        // https://github.com/oracle/oci-java-sdk/blob/v3.36.0/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java#L188-L270
        //
        // For another: you can have a "simple" ConfigFile (one with fingerprint, key_file and tenancy specified) that does
        // not have a user key. In this case, if it has a security_token_file key, then most likely, but not guaranteed,
        // the ConfigFile is serving as a source of values for a SessionTokenAuthenticationDetailsProvider (which does
        // not require user, but does require security_token_file). Conversely, if there is no security_token_file key,
        // then there will need to be a user key.

        switch (cf.get("authentication_type")) {
        case "instance_principal": // yes, singular, not plural
        case "resource_principal":
            // Technically speaking, although other OCI language runtimes impose additional requirements in this case,
            // the OCI Java SDK does not appear to do so. There are certain additional keys (e.g. delegation_token and
            // delegation_token_file
            // (https://github.com/oracle/oci-java-sdk/blob/v3.36.0/bmc-common/src/main/java/com/oracle/bmc/auth/internal/ConfigFileDelegationTokenUtils.java#L22-L23))
            // that, if they do not have values, may result in exceptions "down the line" but they are (for
            // some reason) not checked "early". This method is not in the business of reproducing all of the OCI Java
            // SDK's business logic so, if these values are not checked early, then this method doesn't check them early
            // either.
            //
            // (Note that among other things this means that the documented requirements
            // (https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#File_Entries) are not always
            // enforced by the OCI Java SDK.)
            return true;

        case null:
        default:
            // Different OCI language runtimes do different things if the authentication_type key is not set or set to a
            // value that is not instance_principal (yes, singular) or resource_principal. The OCI Java SDK accepts any
            // other value or the absence of a value. So does the Go SDK. The Python SDK does not accept a value for
            // authentication_type that is anything other than instance_principal. This method follows the Java SDK's
            // behavior.
            //
            // For individual requirements, see:
            //
            //         fingerprint: https://github.com/oracle/oci-java-sdk/blob/v3.34.1/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java#L197-L199
            //            key_file: https://github.com/oracle/oci-java-sdk/blob/v3.34.1/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java#L209-L211
            // security_token_file: https://github.com/oracle/oci-java-sdk/blob/v3.34.1/bmc-common/src/main/java/com/oracle/bmc/auth/SessionTokenAuthenticationDetailsProvider.java#L152-L155
            //             tenancy: https://github.com/oracle/oci-java-sdk/blob/v3.34.1/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java#L201-L203
            //                user: https://github.com/oracle/oci-java-sdk/blob/v3.34.1/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java#L205-L207
            return cf.get("key_file") != null
                && cf.get("tenancy") != null
                && (cf.get("security_token_file") != null || cf.get("fingerprint") != null && cf.get("user") != null);
        }
    }

}
