package com.junseo.comment;

public final class CommentEvents {

    private CommentEvents() {}

    public record CommentCreated(long commentId, long momentId, long authorId, String text) {}

    public record CommentDeleted(long momentId, long deletedBy) {}
}
