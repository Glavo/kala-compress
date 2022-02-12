package kala.compress.archivers.sevenz;

import kala.compress.archivers.*;

public class SevenZArchiver extends BuiltinArchiver {
    public SevenZArchiver() {
        super(ArchiveStreamFactory.SEVEN_Z);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return SevenZFile.matches(signature, length);
    }
}