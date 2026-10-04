package com.junseo.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class TestImages {

    public static final String EXIF_SECRET = "GPS 37.5665N 126.9780E";

    private TestImages() {}

    /** Left half red, right half blue, so rotations are observable. */
    public static BufferedImage split(int w, int h, int type) {
        BufferedImage img = new BufferedImage(w, h, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        return img;
    }

    public static byte[] jpeg(int w, int h) {
        return write(split(w, h, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    /** Left half fully transparent, right half opaque blue. */
    public static byte[] pngWithAlpha(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        return write(img, "png");
    }

    /** Inserts an APP1/EXIF segment (orientation, make, a fake GPS description) right after SOI. */
    public static byte[] withExif(byte[] jpeg, int orientation) {
        byte[] make = "Apple\0".getBytes(StandardCharsets.US_ASCII);
        byte[] description = (EXIF_SECRET + "\0").getBytes(StandardCharsets.US_ASCII);
        int entries = 3;
        int ifdSize = 2 + entries * 12 + 4;
        int dataStart = 8 + ifdSize;
        ByteBuffer tiff = ByteBuffer.allocate(dataStart + make.length + description.length).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) entries);
        tiff.putShort((short) 0x010E).putShort((short) 2).putInt(description.length).putInt(dataStart + make.length);
        tiff.putShort((short) 0x010F).putShort((short) 2).putInt(make.length).putInt(dataStart);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);
        tiff.put(make).put(description);

        byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int length = 2 + header.length + tiff.capacity();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(header);
        out.writeBytes(tiff.array());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** JPEG marker codes of every header segment before the image data. */
    public static List<Integer> jpegMarkers(byte[] d) {
        List<Integer> markers = new ArrayList<>();
        int i = 2;
        while (i + 4 <= d.length && (d[i] & 0xFF) == 0xFF) {
            int marker = d[i + 1] & 0xFF;
            markers.add(marker);
            if (marker == 0xDA) {
                break;
            }
            i += 2 + (((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF));
        }
        return markers;
    }

    public static BufferedImage read(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static byte[] write(BufferedImage img, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
