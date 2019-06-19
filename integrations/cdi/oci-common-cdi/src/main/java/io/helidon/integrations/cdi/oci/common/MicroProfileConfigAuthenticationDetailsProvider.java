/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.oci.common;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.oracle.bmc.auth.CustomerAuthenticationDetailsProvider;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * A {@link CustomerAuthenticationDetailsProvider} backed by a
 * MicroProfile Config {@link Config} implementation.
 *
 * @see OciConfigConfigSource
 */
@ApplicationScoped
public class MicroProfileConfigAuthenticationDetailsProvider extends CustomerAuthenticationDetailsProvider implements Serializable {

    private static final long serialVersionUID = 1L;

    private transient Config config;

    /**
     * Creates a new {@link MicroProfileConfigAuthenticationDetailsProvider}.
     *
     * @param config the {@link Config} that will ultimately be
     * consulted for all configuration information; must not be {@code
     * null}
     */
    @Inject
    public MicroProfileConfigAuthenticationDetailsProvider(final Config config) {
        super();
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Invokes the {@link #getPassphraseCharacters()} method, and
     * returns a {@linkplain String#valueOf(char[])
     * <code>String</code> representation} of its return value.
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>Overrides of this method may return {@code null}.</p>
     *
     * @return a {@linkplain String#valueOf(char[])
     * <code>String</code> representation} of the result of invoking
     * the {@link #getPassphraseCharacters()} method, or {@code null}
     *
     * @see #getPassphraseCharacters()
     *
     * @deprecated Please use the {@link #getPassphraseCharacters()}
     * method instead.
     */
    @Deprecated
    @Override
    public String getPassPhrase() {
        final char[] passphraseCharacters = this.getPassphraseCharacters();
        final String returnValue;
        if (passphraseCharacters == null) {
            returnValue = null;
        } else {
            returnValue = String.valueOf(passphraseCharacters);
        }
        return returnValue;
    }

    /**
     * Returns the value of the {@code oci.auth.passphraseCharacters}
     * configuration property as a {@code char} array, or {@code null}
     * if no such configuration property value exists.
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>Overrides of this method may return {@code null}.</p>
     *
     * @return the value of the {@code oci.auth.passphraseCharacters}
     * configuration property as a {@code char} array, or {@code null}
     *
     * @see
     * com.oracle.bmc.auth.BasicAuthenticationDetailsProvider#getPassphraseCharacters()
     */
    @Override
    public char[] getPassphraseCharacters() {
        final String passphraseString = this.config.getOptionalValue("oci.auth.passphraseCharacters", String.class).orElse(null);
        final char[] returnValue;
        if (passphraseString == null) {
            returnValue = null;
        } else {
            returnValue = passphraseString.toCharArray();
        }
        return returnValue;
    }

    /**
     * Returns an {@link InputStream} containing a PEM-formatted
     * private key.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>Overrides of this method must not return {@code null}.</p>
     *
     * <p>This method performs the following steps in order:</p>
     *
     * <ol>
     *
     * <li>{@link Config#getOptionalValue(String, Class)
     * config.getOptionalValue("oci.auth.privateKey", String.class)}
     * is called.  If this configuration property has a value, then
     * {@linkplain String#getBytes(Charset) its bytes} are returned
     * wrapped in a {@link ByteArrayInputStream} wrapped in a {@link
     * BufferedInputStream}.</li>
     *
     * <li>{@link Config#getOptionalValue(String, Class)
     * config.getOptionalValue("oci.auth.keyFile", String.class)} is
     * called.  If this configuration property does not have a value,
     * then a {@link String} resulting from the concatenation of the
     * {@code user.home} {@linkplain System#getProperty(String,
     * String) system property} and {@code .oci/oci_api_key.pem} is
     * used instead.  The resulting {@link String} in either case is
     * treated as a {@link Paths Path}, and an attempt is made to return the
     * corresponding file's bytes wrapped in an {@link InputStream}
     * wrapped in a {@link BufferedInputStream}.</li>
     *
     * <li>If any {@link IOException} occurs at any point it is
     * wrapped in a {@link NoSuchElementException} which is
     * subsequently rethrown.</li>
     *
     * </ol>
     *
     * @return a non-{@code null} {@link InputStream}
     *
     * @exception NoSuchElementException if there was an IO-related problem;
     * see {@linkplain Throwable#getCause() its cause} for details
     *
     * @see
     * com.oracle.bmc.auth.BasicAuthenticationDetailsProvider#getPrivateKey()
     */
    @Override
    public InputStream getPrivateKey() {
        final String privateKey = this.config.getOptionalValue("oci.auth.privateKey", String.class).orElse(null);
        if (privateKey == null || privateKey.trim().isEmpty()) {
            final String pemFormattedPrivateKeyFilePath =
                this.config.getOptionalValue("oci.auth.keyFile", String.class)
                .orElse(Paths.get(System.getProperty("user.home"),
                                  ".oci/oci_api_key.pem").toString());
            assert pemFormattedPrivateKeyFilePath != null;
            try {
                return new BufferedInputStream(Files.newInputStream(Paths.get(pemFormattedPrivateKeyFilePath)));
            } catch (final IOException ioException) {
                throw (NoSuchElementException) new NoSuchElementException(ioException.getMessage()).initCause(ioException);
            }
        } else {
            return new BufferedInputStream(new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Returns the result of invoking {@link Config#getValue(String,
     * Class) config.getValue("oci.auth.user", String.class)}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>Overrides of this method must not return {@code null}.</p>
     *
     * @return the non-{@code null} value of the {@code oci.auth.user}
     * configuration property
     *
     * @exception NoSuchElementException if no value for the {@code
     * oci.auth.user} configuration property exists
     *
     * @see
     * com.oracle.bmc.auth.AuthenticationDetailsProvider#getUserId()
     *
     * @see Config#getValue(String, Class)
     */
    @Override
    public String getUserId() {
        return this.config.getValue("oci.auth.user", String.class);
    }

    /**
     * Returns the result of invoking {@link Config#getValue(String,
     * Class) config.getValue("oci.auth.tenancy", String.class)}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>Overrides of this method must not return {@code null}.</p>
     *
     * @return the non-{@code null} value of the {@code oci.auth.tenancy}
     * configuration property
     *
     * @exception NoSuchElementException if no value for the {@code
     * oci.auth.tenancy} configuration property exists
     *
     * @see
     * com.oracle.bmc.auth.AuthenticationDetailsProvider#getTenantId()
     *
     * @see Config#getValue(String, Class)
     */
    @Override
    public String getTenantId() {
        return this.config.getValue("oci.auth.tenancy", String.class);
    }

    /**
     * Returns the result of invoking {@link Config#getValue(String,
     * Class) config.getValue("oci.auth.fingerprint", String.class)}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>Overrides of this method must not return {@code null}.</p>
     *
     * @return the non-{@code null} value of the {@code oci.auth.fingerprint}
     * configuration property
     *
     * @exception NoSuchElementException if no value for the {@code
     * oci.auth.fingerprint} configuration property exists
     *
     * @see
     * com.oracle.bmc.auth.AuthenticationDetailsProvider#getFingerprint()
     *
     * @see Config#getValue(String, Class)
     */
    @Override
    public String getFingerprint() {
        return this.config.getValue("oci.auth.fingerprint", String.class);
    }

    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        if (stream != null) {
            stream.defaultReadObject();
        }
        this.config = ConfigProvider.getConfig();
    }

}
