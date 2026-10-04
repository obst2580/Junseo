package com.junseo.media;

import java.util.Optional;
import org.springframework.core.io.Resource;

/** Blob store for processed images, keyed like {@code 301/thumb.jpg}. Local disk today, S3 later. */
public interface MediaStorage {

    void put(String key, byte[] bytes);

    Optional<Resource> get(String key);

    void delete(String key);

    static String key(long momentId, Variant variant) {
        return momentId + "/" + variant.id() + ".jpg";
    }

    enum Variant {
        FULL("full"),
        THUMB("thumb");

        private final String id;

        Variant(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<Variant> parse(String id) {
            for (Variant v : values()) {
                if (v.id.equals(id)) {
                    return Optional.of(v);
                }
            }
            return Optional.empty();
        }
    }
}
