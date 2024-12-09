# Changelog

## 1.27.1-2 (In development)

Based on [apache/commons-compress@d0aec98](https://github.com/apache/commons-compress/commit/d0aec98f62d1870d22f869aeec5ac1ed9ea3126a).

## 1.27.1-1 (2024-12-01)

Based on [apache/commons-compress@b2de056](https://github.com/apache/commons-compress/commit/b2de05610080da6b55a43e8562e2b733fc194ce6).

* Fix crash when loading `ZipArchiveReader` when `kala.compress.compressors.bzip2` or `kala.compress.compressors.deflate64` are not present.

Breaking Changes:

* Change the return type of `ZipArchiveReader::getEntries()` and `ZipArchiveReader::getEntriesInPhysicalOrder()` from `Enumeration<ZipArchiveEntry>` to `Iterable<ZipArchiveEntry>`.

## 1.27.1-0 (2024-12-01)

Based on [apache/commons-compress@b2de056](https://github.com/apache/commons-compress/commit/b2de05610080da6b55a43e8562e2b733fc194ce6).
