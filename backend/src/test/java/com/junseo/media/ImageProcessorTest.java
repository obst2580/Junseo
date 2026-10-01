package com.junseo.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.media.ImageProcessor.ProcessedImage;
import com.junseo.support.TestImages;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void landscapeIsScaledToFullAndThumbLongestSides() {
        ProcessedImage out = processor.process(TestImages.jpeg(3000, 2000));
        assertSize(out.full(), 1440, 960);
        assertSize(out.thumb(), 540, 360);
    }

    @Test
    void portraitIsScaledByItsHeight() {
        ProcessedImage out = processor.process(TestImages.jpeg(1000, 3000));
        assertSize(out.full(), 480, 1440);
        assertSize(out.thumb(), 180, 540);
    }

    @Test
    void smallImagesAreNeverUpscaled() {
        ProcessedImage out = processor.process(TestImages.jpeg(300, 200));
        assertSize(out.full(), 300, 200);
        assertSize(out.thumb(), 300, 200);
        ProcessedImage mid = processor.process(TestImages.jpeg(1000, 800));
        assertSize(mid.full(), 1000, 800);
        assertSize(mid.thumb(), 540, 432);
    }

    @Test
    void outputCarriesNoMetadata() {
        ProcessedImage out = processor.process(TestImages.withExif(TestImages.jpeg(800, 600), 1));
        for (byte[] jpeg : new byte[][] {out.full(), out.thumb()}) {
            assertThat(TestImages.jpegMarkers(jpeg)).doesNotContain(0xE1, 0xE2, 0xED, 0xFE);
            assertThat(TestImages.contains(jpeg, "Exif")).isFalse();
            assertThat(TestImages.contains(jpeg, TestImages.EXIF_SECRET)).isFalse();
        }
    }

    @Test
    void exifOrientationIsAppliedBeforeItIsDropped() {
        // Orientation 6 = rotate 90° clockwise: the red left half ends up on top.
        BufferedImage rotated = TestImages.read(processor.process(TestImages.withExif(TestImages.jpeg(400, 200), 6)).full());
        assertThat(new int[] {rotated.getWidth(), rotated.getHeight()}).containsExactly(200, 400);
        assertReddish(rotated.getRGB(100, 50));
        assertBluish(rotated.getRGB(100, 350));

        BufferedImage upsideDown = TestImages.read(processor.process(TestImages.withExif(TestImages.jpeg(400, 200), 3)).full());
        assertThat(new int[] {upsideDown.getWidth(), upsideDown.getHeight()}).containsExactly(400, 200);
        assertBluish(upsideDown.getRGB(50, 100));
        assertReddish(upsideDown.getRGB(350, 100));
    }

    @Test
    void transparentPngIsFlattenedOntoWhite() {
        BufferedImage full = TestImages.read(processor.process(TestImages.pngWithAlpha(200, 100)).full());
        int pixel = full.getRGB(20, 50);
        assertThat(List.of((pixel >> 16) & 0xFF, (pixel >> 8) & 0xFF, pixel & 0xFF)).allMatch(c -> c > 240);
        assertBluish(full.getRGB(180, 50));
    }

    @Test
    void rejectsAnythingThatIsNotAValidJpegOrPng() {
        byte[] truncatedPng = Arrays.copyOf(TestImages.pngWithAlpha(50, 50), 40);
        byte[] oversized = Arrays.copyOf(TestImages.jpeg(50, 50), ImageProcessor.MAX_BYTES + 1);
        for (byte[] bad : new byte[][] {
                new byte[0],
                "plain text".getBytes(StandardCharsets.UTF_8),
                "GIF89a....".getBytes(StandardCharsets.US_ASCII),
                {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3},
                truncatedPng,
                oversized}) {
            assertThatThrownBy(() -> processor.process(bad))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).code())
                    .isEqualTo(ErrorCode.INVALID_IMAGE);
        }
    }

    private static void assertSize(byte[] jpeg, int w, int h) {
        BufferedImage img = TestImages.read(jpeg);
        assertThat(new int[] {img.getWidth(), img.getHeight()}).containsExactly(w, h);
    }

    private static void assertReddish(int rgb) {
        assertThat((rgb >> 16) & 0xFF).isGreaterThan(200);
        assertThat(rgb & 0xFF).isLessThan(60);
    }

    private static void assertBluish(int rgb) {
        assertThat(rgb & 0xFF).isGreaterThan(200);
        assertThat((rgb >> 16) & 0xFF).isLessThan(60);
    }
}
