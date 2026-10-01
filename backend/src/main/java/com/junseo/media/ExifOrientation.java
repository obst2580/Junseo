package com.junseo.media;

/** Reads only the EXIF Orientation tag (0x0112) from a JPEG's APP1 segment; anything malformed means "1". */
final class ExifOrientation {

    private static final int TAG_ORIENTATION = 0x0112;

    private ExifOrientation() {}

    static int read(byte[] d) {
        int i = 2; // after SOI
        while (i + 4 <= d.length) {
            if ((d[i] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = d[i + 1] & 0xFF;
            if (marker == 0xFF) { // fill byte
                i++;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) { // EOI or start of scan: no more headers
                return 1;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            int length = u16(d, i + 2, false);
            int start = i + 4;
            int end = i + 2 + length;
            if (length < 2 || end > d.length) {
                return 1;
            }
            if (marker == 0xE1 && isExifHeader(d, start, end)) {
                return fromTiff(d, start + 6, end);
            }
            i = end;
        }
        return 1;
    }

    private static boolean isExifHeader(byte[] d, int start, int end) {
        return end - start >= 14
                && d[start] == 'E' && d[start + 1] == 'x' && d[start + 2] == 'i' && d[start + 3] == 'f'
                && d[start + 4] == 0 && d[start + 5] == 0;
    }

    private static int fromTiff(byte[] d, int base, int end) {
        boolean little;
        if (d[base] == 'I' && d[base + 1] == 'I') {
            little = true;
        } else if (d[base] == 'M' && d[base + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        long ifd = base + (u32(d, base + 4, little));
        if (ifd + 2 > end) {
            return 1;
        }
        int entries = u16(d, (int) ifd, little);
        for (int k = 0; k < entries; k++) {
            long entry = ifd + 2 + 12L * k;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(d, (int) entry, little) == TAG_ORIENTATION) {
                int value = u16(d, (int) entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] d, int at, boolean little) {
        int a = d[at] & 0xFF;
        int b = d[at + 1] & 0xFF;
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static long u32(byte[] d, int at, boolean little) {
        long hi = u16(d, little ? at + 2 : at, little);
        long lo = u16(d, little ? at : at + 2, little);
        return (hi << 16) | lo;
    }
}
