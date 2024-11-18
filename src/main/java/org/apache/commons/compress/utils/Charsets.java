/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.compress.utils;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

// Copied from org.apache.commons.io.Charsets
public final class Charsets {
    /**
     * Returns the given Charset or the default Charset if the given Charset is null.
     *
     * @param charset
     *            A charset or null.
     * @return the given Charset or the default Charset if the given Charset is null
     */
    public static Charset toCharset(final Charset charset) {
        return charset == null ? Charset.defaultCharset() : charset;
    }

    /**
     * Returns the given charset if non-null, otherwise return defaultCharset.
     *
     * @param charset The charset to test, may be null.
     * @param defaultCharset The charset to return if charset is null, may be null.
     * @return a Charset.
     */
    public static Charset toCharset(final Charset charset, final Charset defaultCharset) {
        return charset == null ? defaultCharset : charset;
    }

    /**
     * Returns a Charset for the named charset. If the name is null, return the default Charset.
     *
     * @param charsetName The name of the requested charset may be null.
     * @return a Charset for the named charset.
     * @throws UnsupportedCharsetException If the named charset is unavailable (unchecked exception).
     */
    public static Charset toCharset(final String charsetName) throws UnsupportedCharsetException {
        return toCharset(charsetName, Charset.defaultCharset());
    }

    /**
     * Returns a Charset for the named charset. If the name is null, return the given default Charset.
     *
     * @param charsetName The name of the requested charset may be null.
     * @param defaultCharset The name charset to return if charsetName is null, may be null.
     * @return a Charset for the named charset.
     * @throws UnsupportedCharsetException If the named charset is unavailable (unchecked exception).
     */
    public static Charset toCharset(final String charsetName, final Charset defaultCharset) throws UnsupportedCharsetException {
        return charsetName == null ? defaultCharset : Charset.forName(charsetName);
    }

    private Charsets() {
    }
}
