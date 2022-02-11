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
package org.apache.commons.compress.archivers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;

import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.compress.utils.ServiceLoaderIterator;

/**
 * Factory to create Archive[In|Out]putStreams from names or the first bytes of
 * the InputStream. In order to add other implementations, you should extend
 * ArchiveStreamFactory and override the appropriate methods (and call their
 * implementation from super of course).
 *
 * Compressing a ZIP-File:
 *
 * <pre>
 * final OutputStream out = Files.newOutputStream(output.toPath());
 * ArchiveOutputStream os = new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.ZIP, out);
 *
 * os.putArchiveEntry(new ZipArchiveEntry("testdata/test1.xml"));
 * IOUtils.copy(Files.newInputStream(file1.toPath()), os);
 * os.closeArchiveEntry();
 *
 * os.putArchiveEntry(new ZipArchiveEntry("testdata/test2.xml"));
 * IOUtils.copy(Files.newInputStream(file2.toPath()), os);
 * os.closeArchiveEntry();
 * os.close();
 * </pre>
 *
 * Decompressing a ZIP-File:
 *
 * <pre>
 * final InputStream is = Files.newInputStream(input.toPath());
 * ArchiveInputStream in = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP, is);
 * ZipArchiveEntry entry = (ZipArchiveEntry)in.getNextEntry();
 * OutputStream out = Files.newOutputStream(dir.toPath().resolve(entry.getName()));
 * IOUtils.copy(in, out);
 * out.close();
 * in.close();
 * </pre>
 * @Immutable provided that the deprecated method setEntryEncoding is not used.
 * @ThreadSafe even if the deprecated method setEntryEncoding is used
 */
public class ArchiveStreamFactory implements ArchiveStreamProvider {

    private static final int TAR_HEADER_SIZE = 512;

    private static final int DUMP_SIGNATURE_SIZE = 32;

    private static final int SIGNATURE_SIZE = 12;

    /**
     * The singleton instance using the platform default encoding.
     * @since 1.21
     */
    public static final ArchiveStreamFactory DEFAULT = new ArchiveStreamFactory();

    /**
     * Constant (value {@value}) used to identify the AR archive format.
     * @since 1.1
     */
    public static final String AR = "ar";

    /**
     * Constant (value {@value}) used to identify the ARJ archive format.
     * Not supported as an output stream type.
     * @since 1.6
     */
    public static final String ARJ = "arj";

    /**
     * Constant (value {@value}) used to identify the CPIO archive format.
     * @since 1.1
     */
    public static final String CPIO = "cpio";

    /**
     * Constant (value {@value}) used to identify the Unix DUMP archive format.
     * Not supported as an output stream type.
     * @since 1.3
     */
    public static final String DUMP = "dump";

    /**
     * Constant (value {@value}) used to identify the JAR archive format.
     * @since 1.1
     */
    public static final String JAR = "jar";

    /**
     * Constant used to identify the TAR archive format.
     * @since 1.1
     */
    public static final String TAR = "tar";

    /**
     * Constant (value {@value}) used to identify the ZIP archive format.
     * @since 1.1
     */
    public static final String ZIP = "zip";

    /**
     * Constant (value {@value}) used to identify the 7z archive format.
     * @since 1.8
     */
    public static final String SEVEN_Z = "7z";

    private static final BuiltinArchiver[] BUILTIN_ARCHIVERS;
    private static final BuiltinArchiver DUMP_ARCHIVER;
    private static final BuiltinArchiver TAR_ARCHIVER;

    static {
        ArrayList<BuiltinArchiver> archivers = new ArrayList<>();
        BuiltinArchiver dumpArchiver = null;
        BuiltinArchiver tarArchiver = null;

        ServiceLoaderIterator<BuiltinArchiver> it = new ServiceLoaderIterator<>(BuiltinArchiver.class, BuiltinArchiver.class.getClassLoader());
        while (it.hasNext()) {
            BuiltinArchiver archiver = it.next();
            archivers.add(archiver);

            if (DUMP.equals(archiver.getName())) {
                dumpArchiver = archiver;
            } else if (TAR.equals(archiver.getName())) {
                tarArchiver = archiver;
            }
        }

        BUILTIN_ARCHIVERS = archivers.toArray(new BuiltinArchiver[0]);
        DUMP_ARCHIVER = dumpArchiver;
        TAR_ARCHIVER = tarArchiver;
    }

    private final Charset entryCharset;

    private SortedMap<String, ArchiveStreamProvider> archiveInputStreamProviders;

    private SortedMap<String, ArchiveStreamProvider> archiveOutputStreamProviders;

    static void putAll(final Set<String> names, final ArchiveStreamProvider provider,
            final TreeMap<String, ArchiveStreamProvider> map) {
        for (final String name : names) {
            map.put(toKey(name), provider);
        }
    }

    private static String toKey(final String name) {
        return name.toUpperCase(Locale.ROOT);
    }

    /**
     * Constructs a new sorted map from input stream provider names to provider
     * objects.
     *
     * <p>
     * The map returned by this method will have one entry for each provider for
     * which support is available in the current Java virtual machine. If two or
     * more supported provider have the same name then the resulting map will
     * contain just one of them; which one it will contain is not specified.
     * </p>
     *
     * <p>
     * The invocation of this method, and the subsequent use of the resulting
     * map, may cause time-consuming disk or network I/O operations to occur.
     * This method is provided for applications that need to enumerate all of
     * the available providers, for example to allow user provider selection.
     * </p>
     *
     * <p>
     * This method may return different results at different times if new
     * providers are dynamically made available to the current Java virtual
     * machine.
     * </p>
     *
     * @return An immutable, map from names to provider objects
     * @since 1.13
     */
    public static SortedMap<String, ArchiveStreamProvider> findAvailableArchiveInputStreamProviders() {
        final TreeMap<String, ArchiveStreamProvider> map = new TreeMap<>();
        putAll(DEFAULT.getInputStreamArchiveNames(), DEFAULT, map);
        new ServiceLoaderIterator<>(ArchiveStreamProvider.class)
                .forEachRemaining(provider -> putAll(provider.getInputStreamArchiveNames(), provider, map));
        return map;
    }

    /**
     * Constructs a new sorted map from output stream provider names to provider
     * objects.
     *
     * <p>
     * The map returned by this method will have one entry for each provider for
     * which support is available in the current Java virtual machine. If two or
     * more supported provider have the same name then the resulting map will
     * contain just one of them; which one it will contain is not specified.
     * </p>
     *
     * <p>
     * The invocation of this method, and the subsequent use of the resulting
     * map, may cause time-consuming disk or network I/O operations to occur.
     * This method is provided for applications that need to enumerate all of
     * the available providers, for example to allow user provider selection.
     * </p>
     *
     * <p>
     * This method may return different results at different times if new
     * providers are dynamically made available to the current Java virtual
     * machine.
     * </p>
     *
     * @return An immutable, map from names to provider objects
     * @since 1.13
     */
    public static SortedMap<String, ArchiveStreamProvider> findAvailableArchiveOutputStreamProviders() {
        final TreeMap<String, ArchiveStreamProvider> map = new TreeMap<>();
        putAll(DEFAULT.getOutputStreamArchiveNames(), DEFAULT, map);
        new ServiceLoaderIterator<>(ArchiveStreamProvider.class)
                .forEachRemaining(provider -> putAll(provider.getOutputStreamArchiveNames(), provider, map));
        return map;
    }

    /**
     * Create an instance using the platform default encoding.
     */
    public ArchiveStreamFactory() {
        this((Charset) null);
    }

    /**
     * Create an instance using the specified encoding.
     *
     * @param encoding the encoding to be used.
     *
     * @since 1.10
     */
    public ArchiveStreamFactory(final String encoding) {
        this.entryCharset = encoding == null ? null : Charsets.toCharset(encoding);
    }

    /**
     * Create an instance using the specified charset.
     *
     * @param charset the charset to be used.
     *
     * @since 1.21.0.1
     */
    public ArchiveStreamFactory(final Charset charset) {
        this.entryCharset = charset;
    }

    /**
     * Returns the encoding to use for arj, jar, zip, dump, cpio and tar
     * files, or null for the archiver default.
     *
     * @return entry encoding, or null for the archiver default
     * @since 1.5
     */
    public String getEntryEncoding() {
        return entryCharset == null ? null : entryCharset.name();
    }

    /**
     * Returns the encoding to use for arj, jar, zip, dump, cpio and tar
     * files, or null for the archiver default.
     *
     * @return the entry charset, or null for the archiver default
     * @since 1.21.0.1
     */
    public Charset getEntryCharset() {
        return entryCharset;
    }

    /**
     * Creates an archive input stream from an archiver name and an input stream.
     *
     * @param archiverName the archive name,
     * i.e. {@value #AR}, {@value #ARJ}, {@value #ZIP}, {@value #TAR}, {@value #JAR}, {@value #CPIO}, {@value #DUMP} or {@value #SEVEN_Z}
     * @param in the input stream
     * @return the archive input stream
     * @throws ArchiveException if the archiver name is not known
     * @throws StreamingNotSupportedException if the format cannot be
     * read from a stream
     * @throws IllegalArgumentException if the archiver name or stream is null
     */
    public ArchiveInputStream createArchiveInputStream(final String archiverName, final InputStream in)
            throws ArchiveException {
        return createArchiveInputStream(archiverName, in, entryCharset);
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(final String archiverName, final InputStream in,
            final Charset actualCharset) throws ArchiveException {

        if (archiverName == null) {
            throw new IllegalArgumentException("Archivername must not be null.");
        }

        if (in == null) {
            throw new IllegalArgumentException("InputStream must not be null.");
        }

        for (BuiltinArchiver archiver : BUILTIN_ARCHIVERS) {
            if (archiver.getName().equalsIgnoreCase(archiverName)) {
                return actualCharset != null
                        ? archiver.createArchiveInputStream(in, actualCharset)
                        : archiver.createArchiveInputStream(in);
            }
        }

        final ArchiveStreamProvider archiveStreamProvider = getArchiveInputStreamProviders().get(toKey(archiverName));
        if (archiveStreamProvider != null) {
            return archiveStreamProvider.createArchiveInputStream(archiverName, in, actualCharset);
        }

        throw new ArchiveException("Archiver: " + archiverName + " not found.");
    }

    /**
     * Creates an archive output stream from an archiver name and an output stream.
     *
     * @param archiverName the archive name,
     * i.e. {@value #AR}, {@value #ZIP}, {@value #TAR}, {@value #JAR} or {@value #CPIO}
     * @param out the output stream
     * @return the archive output stream
     * @throws ArchiveException if the archiver name is not known
     * @throws StreamingNotSupportedException if the format cannot be
     * written to a stream
     * @throws IllegalArgumentException if the archiver name or stream is null
     */
    public ArchiveOutputStream createArchiveOutputStream(final String archiverName, final OutputStream out)
            throws ArchiveException {
        return createArchiveOutputStream(archiverName, out, entryCharset);
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(
            final String archiverName, final OutputStream out, final Charset actualCharset)
            throws ArchiveException {
        if (archiverName == null) {
            throw new IllegalArgumentException("Archivername must not be null.");
        }
        if (out == null) {
            throw new IllegalArgumentException("OutputStream must not be null.");
        }

        for (BuiltinArchiver archiver : BUILTIN_ARCHIVERS) {
            if (archiver.getName().equalsIgnoreCase(archiverName)) {
                return actualCharset != null
                        ? archiver.createArchiveOutputStream(out, actualCharset)
                        : archiver.createArchiveOutputStream(out);
            }
        }

        final ArchiveStreamProvider archiveStreamProvider = getArchiveOutputStreamProviders().get(toKey(archiverName));
        if (archiveStreamProvider != null) {
            return archiveStreamProvider.createArchiveOutputStream(archiverName, out, actualCharset);
        }

        throw new ArchiveException("Archiver: " + archiverName + " not found.");
    }

    /**
     * Create an archive input stream from an input stream, autodetecting
     * the archive type from the first few bytes of the stream. The InputStream
     * must support marks, like BufferedInputStream.
     *
     * @param in the input stream
     * @return the archive input stream
     * @throws ArchiveException if the archiver name is not known
     * @throws StreamingNotSupportedException if the format cannot be
     * read from a stream
     * @throws IllegalArgumentException if the stream is null or does not support mark
     */
    public ArchiveInputStream createArchiveInputStream(final InputStream in)
            throws ArchiveException {
        return createArchiveInputStream(detect(in), in);
    }

    /**
     * Try to determine the type of Archiver
     * @param in input stream
     * @return type of archiver if found
     * @throws ArchiveException if an archiver cannot be detected in the stream
     * @since 1.14
     */
    public static String detect(final InputStream in) throws ArchiveException {
        if (in == null) {
            throw new IllegalArgumentException("Stream must not be null.");
        }

        if (!in.markSupported()) {
            throw new IllegalArgumentException("Mark is not supported.");
        }

        final byte[] signature = new byte[SIGNATURE_SIZE];
        in.mark(signature.length);
        int signatureLength = -1;
        try {
            signatureLength = IOUtils.readFully(in, signature);
            in.reset();
        } catch (final IOException e) {
            throw new ArchiveException("IOException while reading signature.", e);
        }

        for (BuiltinArchiver archiver : BUILTIN_ARCHIVERS) {
            if (archiver.matches(signature, signatureLength)) {
                return archiver.getName();
            }
        }

        // Dump needs a bigger buffer to check the signature;
        if (DUMP_ARCHIVER != null) {
            final byte[] dumpsig = new byte[DUMP_SIGNATURE_SIZE];
            in.mark(dumpsig.length);
            try {
                signatureLength = IOUtils.readFully(in, dumpsig);
                in.reset();
            } catch (final IOException e) {
                throw new ArchiveException("IOException while reading dump signature", e);
            }
            if (DUMP_ARCHIVER.matches(dumpsig, signatureLength)) {
                return DUMP;
            }
        }

        // Tar needs an even bigger buffer to check the signature; read the first block
        if (TAR_ARCHIVER != null) {
            final byte[] tarHeader = new byte[TAR_HEADER_SIZE];
            in.mark(tarHeader.length);
            try {
                signatureLength = IOUtils.readFully(in, tarHeader);
                in.reset();
            } catch (final IOException e) {
                throw new ArchiveException("IOException while reading tar signature", e);
            }
            if (TAR_ARCHIVER.matches(tarHeader, signatureLength)) {
                return TAR;
            }

            // COMPRESS-117 - improve auto-recognition
            if (signatureLength >= TAR_HEADER_SIZE) {
                ArchiveInputStream tais = null;
                try {
                    tais = TAR_ARCHIVER.createArchiveInputStream(new ByteArrayInputStream(tarHeader));
                    // COMPRESS-191 - verify the header checksum
                    if (TAR_ARCHIVER.checkChecksum(tais)) {
                        return TAR;
                    }
                } catch (final Exception e) { // NOPMD NOSONAR
                    // can generate IllegalArgumentException as well
                    // as IOException
                    // autodetection, simply not a TAR
                    // ignored
                } finally {
                    IOUtils.closeQuietly(tais);
                }
            }
        }
        throw new ArchiveException("No Archiver found for the stream signature");
    }

    public SortedMap<String, ArchiveStreamProvider> getArchiveInputStreamProviders() {
        if (archiveInputStreamProviders == null) {
            archiveInputStreamProviders = Collections
                    .unmodifiableSortedMap(findAvailableArchiveInputStreamProviders());
        }
        return archiveInputStreamProviders;
    }

    public SortedMap<String, ArchiveStreamProvider> getArchiveOutputStreamProviders() {
        if (archiveOutputStreamProviders == null) {
            archiveOutputStreamProviders = Collections
                    .unmodifiableSortedMap(findAvailableArchiveOutputStreamProviders());
        }
        return archiveOutputStreamProviders;
    }

    @Override
    public Set<String> getInputStreamArchiveNames() {
        final HashSet<String> set = new HashSet<>(BUILTIN_ARCHIVERS.length);
        for (BuiltinArchiver archiver : BUILTIN_ARCHIVERS) {
            //noinspection StringEquality
            if (archiver.getName() != SEVEN_Z) {
                set.add(archiver.getName());
            }
        }
        return set;
    }

    @Override
    public Set<String> getOutputStreamArchiveNames() {
        final HashSet<String> set = new HashSet<>(BUILTIN_ARCHIVERS.length);
        for (BuiltinArchiver archiver : BUILTIN_ARCHIVERS) {
            if (archiver.isOutputAvailable()) {
                set.add(archiver.getName());
            }
        }
        return set;
    }

}
