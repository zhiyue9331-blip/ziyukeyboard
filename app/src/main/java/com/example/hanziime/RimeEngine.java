package com.example.hanziime;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thin Java owner for the BSD-licensed librime native decoder. */
final class RimeEngine implements AutoCloseable {
    private static final String DATA_VERSION = "2";
    private final Map<String, Integer> candidateIndexes = new HashMap<>();
    private boolean closed;

    static RimeEngine create(Context context) {
        if (!RimeNativeBridge.isAvailable()) return null;
        try {
            File root = new File(context.getFilesDir(), "rime");
            File shared = new File(root, "shared");
            File user = new File(root, "user");
            installSharedData(context.getAssets(), shared);
            if (!user.exists() && !user.mkdirs()) return null;
            if (!RimeNativeBridge.initialize(shared.getAbsolutePath(), user.getAbsolutePath())) {
                return null;
            }
            return new RimeEngine();
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    synchronized List<Candidate> search(String input, boolean fuzzy) {
        return search(input, fuzzy ? "ziyu_pinyin_fuzzy" : "ziyu_pinyin",
                Candidate.Source.RIME);
    }

    synchronized List<Candidate> searchAssembly(String input) {
        return search(input, "ziyu_assembly", Candidate.Source.RIME_ASSEMBLY);
    }

    private List<Candidate> search(String input, String schema, Candidate.Source source) {
        if (closed || input.isBlank()) return List.of();
        String[] nativeRows = RimeNativeBridge.query(input, schema, 24);
        List<Candidate> result = new ArrayList<>();
        candidateIndexes.clear();
        for (int index = 0; index + 2 < nativeRows.length; index += 3) {
            String text = nativeRows[index];
            String comment = nativeRows[index + 1];
            int consumedLength;
            try {
                consumedLength = Integer.parseInt(nativeRows[index + 2]);
            } catch (NumberFormatException ignored) {
                consumedLength = -1;
            }
            String pronunciation = comment.isBlank() ? input : comment;
            Candidate candidate = new Candidate(text, pronunciation,
                    100_000 - index / 3, source, "", consumedLength);
            result.add(candidate);
            // The menu can contain the same text more than once with different comments. The
            // UI keeps the first occurrence, so selection must resolve to that same entry.
            if (!candidateIndexes.containsKey(text)) {
                candidateIndexes.put(text, index / 3);
            }
        }
        return result;
    }

    synchronized void recordSelection(Candidate candidate) {
        if (closed) return;
        Integer index = candidateIndexes.get(candidate.text());
        if (index != null) RimeNativeBridge.selectCandidate(index);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        candidateIndexes.clear();
        RimeNativeBridge.finalizeEngine();
    }

    private static void installSharedData(AssetManager assets, File shared) throws IOException {
        File marker = new File(shared, ".data-version");
        if (marker.isFile() && DATA_VERSION.equals(readFile(marker))) {
            return;
        }
        if (!shared.exists() && !shared.mkdirs()) {
            throw new IOException("Cannot create Rime shared directory");
        }
        copyAssetDirectory(assets, "rime", shared);
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write(DATA_VERSION.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyAssetDirectory(AssetManager assets, String assetPath, File target)
            throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null) return;
        for (String child : children) {
            String childPath = assetPath + "/" + child;
            String[] grandchildren = assets.list(childPath);
            File output = new File(target, child);
            if (grandchildren != null && grandchildren.length > 0) {
                if (!output.exists() && !output.mkdirs()) {
                    throw new IOException("Cannot create asset directory");
                }
                copyAssetDirectory(assets, childPath, output);
            } else {
                try (InputStream input = assets.open(childPath);
                     FileOutputStream stream = new FileOutputStream(output)) {
                    byte[] buffer = new byte[16 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count);
                }
            }
        }
    }

    private static String readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count == -1) break;
                offset += count;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }
}
