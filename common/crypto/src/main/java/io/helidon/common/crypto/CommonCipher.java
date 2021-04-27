/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.crypto;

import java.util.regex.Matcher;

import io.helidon.integrations.common.rest.Base64Value;

import static io.helidon.common.crypto.CryptoCommonConstants.PREFIX;
import static io.helidon.common.crypto.CryptoCommonConstants.PREFIX_PATTERN;

/**
 * Common cipher which helps to simplify encryption and decryption of the message.
 */
public interface CommonCipher {

    /**
     * Encrypt plain message.
     *
     * @param plain plain message
     * @return encrypted message
     */
    Base64Value encrypt(Base64Value plain);

    /**
     * Decrypt encrypted message.
     *
     * @param encrypted encrypted message
     * @return decrypted message
     */
    Base64Value decrypt(Base64Value encrypted);

    /**
     * Encrypt plain message to the String format.
     * <p>
     * Template format: <code>helidon:(formatVersion):encryptedDataInBase64</code><p>
     * Example: <code>helidon:2:encryptedDataInBase64</code>
     *
     * @param plain plain message
     * @return encrypted message in the String format
     */
    default String encryptToString(Base64Value plain) {
        return PREFIX + encrypt(plain).toBase64();
    }

    /**
     * Decrypt encrypted message in String format.
     * <p>
     * Required format: <code>helidon:(formatVersion):encryptedDataInBase64</code><p>
     * Example: <code>helidon:2:encryptedDataInBase64</code>
     *
     * @param encrypted encrypted message in the String format
     * @return decrypted message
     */
    default Base64Value decryptFromString(String encrypted) {
        Matcher matcher = PREFIX_PATTERN.matcher(encrypted);
        if (matcher.matches()) {
            return decrypt(Base64Value.createFromEncoded(matcher.group(2)));
        } else {
            throw new CryptoException("String does not contain Helidon prefix: " + encrypted);
        }
    }

}
