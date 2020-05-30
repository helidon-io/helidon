/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import com.oracle.bmc.auth.CustomerAuthenticationDetailsProvider;
import org.eclipse.microprofile.config.Config;

final class MicroProfileConfigAuthenticationDetailsProvider extends CustomerAuthenticationDetailsProvider {

  private final Config config;

  MicroProfileConfigAuthenticationDetailsProvider(final Config config) {
    super();
    this.config = Objects.requireNonNull(config);
  }

  /**
   * This method is implementing an interface of a third party. Until
   * that interface removes this method, we need to keep it in Helidon.
   *
   * @return pass phrase
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

  @Override
  public InputStream getPrivateKey() {
    final String privateKey = this.config.getOptionalValue("oci.auth.privateKey", String.class)
      .orElse(null);
    if (privateKey == null || privateKey.trim().isEmpty()) {
      final String pemFormattedPrivateKeyFilePath =
        this.config.getOptionalValue("oci.auth.keyFile", String.class)
        .orElse(Paths.get(System.getProperty("user.home"), ".oci/oci_api_key.pem").toString());
      assert pemFormattedPrivateKeyFilePath != null;
      try {
        return new BufferedInputStream(Files.newInputStream(Paths.get(pemFormattedPrivateKeyFilePath)));
      } catch (final IOException ioException) {
        throw new RuntimeException(ioException.getMessage(), ioException);
      }
    } else {
      return new BufferedInputStream(new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)));
    }
  }

  @Override
  public String getUserId() {
    return this.config.getValue("oci.auth.user", String.class);
  }

  @Override
  public String getTenantId() {
    return this.config.getValue("oci.auth.tenancy", String.class);
  }

  @Override
  public String getFingerprint() {
    return this.config.getValue("oci.auth.fingerprint", String.class);
  }

}
