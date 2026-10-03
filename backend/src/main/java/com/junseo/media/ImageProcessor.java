package com.junseo.media;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Validates an upload and re-encodes it as two metadata-free JPEGs. Only pixels survive: EXIF (GPS,
 * device), ICC and comments are dropped because nothing but the raster is written back.
 */
@Component
public class ImageProcessor {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int FULL_SIDE = 1440;
    public static final int THUMB_SIDE = 540;
    static final long MAX_PIXELS = 50_000_000L;
    private final Semaphore processingSlots = new Semaphore(2);

    public record ProcessedImage(byte[] full, byte[] thumb) {}

    /** A photo takes well under a second: a few uploads at once wait their turn instead of failing at once. */
    static final long SLOT_WAIT_SECONDS = 15;

    public ProcessedImage process(byte[] data) {
        try {
            if (!processingSlots.tryAcquire(SLOT_WAIT_SECONDS, TimeUnit.SECONDS)) throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }
        try { return processImage(data); } finally { processingSlots.release(); }
    }

    private ProcessedImage processImage(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw invalid();
        }
        boolean jpeg = isJpeg(data);
        if (!jpeg && !isPng(data)) {
            throw invalid();
        }
        BufferedImage decoded = decode(data);
        // Phones store portrait shots sideways plus an EXIF hint; bake the rotation in before the hint is dropped.
        int orientation = jpeg ? ExifOrientation.read(data) : 1;
        BufferedImage upright = flattenAndOrient(decoded, orientation);
        BufferedImage full = fit(upright, FULL_SIDE);
        BufferedImage thumb = fit(full, THUMB_SIDE);
        return new ProcessedImage(encodeJpeg(full, 0.85f), encodeJpeg(thumb, 0.8f));
    }

    static boolean isJpeg(byte[] d) {
        return d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] d) {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (d.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (d[i] != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static BufferedImage decode(byte[] data) {
        // In-memory streams: ImageIO's default factories may spill every upload to a temp file.
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw invalid();
                }
                var params = reader.getDefaultReadParam();
                int subsample = Math.max(1, Math.max(reader.getWidth(0), reader.getHeight(0)) / FULL_SIDE);
                params.setSourceSubsampling(subsample, subsample, 0, 0);
                BufferedImage image = reader.read(0, params);
                if (image == null) {
                    throw invalid();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw invalid();
        }
    }

    /** Draws onto an opaque RGB canvas (transparent PNG areas become white) applying the EXIF orientation. */
    static BufferedImage flattenAndOrient(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5 && orientation <= 8;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(src, orientationTransform(orientation, w, h), null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static AffineTransform orientationTransform(int orientation, int w, int h) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0); // mirror horizontally
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h); // rotate 180
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h); // mirror vertically
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // transpose
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0); // rotate 90 clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w); // transverse
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w); // rotate 90 counter-clockwise
            default -> new AffineTransform();
        };
    }

    /** Scales so the longest side is {@code maxSide}; never upscales. Halving steps keep downscales sharp. */
    static BufferedImage fit(BufferedImage src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxSide) {
            return src;
        }
        double scale = (double) maxSide / longest;
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage current = src;
        int cw = w;
        int ch = h;
        while (cw / 2 >= tw && ch / 2 >= th) {
            cw /= 2;
            ch /= 2;
            current = resize(current, cw, ch, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return cw == tw && ch == th ? current : resize(current, tw, th, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h, Object interpolation) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    static byte[] encodeJpeg(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(out);
            // No IIOMetadata: the writer emits only a bare JFIF header.
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_IMAGE);
    }
}
