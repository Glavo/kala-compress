# Changelog

## 1.27.1-2 (In development)

Based on [apache/commons-compress@e237e3b](https://github.com/apache/commons-compress/commit/e237e3b9ede9d1aaea61cb7fd29e199ea4d7ae71).

## 1.27.1-1 (2024-12-01)

Based on [apache/commons-compress@b2de056](https://github.com/apache/commons-compress/commit/b2de05610080da6b55a43e8562e2b733fc194ce6).

* Fix crash when loading `ZipArchiveReader` when `kala.compress.compressors.bzip2` or `kala.compress.compressors.deflate64` are not present.

Breaking Changes:

* Change the return type of `ZipArchiveReader::getEntries()` and `ZipArchiveReader::getEntriesInPhysicalOrder()` from `Enumeration<ZipArchiveEntry>` to `Iterable<ZipArchiveEntry>`.

## 1.27.1-0 (2024-12-01)

Based on [apache/commons-compress@b2de056](https://github.com/apache/commons-compress/commit/b2de05610080da6b55a43e8562e2b733fc194ce6).
