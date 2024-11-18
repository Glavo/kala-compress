/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.compressors.brotli;

/**
 * Utility code for the Brotli compression format.
 *
 * @ThreadSafe
 * @since 1.14
 */
public class BrotliUtils {

    /**
     * Are the classes required to support Brotli compression available?
     *
     * @return true if the classes required to support Brotli compression are available
     */
    public static boolean isBrotliCompressionAvailable() {
        try {
            Class.forName("org.brotli.dec.BrotliInputStream");
            return true;
        } catch (final NoClassDefFoundError | Exception error) { // NOSONAR
            return false;
        }
    }

    /** Private constructor to prevent instantiation of this utility class. */
    private BrotliUtils() {
    }
}
