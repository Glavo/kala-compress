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
package kala.compress.archivers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import kala.compress.utils.IOUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Creates an Archive[In|Out]putStreams from names or the first bytes of the InputStream. In order to add other implementations, you should extend
 * ArchiveStreamFactory and override the appropriate methods (and call their implementation from super of course).
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
 * ZipArchiveEntry entry = (ZipArchiveEntry) in.getNextEntry();
 * OutputStream out = Files.newOutputStream(dir.toPath().resolve(entry.getName()));
 * IOUtils.copy(in, out);
 * out.close();
 * in.close();
 * </pre>
 *
 * @Immutable provided that the deprecated method setEntryEncoding is not used.
 * @ThreadSafe even if the deprecated method setEntryEncoding is used
 */
public class ArchiveStreamFactory implements ArchiveStreamProvider {

    private static final int TAR_HEADER_SIZE = 512;

    private static final int DUMP_SIGNATURE_SIZE = 32;

    private static final int SIGNATURE_SIZE = 12;

    /**
     * Constant (value {@value}) used to identify the APK archive format.
     * <p>
     * APK file extensions are .apk, .xapk, .apks, .apkm
     * </p>
     *
     * @since 1.22
     */
    public static final String APK = "apk";

    /**
     * Constant (value {@value}) used to identify the XAPK archive format.
     * <p>
     * APK file extensions are .apk, .xapk, .apks, .apkm
     * </p>
     *
     * @since 1.22
     */
    public static final String XAPK = "xapk";

    /**
     * Constant (value {@value}) used to identify the APKS archive format.
     * <p>
     * APK file extensions are .apk, .xapk, .apks, .apkm
     * </p>
     *
     * @since 1.22
     */
    public static final String APKS = "apks";

    /**
     * Constant (value {@value}) used to identify the APKM archive format.
     * <p>
     * APK file extensions are .apk, .xapk, .apks, .apkm
     * </p>
     *
     * @since 1.22
     */
    public static final String APKM = "apkm";

    /**
     * Constant (value {@value}) used to identify the AR archive format.
     *
     * @since 1.1
     */
    public static final String AR = "ar";

    /**
     * Constant (value {@value}) used to identify the ARJ archive format. Not supported as an output stream type.
     *
     * @since 1.6
     */
    public static final String ARJ = "arj";

    /**
     * Constant (value {@value}) used to identify the CPIO archive format.
     *
     * @since 1.1
     */
    public static final String CPIO = "cpio";

    /**
     * Constant (value {@value}) used to identify the UNIX DUMP archive format. Not supported as an output stream type.
     *
     * @since 1.3
     */
    public static final String DUMP = "dump";

    /**
     * Constant (value {@value}) used to identify the JAR archive format.
     *
     * @since 1.1
     */
    public static final String JAR = "jar";

    /**
     * Constant used to identify the TAR archive format.
     *
     * @since 1.1
     */
    public static final String TAR = "tar";

    /**
     * Constant (value {@value}) used to identify the ZIP archive format.
     *
     * @since 1.1
     */
    public static final String ZIP = "zip";

    /**
     * Constant (value {@value}) used to identify the 7z archive format.
     *
     * @since 1.8
     */
    public static final String SEVEN_Z = "7z";

    private static final Map<String, BuiltinArchiver> BUILTIN_ARCHIVERS = new LinkedHashMap<>();
    private static final Set<String> ALL_NAMES;
    private static final Set<String> OUTPUT_NAMES;

    private static final BuiltinArchiver TAR_ARCHIVER;
    private static final BuiltinArchiver DUMP_ARCHIVER;

    private static BuiltinArchiver loadArchiver(List<BuiltinArchiver> archivers, String className) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }

        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            BuiltinArchiver archiver = (BuiltinArchiver) constructor.newInstance();
            archivers.add(archiver);
            return archiver;
        } catch (Throwable e) {
            throw new LinkageError(null, e);
        }
    }

    static {
        final String className = ArchiveStreamFactory.class.getName();
        final String packagePrefix = className.substring(0, className.lastIndexOf(".") + 1);

        ArrayList<BuiltinArchiver> archivers = new ArrayList<>();

        TAR_ARCHIVER = loadArchiver(archivers, packagePrefix + "tar.TarArchiver");
        DUMP_ARCHIVER = loadArchiver(archivers, packagePrefix + "dump.DumpArchiver");
        loadArchiver(archivers, packagePrefix + "zip.ZipArchiver");
        BuiltinArchiver jarArchiver = loadArchiver(archivers, packagePrefix + "jar.JarArchiver");
        loadArchiver(archivers, packagePrefix + "ar.ArArchiver");
        loadArchiver(archivers, packagePrefix + "arj.ArjArchiver");
        loadArchiver(archivers, packagePrefix + "cpio.CpioArchiver");
        loadArchiver(archivers, packagePrefix + "sevenz.SevenZArchiver");

        HashSet<String> archiverNames = new HashSet<>();
        HashSet<String> outputArchiverNames = new HashSet<>();

        for (BuiltinArchiver archiver : archivers) {
            BUILTIN_ARCHIVERS.put(archiver.getName(), archiver);
            archiverNames.add(archiver.getName());
            if (archiver.isOutputAvailable()) {
                outputArchiverNames.add(archiver.getName());
            }
        }

        if (jarArchiver != null) {
            BUILTIN_ARCHIVERS.put(APK, jarArchiver);
        }

        ALL_NAMES = Collections.unmodifiableSet(archiverNames);
        OUTPUT_NAMES = Collections.unmodifiableSet(outputArchiverNames);
    }

    /**
     * The singleton instance using the UTF-8.
     *
     * @since 1.21
     */
    public static final ArchiveStreamFactory DEFAULT = new ArchiveStreamFactory();

    /**
     * Try to determine the type of Archiver
     *
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
            throw new ArchiveException("Failure reading signature.", e);
        }

        // For now JAR files are detected as ZIP files.
        for (BuiltinArchiver archiver : BUILTIN_ARCHIVERS.values()) {
            if (archiver.matches(signature, signatureLength)) {
                return archiver.getName();
            }
        }

        if (DUMP_ARCHIVER != null) {
            // Dump needs a bigger buffer to check the signature;
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

        if (TAR_ARCHIVER != null) {
            // Tar needs an even bigger buffer to check the signature; read the first block
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

            // COMPRESS-117
            if (signatureLength >= TAR_HEADER_SIZE && TAR_ARCHIVER.checkTarChecksum(tarHeader)) {
                return TAR;
            }
        }
        throw new ArchiveException("No Archiver found for the stream signature");
    }

    private static String toKey(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Entry encoding, null for the default.
     */
    private final Charset entryEncoding;

    private final SortedMap<String, ArchiveStreamProvider> archiveInputStreamProviders = new TreeMap<>();
    private final SortedMap<String, ArchiveStreamProvider> archiveOutputStreamProviders = new TreeMap<>();
    private final Set<String> inputStreamArchiveNames = new HashSet<>(ALL_NAMES);
    private final Set<String> outputStreamArchiveNames = new HashSet<>(OUTPUT_NAMES);

    /**
     * Constructs an instance using the archiver default encoding.
     */
    public ArchiveStreamFactory() {
        this(null);
    }

    /**
     * Constructs an instance using the specified encoding.
     *
     * @param entryEncoding the encoding to be used.
     *
     * @since 1.27.1-0
     */
    public ArchiveStreamFactory(final @Nullable Charset entryEncoding) {
        this.entryEncoding = entryEncoding;
    }

    /**
     * Creates an archive input stream from an input stream, autodetecting the archive type from the first few bytes of the stream. The InputStream must support
     * marks, like BufferedInputStream.
     *
     * @param <I> The {@link ArchiveInputStream} type.
     * @param in  the input stream
     * @return the archive input stream
     * @throws ArchiveException               if the archiver name is not known
     * @throws StreamingNotSupportedException if the format cannot be read from a stream
     * @throws IllegalArgumentException       if the stream is null or does not support mark
     */
    public <I extends ArchiveInputStream<? extends ArchiveEntry>> I createArchiveInputStream(final InputStream in) throws ArchiveException {
        return createArchiveInputStream(detect(in), in);
    }

    /**
     * Creates an archive input stream from an archiver name and an input stream.
     *
     * @param <I>          The {@link ArchiveInputStream} type.
     * @param archiverName the archive name, i.e. {@value #AR}, {@value #ARJ}, {@value #ZIP}, {@value #TAR}, {@value #JAR}, {@value #CPIO}, {@value #DUMP} or
     *                     {@value #SEVEN_Z}
     * @param in           the input stream
     * @return the archive input stream
     * @throws ArchiveException               if the archiver name is not known
     * @throws StreamingNotSupportedException if the format cannot be read from a stream
     * @throws IllegalArgumentException       if the archiver name or stream is null
     */
    public <I extends ArchiveInputStream<? extends ArchiveEntry>> I createArchiveInputStream(final String archiverName, final InputStream in)
            throws ArchiveException {
        return createArchiveInputStream(archiverName, in, entryEncoding);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I extends ArchiveInputStream<? extends ArchiveEntry>> I createArchiveInputStream(final String archiverName, final InputStream in,
            final Charset actualEncoding) throws ArchiveException {

        if (archiverName == null) {
            throw new IllegalArgumentException("Archiver name must not be null.");
        }

        if (in == null) {
            throw new IllegalArgumentException("InputStream must not be null.");
        }

        BuiltinArchiver archiver = BUILTIN_ARCHIVERS.get(archiverName.toLowerCase(Locale.ROOT));
        if (archiver != null) {
            return (I) archiver.createArchiveInputStream(in, actualEncoding);
        }

        final ArchiveStreamProvider archiveStreamProvider = getArchiveInputStreamProviders().get(toKey(archiverName));
        if (archiveStreamProvider != null && !(archiveStreamProvider instanceof ArchiveStreamFactory)) {
            return archiveStreamProvider.createArchiveInputStream(archiverName, in, actualEncoding);
        }

        throw new ArchiveException("Archiver: " + archiverName + " not found.");
    }

    /**
     * Creates an archive output stream from an archiver name and an output stream.
     *
     * @param <O>          The {@link ArchiveOutputStream} type.
     * @param archiverName the archive name, i.e. {@value #AR}, {@value #ZIP}, {@value #TAR}, {@value #JAR} or {@value #CPIO}
     * @param out          the output stream
     * @return the archive output stream
     * @throws ArchiveException               if the archiver name is not known
     * @throws StreamingNotSupportedException if the format cannot be written to a stream
     * @throws IllegalArgumentException       if the archiver name or stream is null
     */
    public <O extends ArchiveOutputStream<? extends ArchiveEntry>> O createArchiveOutputStream(final String archiverName, final OutputStream out)
            throws ArchiveException {
        return createArchiveOutputStream(archiverName, out, entryEncoding);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O extends ArchiveOutputStream<? extends ArchiveEntry>> O createArchiveOutputStream(final String archiverName, final OutputStream out,
            final Charset actualEncoding) throws ArchiveException {
        if (archiverName == null) {
            throw new IllegalArgumentException("Archiver name must not be null.");
        }
        if (out == null) {
            throw new IllegalArgumentException("OutputStream must not be null.");
        }

        BuiltinArchiver archiver = BUILTIN_ARCHIVERS.get(archiverName.toLowerCase(Locale.ROOT));
        if (archiver != null) {
            return (O) archiver.createArchiveOutputStream(out, actualEncoding);
        }

        final ArchiveStreamProvider archiveStreamProvider = getArchiveOutputStreamProviders().get(toKey(archiverName));
        if (archiveStreamProvider != null && !(archiveStreamProvider instanceof ArchiveStreamFactory)) {
            return archiveStreamProvider.createArchiveOutputStream(archiverName, out, actualEncoding);
        }

        throw new ArchiveException("Archiver: " + archiverName + " not found.");
    }

    /**
     * Gets an unmodifiable sorted map from input stream provider names to provider objects.
     *
     * @return an unmodifiable sorted map of from input stream provider names to provider objects.
     */
    public SortedMap<String, ArchiveStreamProvider> getArchiveInputStreamProviders() {
        return Collections.unmodifiableSortedMap(archiveInputStreamProviders);
    }

    /**
     * Gets an unmodifiable sorted map from output stream provider names to provider objects.
     *
     * @return an unmodifiable sorted map of from input stream provider names to provider objects.
     */
    public SortedMap<String, ArchiveStreamProvider> getArchiveOutputStreamProviders() {
        return Collections.unmodifiableSortedMap(archiveOutputStreamProviders);
    }

    /**
     * Gets the encoding to use for arj, jar, ZIP, dump, cpio and tar files, or null for the archiver default.
     *
     * @return entry encoding, or null for the archiver default
     * @apiNote This method has a different signature in commons-compress.
     * @since 1.27.1-0
     */
    public Charset getEntryEncoding() {
        return entryEncoding;
    }

    @Override
    public Set<String> getInputStreamArchiveNames() {
        return Collections.unmodifiableSet(inputStreamArchiveNames);
    }

    @Override
    public Set<String> getOutputStreamArchiveNames() {
        return Collections.unmodifiableSet(outputStreamArchiveNames);
    }

    /**
     * @since 1.27.1-0
     */
    @ApiStatus.Experimental
    public ArchiveStreamFactory withInstalledProviders() {
        return withProviders(ServiceLoader.load(ArchiveStreamProvider.class));
    }

    /**
     * @since 1.27.1-0
     */
    @ApiStatus.Experimental
    public ArchiveStreamFactory withInstalledProviders(ClassLoader classLoader) {
        return withProviders(ServiceLoader.load(ArchiveStreamProvider.class, classLoader));
    }

    /**
     * @since 1.27.1-0
     */
    @ApiStatus.Experimental
    public ArchiveStreamFactory withProviders(Iterable<? extends ArchiveStreamProvider> providers) {
        ArchiveStreamFactory result = new ArchiveStreamFactory(this.entryEncoding);
        for (ArchiveStreamProvider provider : providers) {
            for (String name : provider.getInputStreamArchiveNames()) {
                result.archiveInputStreamProviders.put(toKey(name), provider);
                result.inputStreamArchiveNames.add(name);
            }
            for (String name : provider.getOutputStreamArchiveNames()) {
                result.archiveOutputStreamProviders.put(toKey(name), provider);
                result.outputStreamArchiveNames.add(name);
            }
        }
        return result;
    }

    /**
     * This is an internal class and should not be used directly.
     *
     * @author Glavo
     * @since 1.27.1-0
     */
    @ApiStatus.Internal
    public static abstract class BuiltinArchiver {
        private final String name;

        protected BuiltinArchiver(String name) {
            this.name = name;
        }

        public final String getName() {
            return name;
        }

        public boolean matches(final byte[] signature, final int length) {
            return false;
        }

        // For verify tar file
        // COMPRESS-191 - verify the header checksum
        public boolean checkTarChecksum(byte[] bytes) {
            throw new UnsupportedOperationException("checkChecksum");
        }

        public ArchiveInputStream<?> createArchiveInputStream(final InputStream in, final Charset charset) throws ArchiveException {
            throw new StreamingNotSupportedException(name);
        }

        public boolean isOutputAvailable() {
            return false;
        }

        public ArchiveOutputStream<?> createArchiveOutputStream(final OutputStream out, final Charset charset) throws ArchiveException {
            throw new StreamingNotSupportedException(name);
        }

        @Override
        public String toString() {
            return name + " archiver";
        }
    }
}
