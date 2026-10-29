package com.agvlab.rcs.map.service;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DB 壓縮內容為唯一來源，磁碟檔案可隨時重建。 */
@Component
public class MapContentStore {
    private static final Logger log = LoggerFactory.getLogger(MapContentStore.class);
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final Path directory;
    public MapContentStore(@Value("${rcs.map.storage-dir:map_storage}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public byte[] compress(String json) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_BYTES) throw new IllegalArgumentException("地圖內容不可超過 20 MiB");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(raw); }
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("地圖壓縮失敗", e); }
    }
    public String digest(String json) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("無法計算地圖雜湊", e); }
    }
    public String read(long id, byte[] compressed, String hash) {
        Path file = directory.resolve(id + ".json");
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= MAX_BYTES) {
                String json = Files.readString(file);
                if (digest(json).equals(hash)) return json;
            }
        } catch (IOException e) { log.warn("地圖快取讀取失敗，改讀資料庫：{}", id); }
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] raw = gzip.readNBytes(MAX_BYTES + 1);
            if (raw.length > MAX_BYTES) throw new IllegalStateException("地圖解壓縮超過容量限制");
            String json = new String(raw, StandardCharsets.UTF_8);
            if (!digest(json).equals(hash)) throw new IllegalStateException("地圖內容雜湊不一致");
            cache(id, json);
            return json;
        } catch (IOException e) { throw new IllegalStateException("地圖解壓縮失敗", e); }
    }
    public void cache(long id, String json) {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, id + "-", ".tmp");
            Files.writeString(temporary, json);
            try { Files.move(temporary, directory.resolve(id + ".json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, directory.resolve(id + ".json"), StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { log.warn("地圖已保存在資料庫，快取寫入失敗：{}", id, e); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }
}
