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
package kala.compress.archivers.examples;

import kala.compress.archivers.ArchiveEntry;
import kala.compress.archivers.ArchiveException;
import kala.compress.archivers.ArchiveOutputStream;
import kala.compress.archivers.ArchiveStreamFactory;
import kala.compress.archivers.sevenz.SevenZArchiveEntry;
import kala.compress.archivers.sevenz.SevenZArchiveWriter;
import kala.compress.archivers.zip.ZipArchiveOutputStream;
import kala.compress.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Provides a high level API for creating archives.
 *
 * @since 1.17
 * @since 1.21 Supports {@link Path}.
 */
public class Archiver {

    private static class ArchiverFileVisitor<O extends ArchiveOutputStream<E>, E extends ArchiveEntry> extends SimpleFileVisitor<Path> {

        private final O target;
        private final Path directory;
        private final LinkOption[] linkOptions;

        private ArchiverFileVisitor(final O target, final Path directory, final LinkOption... linkOptions) {
            this.target = target;
            this.directory = directory;
            this.linkOptions = linkOptions == null ? IOUtils.EMPTY_LINK_OPTIONS : linkOptions.clone();
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
            return visit(dir, attrs, false);
        }

        protected FileVisitResult visit(final Path path, final BasicFileAttributes attrs, final boolean isFile) throws IOException {
            Objects.requireNonNull(path);
            Objects.requireNonNull(attrs);
            final String name = directory.relativize(path).toString().replace('\\', '/');
            if (!name.isEmpty()) {
                final E archiveEntry = target.createArchiveEntry(path, isFile || name.endsWith("/") ? name : name + "/", linkOptions);
                target.putArchiveEntry(archiveEntry);
                if (isFile) {
                    // Refactor this as a BiConsumer on Java 8
                    Files.copy(path, target);
                }
                target.closeArchiveEntry();
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            return visit(file, attrs, true);
        }
    }

    /**
     * No {@link FileVisitOption}.
     */
    public static final EnumSet<FileVisitOption> EMPTY_FileVisitOption = EnumSet.noneOf(FileVisitOption.class);

    /**
     * Creates an archive {@code target} by recursively including all files and directories in {@code directory}.
     *
     * @param target    the stream to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException if an I/O error occurs
     */
    public void create(final ArchiveOutputStream<?> target, final File directory) throws IOException {
        create(target, directory.toPath(), EMPTY_FileVisitOption);
    }

    /**
     * Creates an archive {@code target} by recursively including all files and directories in {@code directory}.
     *
     * @param target    the stream to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException if an I/O error occurs or the archive cannot be created for other reasons.
     * @since 1.21
     */
    public void create(final ArchiveOutputStream<?> target, final Path directory) throws IOException {
        create(target, directory, EMPTY_FileVisitOption);
    }

    /**
     * Creates an archive {@code target} by recursively including all files and directories in {@code directory}.
     *
     * @param target           the stream to write the new archive to.
     * @param directory        the directory that contains the files to archive.
     * @param fileVisitOptions linkOptions to configure the traversal of the source {@code directory}.
     * @param linkOptions      indicating how symbolic links are handled.
     * @throws IOException if an I/O error occurs or the archive cannot be created for other reasons.
     * @since 1.21
     */
    public void create(final ArchiveOutputStream<?> target, final Path directory, final EnumSet<FileVisitOption> fileVisitOptions,
            final LinkOption... linkOptions) throws IOException {
        Files.walkFileTree(directory, fileVisitOptions, Integer.MAX_VALUE, new ArchiverFileVisitor<>(target, directory, linkOptions));
        target.finish();
    }

    /**
     * Creates an archive {@code target} by recursively including all files and directories in {@code directory}.
     *
     * @param target    the file to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException if an I/O error occurs
     */
    public void create(final SevenZArchiveWriter target, final File directory) throws IOException {
        create(target, directory.toPath());
    }

    /**
     * Creates an archive {@code target} by recursively including all files and directories in {@code directory}.
     *
     * @param target    the file to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException if an I/O error occurs
     * @since 1.21
     */
    public void create(final SevenZArchiveWriter target, final Path directory) throws IOException {
        // This custom SimpleFileVisitor goes away with Java 8's BiConsumer.
        Files.walkFileTree(directory, new ArchiverFileVisitor<ArchiveOutputStream<ArchiveEntry>, ArchiveEntry>(null, directory) {

            @Override
            protected FileVisitResult visit(final Path path, final BasicFileAttributes attrs, final boolean isFile) throws IOException {
                Objects.requireNonNull(path);
                Objects.requireNonNull(attrs);
                final String name = directory.relativize(path).toString().replace('\\', '/');
                if (!name.isEmpty()) {
                    final SevenZArchiveEntry archiveEntry = target.createArchiveEntry(path, isFile || name.endsWith("/") ? name : name + "/");
                    target.putArchiveEntry(archiveEntry);
                    if (isFile) {
                        // Refactor this as a BiConsumer on Java 8
                        target.write(path);
                    }
                    target.closeArchiveEntry();
                }
                return FileVisitResult.CONTINUE;
            }

        });
        target.finish();
    }

    /**
     * Creates an archive {@code target} using the format {@code
     * format} by recursively including all files and directories in {@code directory}.
     *
     * @param format    the archive format. This uses the same format as accepted by {@link ArchiveStreamFactory}.
     * @param target    the file to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException      if an I/O error occurs
     * @throws ArchiveException if the archive cannot be created for other reasons
     */
    public void create(final String format, final File target, final File directory) throws IOException, ArchiveException {
        create(format, target.toPath(), directory.toPath());
    }

    /**
     * Creates an archive {@code target} using the format {@code
     * format} by recursively including all files and directories in {@code directory}.
     *
     * <p>
     * This method creates a wrapper around the target stream which is never closed and thus leaks resources, please use
     * {@link #create(String,OutputStream,File,CloseableConsumer)} instead.
     * </p>
     *
     * @param format    the archive format. This uses the same format as accepted by {@link ArchiveStreamFactory}.
     * @param target    the stream to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException      if an I/O error occurs
     * @throws ArchiveException if the archive cannot be created for other reasons
     * @deprecated this method leaks resources
     */
    @Deprecated
    public void create(final String format, final OutputStream target, final File directory) throws IOException, ArchiveException {
        create(format, target, directory, CloseableConsumer.NULL_CONSUMER);
    }

    /**
     * Creates an archive {@code target} using the format {@code
     * format} by recursively including all files and directories in {@code directory}.
     *
     * <p>
     * This method creates a wrapper around the archive stream and the caller of this method is responsible for closing it - probably at the same time as
     * closing the stream itself. The caller is informed about the wrapper object via the {@code
     * closeableConsumer} callback as soon as it is no longer needed by this class.
     * </p>
     *
     * @param format            the archive format. This uses the same format as accepted by {@link ArchiveStreamFactory}.
     * @param target            the stream to write the new archive to.
     * @param directory         the directory that contains the files to archive.
     * @param closeableConsumer is informed about the stream wrapped around the passed in stream
     * @throws IOException      if an I/O error occurs
     * @throws ArchiveException if the archive cannot be created for other reasons
     * @since 1.19
     */
    public void create(final String format, final OutputStream target, final File directory, final CloseableConsumer closeableConsumer)
            throws IOException, ArchiveException {
        try (CloseableConsumerAdapter c = new CloseableConsumerAdapter(closeableConsumer)) {
            final ArchiveOutputStream<? extends ArchiveEntry> archiveOutputStream = ArchiveStreamFactory.DEFAULT.createArchiveOutputStream(format, target);
            create(c.track(archiveOutputStream), directory);
        }
    }

    /**
     * Creates an archive {@code target} using the format {@code
     * format} by recursively including all files and directories in {@code directory}.
     *
     * @param format    the archive format. This uses the same format as accepted by {@link ArchiveStreamFactory}.
     * @param target    the file to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException      if an I/O error occurs
     * @throws ArchiveException if the archive cannot be created for other reasons
     * @since 1.21
     */
    public void create(final String format, final Path target, final Path directory) throws IOException, ArchiveException {
        if (prefersSeekableByteChannel(format)) {
            try (SeekableByteChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                create(format, channel, directory);
                return;
            }
        }
        try (@SuppressWarnings("resource") // ArchiveOutputStream wraps newOutputStream result
        ArchiveOutputStream<?> outputStream = ArchiveStreamFactory.DEFAULT.createArchiveOutputStream(format, Files.newOutputStream(target))) {
            create(outputStream, directory, EMPTY_FileVisitOption);
        }
    }

    /**
     * Creates an archive {@code target} using the format {@code
     * format} by recursively including all files and directories in {@code directory}.
     *
     * <p>
     * This method creates a wrapper around the target channel which is never closed and thus leaks resources, please use
     * {@link #create(String,SeekableByteChannel,File,CloseableConsumer)} instead.
     * </p>
     *
     * @param format    the archive format. This uses the same format as accepted by {@link ArchiveStreamFactory}.
     * @param target    the channel to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException      if an I/O error occurs
     * @throws ArchiveException if the archive cannot be created for other reasons
     * @deprecated this method leaks resources
     */
    @Deprecated
    public void create(final String format, final SeekableByteChannel target, final File directory) throws IOException, ArchiveException {
        create(format, target, directory, CloseableConsumer.NULL_CONSUMER);
    }

    /**
     * Creates an archive {@code target} using the format {@code
     * format} by recursively including all files and directories in {@code directory}.
     *
     * <p>
     * This method creates a wrapper around the archive channel and the caller of this method is responsible for closing it - probably at the same time as
     * closing the channel itself. The caller is informed about the wrapper object via the {@code
     * closeableConsumer} callback as soon as it is no longer needed by this class.
     * </p>
     *
     * @param format            the archive format. This uses the same format as accepted by {@link ArchiveStreamFactory}.
     * @param target            the channel to write the new archive to.
     * @param directory         the directory that contains the files to archive.
     * @param closeableConsumer is informed about the stream wrapped around the passed in stream
     * @throws IOException      if an I/O error occurs
     * @throws ArchiveException if the archive cannot be created for other reasons
     * @since 1.19
     */
    public void create(final String format, final SeekableByteChannel target, final File directory, final CloseableConsumer closeableConsumer)
            throws IOException, ArchiveException {
        try (CloseableConsumerAdapter c = new CloseableConsumerAdapter(closeableConsumer)) {
            if (!prefersSeekableByteChannel(format)) {
                create(format, c.track(Channels.newOutputStream(target)), directory);
            } else if (ArchiveStreamFactory.ZIP.equalsIgnoreCase(format)) {
                create(c.track(new ZipArchiveOutputStream(target)), directory);
            } else if (ArchiveStreamFactory.SEVEN_Z.equalsIgnoreCase(format)) {
                create(c.track(new SevenZArchiveWriter(target)), directory);
            } else {
                // never reached as prefersSeekableByteChannel only returns true for ZIP and 7z
                throw new ArchiveException("Don't know how to handle format " + format);
            }
        }
    }

    /**
     * Creates an archive {@code target} using the format {@code
     * format} by recursively including all files and directories in {@code directory}.
     *
     * @param format    the archive format. This uses the same format as accepted by {@link ArchiveStreamFactory}.
     * @param target    the channel to write the new archive to.
     * @param directory the directory that contains the files to archive.
     * @throws IOException           if an I/O error occurs
     * @throws IllegalStateException if the format does not support {@code SeekableByteChannel}.
     */
    public void create(final String format, final SeekableByteChannel target, final Path directory) throws IOException {
        if (ArchiveStreamFactory.SEVEN_Z.equalsIgnoreCase(format)) {
            try (SevenZArchiveWriter sevenZFile = new SevenZArchiveWriter(target)) {
                create(sevenZFile, directory);
            }
        } else if (ArchiveStreamFactory.ZIP.equalsIgnoreCase(format)) {
            try (ZipArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(target)) {
                create(archiveOutputStream, directory, EMPTY_FileVisitOption);
            }
        } else {
            throw new IllegalStateException(format);
        }
    }

    private boolean prefersSeekableByteChannel(final String format) {
        return ArchiveStreamFactory.ZIP.equalsIgnoreCase(format) || ArchiveStreamFactory.SEVEN_Z.equalsIgnoreCase(format);
    }
}
