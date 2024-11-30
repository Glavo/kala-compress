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
package kala.compress.compressors.lzma;

import java.util.HashMap;
import java.util.Map;

import kala.compress.compressors.FileNameUtil;

/**
 * Utility code for the LZMA compression format.
 *
 * @ThreadSafe
 * @since 1.10
 */
public class LZMAUtils {

    private static final FileNameUtil fileNameUtil;

    /**
     * LZMA Header Magic Bytes begin a LZMA file.
     */
    private static final byte[] HEADER_MAGIC = { (byte) 0x5D, 0, 0 };

    static {
        final Map<String, String> uncompressSuffix = new HashMap<>();
        uncompressSuffix.put(".lzma", "");
        uncompressSuffix.put("-lzma", "");
        fileNameUtil = new FileNameUtil(uncompressSuffix, ".lzma");
    }

    /**
     * Maps the given file name to the name that the file should have after compression with LZMA.
     *
     * @param fileName name of a file
     * @return name of the corresponding compressed file
     * @since 1.25.0
     */
    public static String getCompressedFileName(final String fileName) {
        return fileNameUtil.getCompressedFileName(fileName);
    }

    /**
     * Maps the given name of a LZMA-compressed file to the name that the file should have after uncompression. Any file names with the generic ".lzma" suffix
     * (or any other generic LZMA suffix) is mapped to a name without that suffix. If no LZMA suffix is detected, then the file name is returned unmapped.
     *
     * @param fileName name of a file
     * @return name of the corresponding uncompressed file
     * @since 1.25.0
     */
    public static String getUncompressedFileName(final String fileName) {
        return fileNameUtil.getUncompressedFileName(fileName);
    }

    /**
     * Detects common LZMA suffixes in the given file name.
     *
     * @param fileName name of a file
     * @return {@code true} if the file name has a common LZMA suffix, {@code false} otherwise
     * @since 1.25.0
     */
    public static boolean isCompressedFileName(final String fileName) {
        return fileNameUtil.isCompressedFileName(fileName);
    }

    /**
     * Are the classes required to support LZMA compression available?
     *
     * @return true if the classes required to support LZMA compression are available
     */
    public static boolean isLZMACompressionAvailable() {
        try {
            LZMACompressorInputStream.matches(null, 0);
            return true;
        } catch (final NoClassDefFoundError error) { // NOSONAR
            return false;
        }
    }

    /**
     * Checks if the signature matches what is expected for a .lzma file.
     *
     * @param signature the bytes to check
     * @param length    the number of bytes to check
     * @return true if signature matches the .lzma magic bytes, false otherwise
     */
    public static boolean matches(final byte[] signature, final int length) {
        if (length < HEADER_MAGIC.length) {
            return false;
        }

        for (int i = 0; i < HEADER_MAGIC.length; ++i) {
            if (signature[i] != HEADER_MAGIC[i]) {
                return false;
            }
        }

        return true;
    }

    /** Private constructor to prevent instantiation of this utility class. */
    private LZMAUtils() {
    }
}
