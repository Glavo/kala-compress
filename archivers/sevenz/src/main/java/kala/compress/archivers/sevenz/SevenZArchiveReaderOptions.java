/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package kala.compress.archivers.sevenz;

/**
 * Collects options for reading 7z archives.
 *
 * @since 1.19
 * @Immutable
 */
public class SevenZArchiveReaderOptions {
    private static final int DEFAUL_MEMORY_LIMIT_IN_KB = Integer.MAX_VALUE;
    private static final boolean DEFAULT_USE_DEFAULTNAME_FOR_UNNAMED_ENTRIES= false;
    private static final boolean DEFAULT_TRY_TO_RECOVER_BROKEN_ARCHIVES = false;

    private final int maxMemoryLimitInKb;
    private final boolean useDefaultNameForUnnamedEntries;
    private final boolean tryToRecoverBrokenArchives;

    private SevenZArchiveReaderOptions(final int maxMemoryLimitInKb, final boolean useDefaultNameForUnnamedEntries,
                                       final boolean tryToRecoverBrokenArchives) {
        this.maxMemoryLimitInKb = maxMemoryLimitInKb;
        this.useDefaultNameForUnnamedEntries = useDefaultNameForUnnamedEntries;
        this.tryToRecoverBrokenArchives = tryToRecoverBrokenArchives;
    }

    /**
     * The default options.
     *
     * <ul>
     *   <li>no memory limit</li>
     *   <li>don't modify the name of unnamed entries</li>
     * </ul>
     */
    public static final SevenZArchiveReaderOptions DEFAULT = new SevenZArchiveReaderOptions(DEFAUL_MEMORY_LIMIT_IN_KB,
        DEFAULT_USE_DEFAULTNAME_FOR_UNNAMED_ENTRIES,
        DEFAULT_TRY_TO_RECOVER_BROKEN_ARCHIVES);

    /**
     * Obtains a builder for SevenZArchiveReaderOptions.
     * @return a builder for SevenZArchiveReaderOptions.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the maximum amount of memory to use for parsing the
     * archive and during extraction.
     *
     * <p>Not all codecs will honor this setting. Currently only lzma
     * and lzma2 are supported.</p>
     *
     * @return the maximum amount of memory to use for extraction
     */
    public int getMaxMemoryLimitInKb() {
        return maxMemoryLimitInKb;
    }

    /**
     * Gets whether entries without a name should get their names set
     * to the archive's default file name.
     * @return whether entries without a name should get their names
     * set to the archive's default file name
     */
    public boolean getUseDefaultNameForUnnamedEntries() {
        return useDefaultNameForUnnamedEntries;
    }

    /**
     * Whether {@link SevenZArchiveReader} shall try to recover from a certain type of broken archive.
     * @return whether SevenZArchiveReader shall try to recover from a certain type of broken archive.
     * @since 1.21
     */
    public boolean getTryToRecoverBrokenArchives() {
        return tryToRecoverBrokenArchives;
    }

    /**
     * Mutable builder for the immutable {@link SevenZArchiveReaderOptions}.
     *
     * @since 1.19
     */
    public static class Builder {
        private int maxMemoryLimitInKb = DEFAUL_MEMORY_LIMIT_IN_KB;
        private boolean useDefaultNameForUnnamedEntries = DEFAULT_USE_DEFAULTNAME_FOR_UNNAMED_ENTRIES;
        private boolean tryToRecoverBrokenArchives = DEFAULT_TRY_TO_RECOVER_BROKEN_ARCHIVES;

        /**
         * Sets the maximum amount of memory to use for parsing the
         * archive and during extraction.
         *
         * <p>Not all codecs will honor this setting. Currently only lzma
         * and lzma2 are supported.</p>
         *
         * @param maxMemoryLimitInKb limit of the maximum amount of memory to use
         * @return the reconfigured builder
         */
        public Builder withMaxMemoryLimitInKb(final int maxMemoryLimitInKb) {
            this.maxMemoryLimitInKb = maxMemoryLimitInKb;
            return this;
        }

        /**
         * Sets whether entries without a name should get their names
         * set to the archive's default file name.
         *
         * @param useDefaultNameForUnnamedEntries if true the name of
         * unnamed entries will be set to the archive's default name
         * @return the reconfigured builder
         */
        public Builder withUseDefaultNameForUnnamedEntries(final boolean useDefaultNameForUnnamedEntries) {
            this.useDefaultNameForUnnamedEntries = useDefaultNameForUnnamedEntries;
            return this;
        }

        /**
         * Sets whether {@link SevenZArchiveReader} will try to revover broken archives where the CRC of the file's metadata is
         * 0.
         *
         * <p>This special kind of broken archive is encountered when mutli volume archives are closed prematurely. If
         * you enable this option SevenZArchiveReader will trust data that looks as if it could contain metadata of an archive
         * and allocate big amounts of memory. It is strongly recommended to not enable this option without setting
         * {@link #withMaxMemoryLimitInKb} at the same time.
         *
         * @param tryToRecoverBrokenArchives if true SevenZArchiveReader will try to recover archives that are broken in the
         * specific way
         * @return the reconfigured builder
         * @since 1.21
         */
        public Builder withTryToRecoverBrokenArchives(final boolean tryToRecoverBrokenArchives) {
            this.tryToRecoverBrokenArchives = tryToRecoverBrokenArchives;
            return this;
        }

        /**
         * Create the {@link SevenZArchiveReaderOptions}.
         *
         * @return configured {@link SevenZArchiveReaderOptions}.
         */
        public SevenZArchiveReaderOptions build() {
            return new SevenZArchiveReaderOptions(maxMemoryLimitInKb, useDefaultNameForUnnamedEntries,
                tryToRecoverBrokenArchives);
        }
    }
}
