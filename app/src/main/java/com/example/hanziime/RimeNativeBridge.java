package com.example.hanziime;

final class RimeNativeBridge {
    private static final boolean AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("ziyu_rime");
            loaded = true;
        } catch (LinkageError error) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private RimeNativeBridge() {}

    static boolean isAvailable() {
        return AVAILABLE;
    }

    static native boolean initialize(String sharedDirectory, String userDirectory);
    static native String[] query(String input, String schemaId, int limit);
    static native String selectCandidate(int index);
    static native void finalizeEngine();
}
