package kala.compress.archivers.sevenz;

import kala.compress.archivers.*;

final class SevenZArchiver extends BuiltinArchiver {
    public SevenZArchiver() {
        super(ArchiveStreamFactory.SEVEN_Z);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return SevenZArchiveReader.matches(signature, length);
    }
}
