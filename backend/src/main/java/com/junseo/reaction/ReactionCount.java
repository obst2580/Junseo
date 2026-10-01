package com.junseo.reaction;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ReactionCount(String emoji, long count) {

    /** Total taps per emoji, most first; ties go to the emoji used most recently. */
    public static List<ReactionCount> summarize(List<Reaction> reactions) {
        record Tally(String emoji, long count, Instant last, long lastId) {}
        Map<String, Tally> byEmoji = new HashMap<>();
        for (Reaction r : reactions) {
            byEmoji.merge(
                    r.getEmoji(),
                    new Tally(r.getEmoji(), r.getTaps(), r.getUpdatedAt(), r.getId()),
                    (a, b) -> a.last().isAfter(b.last()) || (a.last().equals(b.last()) && a.lastId() > b.lastId())
                            ? new Tally(a.emoji(), a.count() + b.count(), a.last(), a.lastId())
                            : new Tally(a.emoji(), a.count() + b.count(), b.last(), b.lastId()));
        }
        return byEmoji.values().stream()
                .sorted(Comparator.comparingLong(Tally::count).reversed()
                        .thenComparing(Tally::last, Comparator.reverseOrder())
                        .thenComparing(Tally::lastId, Comparator.reverseOrder()))
                .map(t -> new ReactionCount(t.emoji(), t.count()))
                .toList();
    }
}
