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
package kala.compress.compressors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.*;

import kala.compress.utils.IOUtils;
import kala.compress.utils.ServiceLoaderIterator;

/**
 * <p>
 * Factory to create Compressor[In|Out]putStreams from names. To add other
 * implementations you should extend CompressorStreamFactory and override the
 * appropriate methods (and call their implementation from super of course).
 * </p>
 *
 * Example (Compressing a file):
 *
 * <pre>
 * final OutputStream out = Files.newOutputStream(output.toPath());
 * CompressorOutputStream cos = new CompressorStreamFactory()
 *         .internalCreateCompressorOutputStream(CompressorStreamFactory.BZIP2, out);
 * IOUtils.copy(Files.newInputStream(input.toPath()), cos);
 * cos.close();
 * </pre>
 *
 * Example (Decompressing a file):
 *
 * <pre>
 * final InputStream is = Files.newInputStream(input.toPath());
 * CompressorInputStream in = new CompressorStreamFactory().internalCreateCompressorInputStream(CompressorStreamFactory.BZIP2,
 *         is);
 * IOUtils.copy(in, Files.newOutputStream(output.toPath()));
 * in.close();
 * </pre>
 *
 * @Immutable provided that the deprecated method setDecompressConcatenated is
 *            not used.
 * @ThreadSafe even if the deprecated method setDecompressConcatenated is used
 */
public class CompressorStreamFactory implements CompressorStreamProvider {

    private static final CompressorStreamFactory SINGLETON = new CompressorStreamFactory();

    /**
     * Constant (value {@value}) used to identify the BROTLI compression
     * algorithm.
     *
     * @since 1.14
     */
    public static final String BROTLI = "br";

    /**
     * Constant (value {@value}) used to identify the BZIP2 compression
     * algorithm.
     *
     * @since 1.1
     */
    public static final String BZIP2 = "bzip2";

    /**
     * Constant (value {@value}) used to identify the GZIP compression
     * algorithm.
     *
     * @since 1.1
     */
    public static final String GZIP = "gz";

    /**
     * Constant (value {@value}) used to identify the PACK200 compression
     * algorithm.
     *
     * @since 1.3
     */
    public static final String PACK200 = "pack200";

    /**
     * Constant (value {@value}) used to identify the XZ compression method.
     *
     * @since 1.4
     */
    public static final String XZ = "xz";

    /**
     * Constant (value {@value}) used to identify the LZMA compression method.
     *
     * @since 1.6
     */
    public static final String LZMA = "lzma";

    /**
     * Constant (value {@value}) used to identify the "framed" Snappy
     * compression method.
     *
     * @since 1.7
     */
    public static final String SNAPPY_FRAMED = "snappy-framed";

    /**
     * Constant (value {@value}) used to identify the "raw" Snappy compression
     * method. Not supported as an output stream type.
     *
     * @since 1.7
     */
    public static final String SNAPPY_RAW = "snappy-raw";

    /**
     * Constant (value {@value}) used to identify the traditional Unix compress
     * method. Not supported as an output stream type.
     *
     * @since 1.7
     */
    public static final String Z = "z";

    /**
     * Constant (value {@value}) used to identify the Deflate compress method.
     *
     * @since 1.9
     */
    public static final String DEFLATE = "deflate";

    /**
     * Constant (value {@value}) used to identify the Deflate64 compress method.
     *
     * @since 1.16
     */
    public static final String DEFLATE64 = "deflate64";

    /**
     * Constant (value {@value}) used to identify the block LZ4
     * compression method.
     *
     * @since 1.14
     */
    public static final String LZ4_BLOCK = "lz4-block";

    /**
     * Constant (value {@value}) used to identify the frame LZ4
     * compression method.
     *
     * @since 1.14
     */
    public static final String LZ4_FRAMED = "lz4-framed";

    /**
     * Constant (value {@value}) used to identify the Zstandard compression
     * algorithm. Not supported as an output stream type.
     *
     * @since 1.16
     */
    public static final String ZSTANDARD = "zstd";

    private static final BuiltinCompressor[] BUILTIN_COMPRESSORS;

    static {
        final String[] builtinCompressorClasses = {
                "kala.compress.compressors.brotli.BrotliCompressor",
                "kala.compress.compressors.bzip2.BZip2Compressor",
                "kala.compress.compressors.deflate.DeflateCompressor",
                "kala.compress.compressors.deflate64.Deflate64Compressor",
                "kala.compress.compressors.gzip.GzipCompressor",
                "kala.compress.compressors.lz4.BlockLZ4Compressor",
                "kala.compress.compressors.lz4.FramedLZ4Compressor",
                "kala.compress.compressors.lzma.LZMACompressor",
                "kala.compress.compressors.pack200.Pack200Compressor",
                "kala.compress.compressors.snappy.FramedSnappyCompressor",
                "kala.compress.compressors.snappy.SnappyCompressor",
                "kala.compress.compressors.xz.XZCompressor",
                "kala.compress.compressors.z.ZCompressor",
                "kala.compress.compressors.zstandard.ZstdCompressor"
        };

        ArrayList<BuiltinCompressor> builtinCompressors = new ArrayList<>();
        for (String cls : builtinCompressorClasses) {
            try {
                Class<?> clazz = Class.forName(cls);

                if (!BuiltinCompressor.class.isAssignableFrom(clazz)) {
                    throw new LinkageError(clazz + " is not a subclass of BuiltinCompressor");
                }

                Constructor<?> constructor = clazz.getConstructor();
                constructor.setAccessible(true);

                BuiltinCompressor archiver = (BuiltinCompressor) constructor.newInstance();
                builtinCompressors.add(archiver);

            } catch (ClassNotFoundException ignored) {
            } catch (ReflectiveOperationException e) {
                throw (LinkageError) new LinkageError().initCause(e);
            }
        }

        BUILTIN_COMPRESSORS = builtinCompressors.toArray(new BuiltinCompressor[0]);
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
    public static SortedMap<String, CompressorStreamProvider> findAvailableCompressorInputStreamProviders() {
        final TreeMap<String, CompressorStreamProvider> map = new TreeMap<>();
        putAll(SINGLETON.getInputStreamCompressorNames(), SINGLETON, map);
        new ServiceLoaderIterator<>(CompressorStreamProvider.class)
                .forEachRemaining(provider -> putAll(provider.getInputStreamCompressorNames(), provider, map));
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
    public static SortedMap<String, CompressorStreamProvider> findAvailableCompressorOutputStreamProviders() {
        final TreeMap<String, CompressorStreamProvider> map = new TreeMap<>();
        putAll(SINGLETON.getOutputStreamCompressorNames(), SINGLETON, map);

        new ServiceLoaderIterator<>(CompressorStreamProvider.class)
                .forEachRemaining(provider -> putAll(provider.getOutputStreamCompressorNames(), provider, map));
        return map;
    }

    public static CompressorStreamFactory getSingleton() {
        return SINGLETON;
    }

    static void putAll(final Set<String> names, final CompressorStreamProvider provider,
            final TreeMap<String, CompressorStreamProvider> map) {
        for (final String name : names) {
            map.put(toKey(name), provider);
        }
    }

    private static Iterator<CompressorStreamProvider> serviceLoaderIterator() {
        return new ServiceLoaderIterator<>(CompressorStreamProvider.class);
    }

    private static String toKey(final String name) {
        return name.toUpperCase(Locale.ROOT);
    }

    /**
     * If true, decompress until the end of the input. If false, stop after the
     * first stream and leave the input position to point to the next byte after
     * the stream
     */
    private final Boolean decompressUntilEOF;
    // This is Boolean so setDecompressConcatenated can determine whether it has
    // been set by the ctor
    // once the setDecompressConcatenated method has been removed, it can revert
    // to boolean

    private SortedMap<String, CompressorStreamProvider> compressorInputStreamProviders;

    private SortedMap<String, CompressorStreamProvider> compressorOutputStreamProviders;

    /**
     * If true, decompress until the end of the input. If false, stop after the
     * first stream and leave the input position to point to the next byte after
     * the stream
     */
    private volatile boolean decompressConcatenated;

    private final int memoryLimitInKb;

    /**
     * Create an instance with the decompress Concatenated option set to false.
     */
    public CompressorStreamFactory() {
        this.decompressUntilEOF = null;
        this.memoryLimitInKb = -1;
    }

    /**
     * Create an instance with the provided decompress Concatenated option.
     *
     * @param decompressUntilEOF
     *            if true, decompress until the end of the input; if false, stop
     *            after the first stream and leave the input position to point
     *            to the next byte after the stream. This setting applies to the
     *            gzip, bzip2 and xz formats only.
     *
     * @param memoryLimitInKb
     *            Some streams require allocation of potentially significant
     *            byte arrays/tables, and they can offer checks to prevent OOMs
     *            on corrupt files.  Set the maximum allowed memory allocation in KBs.
     *
     * @since 1.14
     */
    public CompressorStreamFactory(final boolean decompressUntilEOF, final int memoryLimitInKb) {
        this.decompressUntilEOF = decompressUntilEOF;
        // Also copy to existing variable so can continue to use that as the
        // current value
        this.decompressConcatenated = decompressUntilEOF;
        this.memoryLimitInKb = memoryLimitInKb;
    }

    /**
     * Create an instance with the provided decompress Concatenated option.
     *
     * @param decompressUntilEOF
     *            if true, decompress until the end of the input; if false, stop
     *            after the first stream and leave the input position to point
     *            to the next byte after the stream. This setting applies to the
     *            gzip, bzip2 and xz formats only.
     * @since 1.10
     */
    public CompressorStreamFactory(final boolean decompressUntilEOF) {
        this(decompressUntilEOF, -1);
    }

    /**
     * Try to detect the type of compressor stream.
     *
     * @param inputStream input stream
     * @return type of compressor stream detected
     * @throws CompressorException if no compressor stream type was detected
     *                             or if something else went wrong
     * @throws IllegalArgumentException if stream is null or does not support mark
     *
     * @since 1.14
     */
    public static String detect(final InputStream inputStream) throws CompressorException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Stream must not be null.");
        }

        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException("Mark is not supported.");
        }

        final byte[] signature = new byte[12];
        inputStream.mark(signature.length);
        int signatureLength = -1;
        try {
            signatureLength = IOUtils.readFully(inputStream, signature);
            inputStream.reset();
        } catch (final IOException e) {
            throw new CompressorException("IOException while reading signature.", e);
        }

        for (BuiltinCompressor compressor : BUILTIN_COMPRESSORS) {
            if (compressor.matches(signature, signatureLength)) {
                return compressor.getName();
            }
        }

        throw new CompressorException("No Compressor found for the stream signature.");
    }
    /**
     * Create an compressor input stream from an input stream, autodetecting the
     * compressor type from the first few bytes of the stream. The InputStream
     * must support marks, like BufferedInputStream.
     *
     * @param in
     *            the input stream
     * @return the compressor input stream
     * @throws CompressorException
     *             if the compressor name is not known
     * @throws IllegalArgumentException
     *             if the stream is null or does not support mark
     * @since 1.1
     */
    public CompressorInputStream createCompressorInputStream(final InputStream in) throws CompressorException {
        return createCompressorInputStream(detect(in), in);
    }

    /**
     * Creates a compressor input stream from a compressor name and an input
     * stream.
     *
     * @param name
     *            of the compressor, i.e. {@value #GZIP}, {@value #BZIP2},
     *            {@value #XZ}, {@value #LZMA}, {@value #PACK200},
     *            {@value #SNAPPY_RAW}, {@value #SNAPPY_FRAMED}, {@value #Z},
     *            {@value #LZ4_BLOCK}, {@value #LZ4_FRAMED}, {@value #ZSTANDARD},
     *            {@value #DEFLATE64}
     *            or {@value #DEFLATE}
     * @param in
     *            the input stream
     * @return compressor input stream
     * @throws CompressorException
     *             if the compressor name is not known or not available,
     *             or if there's an IOException or MemoryLimitException thrown
     *             during initialization
     * @throws IllegalArgumentException
     *             if the name or input stream is null
     */
    public CompressorInputStream createCompressorInputStream(final String name, final InputStream in)
            throws CompressorException {
        return createCompressorInputStream(name, in, decompressConcatenated);
    }

    @Override
    public CompressorInputStream createCompressorInputStream(final String name, final InputStream in,
            final boolean actualDecompressConcatenated) throws CompressorException {
        if (name == null || in == null) {
            throw new IllegalArgumentException("Compressor name and stream must not be null.");
        }

        for (BuiltinCompressor compressor : BUILTIN_COMPRESSORS) {
            if (compressor.getName().equalsIgnoreCase(name)) {
                return compressor.createCompressorInputStream(in, actualDecompressConcatenated, memoryLimitInKb);
            }
        }

        final CompressorStreamProvider compressorStreamProvider = getCompressorInputStreamProviders().get(toKey(name));
        if (compressorStreamProvider != null) {
            return compressorStreamProvider.createCompressorInputStream(name, in, actualDecompressConcatenated);
        }

        throw new CompressorException("Compressor: " + name + " not found.");
    }

    /**
     * Creates an compressor output stream from an compressor name and an output
     * stream.
     *
     * @param name
     *            the compressor name, i.e. {@value #GZIP}, {@value #BZIP2},
     *            {@value #XZ}, {@value #PACK200}, {@value #SNAPPY_FRAMED},
     *            {@value #LZ4_BLOCK}, {@value #LZ4_FRAMED}, {@value #ZSTANDARD}
     *            or {@value #DEFLATE}
     * @param out
     *            the output stream
     * @return the compressor output stream
     * @throws CompressorException
     *             if the archiver name is not known
     * @throws IllegalArgumentException
     *             if the archiver name or stream is null
     */
    @Override
    public CompressorOutputStream createCompressorOutputStream(final String name, final OutputStream out)
            throws CompressorException {
        if (name == null || out == null) {
            throw new IllegalArgumentException("Compressor name and stream must not be null.");
        }

        for (BuiltinCompressor compressor : BUILTIN_COMPRESSORS) {
            if (compressor.getName().equalsIgnoreCase(name)) {
                return compressor.createCompressorOutputStream(out);
            }
        }

        final CompressorStreamProvider compressorStreamProvider = getCompressorOutputStreamProviders().get(toKey(name));
        if (compressorStreamProvider != null) {
            return compressorStreamProvider.createCompressorOutputStream(name, out);
        }
        throw new CompressorException("Compressor: " + name + " not found.");
    }

    public SortedMap<String, CompressorStreamProvider> getCompressorInputStreamProviders() {
        if (compressorInputStreamProviders == null) {
            compressorInputStreamProviders = Collections
                    .unmodifiableSortedMap(findAvailableCompressorInputStreamProviders());
        }
        return compressorInputStreamProviders;
    }

    public SortedMap<String, CompressorStreamProvider> getCompressorOutputStreamProviders() {
        if (compressorOutputStreamProviders == null) {
            compressorOutputStreamProviders = Collections
                    .unmodifiableSortedMap(findAvailableCompressorOutputStreamProviders());
        }
        return compressorOutputStreamProviders;
    }

    // For Unit tests
    boolean getDecompressConcatenated() {
        return decompressConcatenated;
    }

    public Boolean getDecompressUntilEOF() {
        return decompressUntilEOF;
    }

    @Override
    public Set<String> getInputStreamCompressorNames() {
        final HashSet<String> set = new HashSet<>(BUILTIN_COMPRESSORS.length);
        for (BuiltinCompressor compressor : BUILTIN_COMPRESSORS) {
            set.add(compressor.getName());
        }
        return set;
    }

    @Override
    public Set<String> getOutputStreamCompressorNames() {
        final HashSet<String> set = new HashSet<>(BUILTIN_COMPRESSORS.length);
        for (BuiltinCompressor compressor : BUILTIN_COMPRESSORS) {
            if (compressor.isOutputAvailable()) {
                set.add(compressor.getName());
            }
        }
        return set;
    }
}
