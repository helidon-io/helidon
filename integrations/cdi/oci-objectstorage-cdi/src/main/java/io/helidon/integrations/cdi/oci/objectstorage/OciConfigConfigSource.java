/*
 * Copyright (c) 2018, 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.oci.objectstorage;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * A {@link ConfigSource} implementation that is backed by a {@link
 * ConfigFileAuthenticationDetailsProvider}.
 */
public final class OciConfigConfigSource implements ConfigSource {

  private volatile Map<String, String> properties;

  /**
   * Creates a new {@link OciConfigConfigSource}.
   */
  public OciConfigConfigSource() {
    super();
  }

  /**
   * Returns the name of this {@link OciConfigConfigSource}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method returns the same value every time it is
   * invoked.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * <p>Overrides of this method must return the same value every time
   * they are invoked.</p>
   *
   * <p>The default return value of this method is subject to change
   * without notice.</p>
   *
   * @return the name of this {@link OciConfigConfigSource}; never
   * {@code null}
   *
   * @see ConfigSource#getName()
   */
  @Override
  public String getName() {
    return ".oci/config";
  }

  /**
   * Returns the ordinal of this {@link OciConfigConfigSource}.
   *
   * <p>This implementation returns {@code 101}, which will ensure
   * values from this {@link ConfigSource} implementation will trump
   * those from {@code /META-INF/microprofile-config.properties} but
   * none other.</p>
   *
   * @return the ordinal of this {@link OciConfigConfigSource}; {@code
   * 101} by default
   */
  @Override
  public int getOrdinal() {
    return 101; // one higher than microprofile-config.properties' ordinal
  }

  /**
   * Returns a value for the supplied {@code propertyName}, or {@code
   * null} if there is no such value.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param propertyName the name of the property for which a value
   * should be returned; may be {@code null} in which case {@code
   * null} will be returned
   *
   * @return a value for the supplied {@code propertyName}, or {@code
   * null}
   */
  @Override
  public String getValue(final String propertyName) {
    final String returnValue;
    if (propertyName == null) {
      returnValue = null;
    } else if (propertyName.equals(ConfigSource.CONFIG_ORDINAL)) {
      returnValue = String.valueOf(this.getOrdinal());
    } else {
      Map<String, String> properties = this.properties;
      if (properties == null) {
        final Config config = ConfigProviderResolver.instance()
          .getBuilder()
          .addDefaultSources()
          .build();
        final String profile = config.getOptionalValue("oci.auth.profile", String.class).orElse("DEFAULT");
        final String configFilePath = config.getOptionalValue("oci.config.path", String.class).orElse(null);
        final ConfigFileAuthenticationDetailsProvider provider;
        ConfigFileAuthenticationDetailsProvider temp = null;
        try {
          if (configFilePath == null) {
            temp = new ConfigFileAuthenticationDetailsProvider(profile);
          } else {
            temp = new ConfigFileAuthenticationDetailsProvider(configFilePath, profile);
          }
        } catch (final IOException ioException) {
          temp = null;
        } finally {
          provider = temp;
        }
        properties = createProperties(provider);
        this.properties = properties;
      }
      returnValue = properties.get(propertyName);
    }
    return returnValue;
  }

  /**
   * Returns a {@link Map} consisting of all property names and their
   * values that this {@link OciConfigConfigSource} knows about at the
   * time that this method is invoked.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>The returned {@link Map} is {@linkplain
   * Collections#unmodifiableMap(Map) immutable} and safe for
   * concurrent use by multiple threads.</p>
   *
   * <p>This method may return different {@link Map} instances when
   * invoked at different times.</p>
   *
   * <p>The returned {@link Map}, if non-{@linkplain Map#isEmpty()
   * empty}, is guaranteed to contain at least the following keys:</p>
   *
   * <ul>
   *
   * <li>oci.auth.fingerprint</li>
   *
   * <li>oci.auth.passphraseCharacters</li>
   *
   * <li>oci.auth.tenancy</li>
   *
   * <li>oci.auth.user</li>
   *
   * </ul>
   *
   * <p>The MicroProfile Config specification does not give any
   * guidance on whether the return value of an implementation of the
   * {@link ConfigSource#getProperties()} method should be immutable
   * and/or threadsafe.  This implementation returns an {@linkplain
   * Collections#unmodifiableMap(Map) immutable <code>Map</code>}.</p>
   *
   * @return a non-{@code null} {@link Map} of properties known to
   * this {@link OciConfigConfigSource}
   *
   * @exception IllegalStateException if there was a problem reading
   * the OCI config file
   *
   * @see ConfigSource#getProperties()
   */
  @Override
  public Map<String, String> getProperties() {
    final Map<String, String> properties = this.properties;
    return properties == null || properties.isEmpty() ? Collections.emptyMap() : properties;
  }

  private static Map<String, String> createProperties(final ConfigFileAuthenticationDetailsProvider provider) {
    final Map<String, String> returnValue;
    if (provider == null) {
      returnValue = Collections.emptyMap();
    } else {
      final Map<String, String> properties = new HashMap<>();
      properties.put("oci.auth.fingerprint", provider.getFingerprint());
      char[] passphrase = provider.getPassphraseCharacters();
      if (passphrase != null && passphrase.length > 0) {
        properties.put("oci.auth.passphraseCharacters", String.valueOf(passphrase));
      }
      properties.put("oci.auth.tenancy", provider.getTenantId());
      properties.put("oci.auth.user", provider.getUserId());
      returnValue = Collections.unmodifiableMap(properties);
    }
    return returnValue;
  }

}
