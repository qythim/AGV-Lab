package com.agvlab.rcs.map.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MapContentStoreTests {
    @TempDir Path directory;
    @Test void restoresMissingAndCorruptFileFromGzip() throws Exception {
        var store = new MapContentStore(directory.toString());
        String json = "{\"pointList\":[],\"lineList\":[]}";
        byte[] gzip = store.compress(json);
        assertEquals(json, store.read(1, gzip, store.digest(json)));
        Files.writeString(directory.resolve("1.json"),"損毀資料");
        assertEquals(json, store.read(1,gzip,store.digest(json)));
        assertEquals(json,Files.readString(directory.resolve("1.json")));
        Files.delete(directory.resolve("1.json"));
        assertEquals(json,store.read(1,gzip,store.digest(json)));
        assertThrows(IllegalStateException.class,()->store.read(2,gzip,"錯誤雜湊"));
    }
}
