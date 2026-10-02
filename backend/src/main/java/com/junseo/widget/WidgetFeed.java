package com.junseo.widget;

import java.util.List;

/** GET /api/widget/feed: the photos a widget pages through, newest first (each item as in WidgetLatest). */
public record WidgetFeed(String version, List<WidgetLatest> items) {}
