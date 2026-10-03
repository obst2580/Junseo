package com.junseo.template;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A server-delivered photo template: background picture + photo slots (+ optional overlay picture). */
@Entity
@Table(name = "templates")
public class Template {

    @Id
    private String id;

    private String name;
    private int width;
    private int height;
    private String backgroundColor;
    /** {@code background.jpg} or {@code background.png} in the template's media folder. */
    private String backgroundFile;
    private boolean hasOverlay;
    /** JSON array, stored as text and handed to the app as is. */
    private String slots;
    /** Space separated features the app must support, e.g. {@code glow overlay quad}. */
    private String requires;
    private int sortOrder;
    private boolean active;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    protected Template() {}

    public Template(String id, Instant now) {
        this.id = id;
        this.active = true;
        this.version = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Replaces everything about the template and bumps the version (new image URLs). */
    void replace(TemplateSpec spec, String backgroundFile, boolean hasOverlay, String slots, String requires, Instant now) {
        this.name = spec.name();
        this.width = spec.width();
        this.height = spec.height();
        this.backgroundColor = spec.backgroundColor();
        this.backgroundFile = backgroundFile;
        this.hasOverlay = hasOverlay;
        this.slots = slots;
        this.requires = requires;
        this.sortOrder = spec.sortOrder() == null ? 0 : spec.sortOrder();
        this.active = true;
        this.version += 1;
        this.updatedAt = now;
    }

    void hide(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getBackgroundFile() {
        return backgroundFile;
    }

    public boolean isHasOverlay() {
        return hasOverlay;
    }

    public String getSlots() {
        return slots;
    }

    public String getRequires() {
        return requires;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
