package com.crm.view;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Optional reproducible native-icon generation: mvn -Dtest=BrandAssetsIT test. Outputs only under target/. */
class BrandAssetsIT {
    @Test void generateNativeIconsFromTheVectorMark() throws Exception {
        FutureTask<Map<Integer, byte[]>> rendering = new FutureTask<>(() -> {
            Map<Integer, byte[]> images = new TreeMap<>();
            for (int size : List.of(16, 32, 48, 64, 128, 256, 512, 1024)) {
                ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(SwingFXUtils.fromFXImage(BrandMark.icon(size), null), "png", png);
                images.put(size, png.toByteArray());
            }
            return images;
        });
        Platform.startup(() -> { Platform.setImplicitExit(false); rendering.run(); });
        Map<Integer, byte[]> images;
        try { images = rendering.get(20, TimeUnit.SECONDS); } finally { Platform.exit(); }
        Path destination = Files.createDirectories(Path.of("target", "brand-assets"));
        Files.write(destination.resolve("voidreach-mark.png"), images.get(256));
        List<Integer> icoSizes = List.of(16, 32, 48, 64, 128, 256);
        int headerSize = 6 + 16 * icoSizes.size(), offset = headerSize;
        ByteBuffer ico = ByteBuffer.allocate(headerSize + icoSizes.stream().mapToInt(size -> images.get(size).length).sum()).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short)0).putShort((short)1).putShort((short)icoSizes.size());
        for (int size : icoSizes) {
            byte[] png = images.get(size);
            ico.put((byte)(size == 256 ? 0 : size)).put((byte)(size == 256 ? 0 : size)).put((byte)0).put((byte)0);
            ico.putShort((short)1).putShort((short)32).putInt(png.length).putInt(offset); offset += png.length;
        }
        icoSizes.forEach(size -> ico.put(images.get(size)));
        Files.write(destination.resolve("VoidReach-v2.ico"), ico.array()); assertEquals(offset, ico.position());
        Map<Integer, String> types = new LinkedHashMap<>(); types.put(128, "ic07"); types.put(256, "ic08"); types.put(512, "ic09"); types.put(1024, "ic10");
        int length = 8 + types.keySet().stream().mapToInt(size -> images.get(size).length + 8).sum();
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(destination.resolve("VoidReach-v2.icns")))) {
            output.writeBytes("icns"); output.writeInt(length);
            for (var entry : types.entrySet()) { output.writeBytes(entry.getValue()); output.writeInt(images.get(entry.getKey()).length + 8); output.write(images.get(entry.getKey())); }
        }
        assertEquals(length, Files.size(destination.resolve("VoidReach-v2.icns")));
        for (var entry : images.entrySet()) assertEquals(entry.getKey().intValue(), ImageIO.read(new ByteArrayInputStream(entry.getValue())).getWidth());
    }
}
