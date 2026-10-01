package com.junseo.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Newest-first page with an opaque cursor that encodes the last id returned. */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public static final int MAX_LIMIT = 100;

    public static int clampLimit(int requested) {
        return Math.clamp(requested, 1, MAX_LIMIT);
    }

    /** Exclusive upper bound for ids on the requested page. */
    public static long before(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            if (!raw.startsWith("v1:")) {
                throw new IllegalArgumentException();
            }
            return Long.parseLong(raw.substring(3));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "cursor 값이 올바르지 않아요.");
        }
    }

    /**
     * Builds a page from a query that fetched {@code limit + 1} rows; the extra row only signals that
     * another page exists.
     */
    public static <E, T> CursorPage<T> of(
            List<E> rows, int limit, ToLongFunction<E> id, Function<List<E>, List<T>> mapper) {
        boolean more = rows.size() > limit;
        List<E> page = more ? rows.subList(0, limit) : rows;
        String next = more ? encode(id.applyAsLong(page.getLast())) : null;
        return new CursorPage<>(mapper.apply(page), next);
    }

    static String encode(long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(("v1:" + id).getBytes(StandardCharsets.UTF_8));
    }
}
