package com.axplatform.workspace;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

import javax.imageio.ImageIO;

/** Bounded original sources; XLSX is validated by the same safe import parser. */
public final class AssetValidation {
    public record Prepared(byte[] data, String mediaType, String extension) {}

    private AssetValidation() {}

    public static Prepared validate(String name, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
            throw new IOException("첨부는 10MB 이하의 파일이어야 합니다.");
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".csv")) {
            if (lower.endsWith(".xlsx"))
                com.axplatform.sheet.SheetImport.read(bytes, name, "UTF-8", "");
            else {
                if (bytes.length > 2 * 1024 * 1024) throw new IOException("CSV는 2MB 이하입니다.");
                for (byte b : bytes) if (b == 0) throw new IOException("CSV에 바이너리 문자가 있습니다.");
            }
            return new Prepared(
                    bytes,
                    lower.endsWith(".xlsx")
                            ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            : "text/csv",
                    lower.endsWith(".xlsx") ? "xlsx" : "csv");
        }
        if (lower.endsWith(".txt")) {
            try {
                String text =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes))
                                .toString();
                if (text.indexOf('\0') >= 0) throw new IOException("텍스트에 바이너리 문자가 있습니다.");
                return new Prepared(bytes, "text/plain", "txt");
            } catch (CharacterCodingException e) {
                throw new IOException("텍스트는 UTF-8 형식이어야 합니다.");
            }
        }
        boolean png = lower.endsWith(".png"),
                jpg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        if (!png && !jpg) throw new IOException("PNG/JPG, UTF-8 TXT, CSV, XLSX 파일을 지원합니다.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("실제 이미지 파일을 확인해 주세요.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if ((png && !"png".equalsIgnoreCase(format))
                        || (jpg && !"jpeg".equalsIgnoreCase(format)))
                    throw new IOException("확장자와 이미지 형식이 다릅니다.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0
                        || height <= 0
                        || width > 8000
                        || height > 8000
                        || (long) width * height > 16000000L)
                    throw new IOException("이미지 크기는 8,000px·1,600만 픽셀 이하입니다.");
                if (reader.read(0) == null) throw new IOException("이미지를 읽지 못했습니다.");
                return new Prepared(bytes, png ? "image/png" : "image/jpeg", png ? "png" : "jpg");
            } finally {
                reader.dispose();
            }
        }
    }
}
