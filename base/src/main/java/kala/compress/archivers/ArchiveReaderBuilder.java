/*
 * Copyright 2024 Glavo
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
package kala.compress.archivers;

import kala.compress.utils.Charsets;
import kala.compress.utils.SeekableInMemoryByteChannel;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @author Glavo
 * @since 1.27.1-0
 */
public abstract class ArchiveReaderBuilder<R, B extends ArchiveReaderBuilder<R, B>> {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    protected static final OpenOption[] DEFAULT_OPEN_OPTIONS = {StandardOpenOption.READ};

    protected OpenOption[] openOptions = DEFAULT_OPEN_OPTIONS;
    protected Charset charset = DEFAULT_CHARSET;

    protected String originDescription;
    protected SeekableByteChannel seekableByteChannel;
    protected Path path;

    protected Path checkPath() {
        if (path == null) {
            throw new IllegalStateException("path is null");
        }

        return path;
    }

    /**
     * Sets the Charset.
     *
     * @param charset the Charset, null resets to the default.
     * @return {@code this} instance.
     */
    public final B setCharset(final Charset charset) {
        this.charset = charset != null ? charset : DEFAULT_CHARSET;
        return asThis();
    }

    /**
     * Sets the Charset.
     *
     * @param charset the Charset, null resets to the default.
     * @return {@code this} instance.
     */
    public final B setCharset(final String charset) {
        return setCharset(Charsets.toCharset(charset, DEFAULT_CHARSET));
    }

    /**
     * Sets the OpenOption[].
     *
     * @param openOptions the OpenOption[] name, null resets to the default.
     * @return {@code this} instance.
     */
    public final B setOpenOptions(final OpenOption... openOptions) {
        this.openOptions = openOptions != null ? openOptions : DEFAULT_OPEN_OPTIONS;
        return asThis();
    }

    /**
     * Set the origin description.
     *
     * @param description the origin description, null resets to the default..
     * @return {@code this} instance.
     */
    public final B setOriginDescription(final String description) {
        this.originDescription = description;
        return asThis();
    }

    /**
     * Sets a new origin.
     *
     * @param origin the new origin.
     * @return {@code this} instance.
     */
    public final B setSeekableByteChannel(final SeekableByteChannel origin) {
        this.seekableByteChannel = origin;
        return asThis();
    }

    /**
     * Sets a new origin.
     *
     * @param origin the new origin.
     * @return {@code this} instance.
     */
    public final B setByteArray(final byte[] origin) {
        return setSeekableByteChannel(new SeekableInMemoryByteChannel(origin));
    }

    /**
     * Sets a new origin.
     *
     * @param origin the new origin.
     * @return {@code this} instance.
     */
    public final B setFile(final File origin) {
        this.path = origin.toPath();
        return asThis();
    }

    /**
     * Sets a new origin.
     *
     * @param origin the new origin.
     * @return {@code this} instance.
     */
    public final B setFile(final String origin) {
        return setFile(new File(origin));
    }

    /**
     * Sets a new origin.
     *
     * @param origin the new origin.
     * @return {@code this} instance.
     */
    public final B setPath(final Path origin) {
        this.path = origin;
        return asThis();
    }

    /**
     * Sets a new origin.
     *
     * @param origin the new origin.
     * @return {@code this} instance.
     */
    public final B setPath(final String origin) {
        return setPath(Paths.get(origin));
    }

    /**
     * Sets a new origin.
     *
     * @param origin the new origin.
     * @return {@code this} instance.
     */
    public final B setURI(final URI origin) {
        return setPath(Paths.get(origin));
    }

    /**
     * Gets the archive reader.
     *
     * @return a result.
     * @throws IOException if an I/O error occurs.
     */
    public abstract R get() throws IOException;

    /**
     * Returns this instance typed as the proper subclass type.
     *
     * @return this instance typed as the proper subclass type.
     */
    @SuppressWarnings("unchecked")
    protected final B asThis() {
        return (B) this;
    }
}
