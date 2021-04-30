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

import io.helidon.common.Base64Value;

import static io.helidon.common.crypto.CryptoCommonConstants.PREFIX;
import static io.helidon.common.crypto.CryptoCommonConstants.PREFIX_PATTERN;

/**
 * Common cipher which helps to simplify encryption and decryption of the message.
 */
public interface CommonCipher {

    /**
     * Encrypt message.
     *
     * @param message message
     * @return encrypted message
     */
    Base64Value encrypt(Base64Value message);

    /**
     * Decrypt encrypted message.
     *
     * @param encrypted encrypted message
     * @return decrypted message
     */
    Base64Value decrypt(Base64Value encrypted);

    /**
     * Encrypt message to the String format.
     * <br>
     * Template format: <code>helidon:(formatVersion):encryptedDataInBase64</code><br>
     * Example: <code>helidon:2:encryptedDataInBase64</code>
     *
     * @param message message
     * @return cipher text
     */
    default String encryptToString(Base64Value message) {
        return PREFIX + encrypt(message).toBase64();
    }

    /**
     * Decrypt cipherText provided by {@link #encryptToString(Base64Value)}.
     * <br>
     * Required format: <code>helidon:(formatVersion):encryptedDataInBase64</code><br>
     * Example: <code>helidon:2:encryptedDataInBase64</code>
     *
     * @param cipherText cipher text
     * @return decrypted message
     */
    default Base64Value decryptFromString(String cipherText) {
        Matcher matcher = PREFIX_PATTERN.matcher(cipherText);
        if (matcher.matches()) {
            return decrypt(Base64Value.createFromEncoded(matcher.group(2)));
        } else {
            throw new CryptoException("String does not contain Helidon prefix: " + cipherText);
        }
    }

}
