package com.junseo.template;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.media.MediaStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TemplateService {

    static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");
    private static final Pattern FEATURE = Pattern.compile("[a-z][a-z0-9-]{0,19}");
    private static final int MAX_SIDE = 4096;
    private static final int MAX_SLOTS = 6;

    private final TemplateRepository templates;
    private final MediaStorage storage;
    private final ObjectMapper json;
    private final Clock clock;

    public TemplateService(TemplateRepository templates, MediaStorage storage, ObjectMapper json, Clock clock) {
        this.templates = templates;
        this.storage = storage;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        return templates.findByActiveTrueOrderBySortOrderDescCreatedAtDesc().stream().map(this::view).toList();
    }

    /** Creates or replaces a template. Pictures must match the template size; the overlay must be a PNG. */
    @Transactional
    public TemplateView put(String id, TemplateSpec spec, byte[] background, byte[] overlay) {
        if (id == null || !ID.matcher(id).matches()) {
            throw invalid("템플릿 id 는 영문 소문자 · 숫자 · - 로 40자까지예요.");
        }
        if (spec == null || spec.name() == null || spec.name().isBlank() || spec.name().length() > 30) {
            throw invalid("이름은 1~30자로 적어 주세요.");
        }
        if (spec.width() < 100 || spec.height() < 100 || spec.width() > MAX_SIDE || spec.height() > MAX_SIDE) {
            throw invalid("템플릿 크기는 100~" + MAX_SIDE + "px 이어야 해요.");
        }
        if (spec.backgroundColor() == null || !COLOR.matcher(spec.backgroundColor()).matches()) {
            throw invalid("배경색은 #rrggbb 로 적어 주세요.");
        }
        Set<String> features = new TreeSet<>();
        checkSlots(spec, features);
        if (spec.requires() != null) {
            for (String f : spec.requires()) {
                if (f == null || !FEATURE.matcher(f).matches()) {
                    throw invalid("requires 에는 영문 소문자 기능 이름만 적어 주세요.");
                }
                features.add(f);
            }
        }
        String backgroundExt = imageType(background, "바탕 그림");
        checkSize(background, spec, "바탕 그림");
        boolean hasOverlay = overlay != null && overlay.length > 0;
        if (hasOverlay) {
            if (!"png".equals(imageType(overlay, "앞장 그림"))) {
                throw invalid("앞장 그림은 투명 PNG 여야 해요.");
            }
            checkSize(overlay, spec, "앞장 그림");
            features.add("overlay");
        }

        Template t = templates.findById(id).orElseGet(() -> new Template(id, clock.instant()));
        String backgroundFile = "background." + backgroundExt;
        // 새 그림부터 올리고 기록을 바꾼다 (이전 주소는 ?v= 가 달라서 폰들이 새 그림을 받는다)
        storage.put(key(id, backgroundFile), background);
        if (hasOverlay) {
            storage.put(key(id, "overlay.png"), overlay);
        }
        t.replace(spec, backgroundFile, hasOverlay, json.writeValueAsString(spec.slots()), String.join(" ", features), clock.instant());
        return view(templates.save(t));
    }

    /** Takes a template off the list. Its pictures stay, so phones that already have it don't break. */
    @Transactional
    public void hide(String id) {
        Template t = templates.findById(id).orElseThrow(ApiException::notFound);
        t.hide(clock.instant());
    }

    static String key(String id, String file) {
        return "templates/" + id + "/" + file;
    }

    private TemplateView view(Template t) {
        String base = "/media/templates/" + t.getId() + "/";
        String v = "?v=" + t.getVersion();
        List<String> requires = t.getRequires().isBlank() ? List.of() : Arrays.asList(t.getRequires().split(" "));
        return new TemplateView(
                t.getId(),
                t.getName(),
                t.getWidth(),
                t.getHeight(),
                t.getBackgroundColor(),
                base + t.getBackgroundFile() + v,
                t.isHasOverlay() ? base + "overlay.png" + v : null,
                json.readTree(t.getSlots()),
                requires,
                t.getVersion());
    }

    /** Square {x, y, size, radius?} or quad {quad: 4 × [x, y], aspect, grow?, glow?: {grow, blur, wash}}. */
    private static void checkSlots(TemplateSpec spec, Set<String> features) {
        JsonNode slots = spec.slots();
        if (slots == null || !slots.isArray() || slots.isEmpty() || slots.size() > MAX_SLOTS) {
            throw invalid("사진 칸은 1~" + MAX_SLOTS + "개여야 해요.");
        }
        double limit = Math.max(spec.width(), spec.height()) * 1.5;
        for (JsonNode slot : slots) {
            if (!slot.isObject()) {
                throw invalid("사진 칸 모양이 올바르지 않아요.");
            }
            if (slot.has("quad")) {
                JsonNode quad = slot.get("quad");
                if (!quad.isArray() || quad.size() != 4) {
                    throw invalid("quad 는 꼭짓점 4개 [x, y] 예요.");
                }
                for (JsonNode p : quad) {
                    if (!p.isArray() || p.size() != 2 || !inRange(p.get(0), -limit, limit) || !inRange(p.get(1), -limit, limit)) {
                        throw invalid("quad 꼭짓점이 템플릿 밖으로 너무 멀어요.");
                    }
                }
                if (!inRange(slot.get("aspect"), 0.05, 20)) {
                    throw invalid("aspect(판의 가로/세로)를 적어 주세요.");
                }
                if (slot.has("grow") && !inRange(slot.get("grow"), 0, 200)) {
                    throw invalid("grow 는 0~200 이에요.");
                }
                features.add("quad");
                if (slot.has("glow")) {
                    JsonNode glow = slot.get("glow");
                    if (!glow.isObject() || !inRange(glow.get("grow"), 0, 500) || !inRange(glow.get("blur"), 0, 0.5) || !inRange(glow.get("wash"), 0, 1)) {
                        throw invalid("glow 는 {grow, blur, wash} 숫자예요.");
                    }
                    features.add("glow");
                }
            } else {
                if (!inRange(slot.get("x"), -limit, limit) || !inRange(slot.get("y"), -limit, limit) || !inRange(slot.get("size"), 1, limit)) {
                    throw invalid("네모 칸은 x, y, size 숫자예요.");
                }
                if (slot.has("radius") && !inRange(slot.get("radius"), 0, limit)) {
                    throw invalid("radius 가 올바르지 않아요.");
                }
            }
        }
    }

    private static boolean inRange(JsonNode n, double min, double max) {
        return n != null && n.isNumber() && n.asDouble() >= min && n.asDouble() <= max;
    }

    private static String imageType(byte[] bytes, String what) {
        if (bytes == null || bytes.length < 8) {
            throw invalid(what + "을 올려 주세요.");
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        throw invalid(what + "은 JPG 나 PNG 여야 해요.");
    }

    /** Reads only the header (no full decode) and checks the picture is exactly the template size. */
    private static void checkSize(byte[] bytes, TemplateSpec spec, String what) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw invalid(what + "을 읽을 수 없어요.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                if (reader.getWidth(0) != spec.width() || reader.getHeight(0) != spec.height()) {
                    throw invalid(what + " 크기(" + reader.getWidth(0) + "×" + reader.getHeight(0) + ")가 템플릿 크기("
                            + spec.width() + "×" + spec.height() + ")와 달라요.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid(what + "을 읽을 수 없어요.");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
