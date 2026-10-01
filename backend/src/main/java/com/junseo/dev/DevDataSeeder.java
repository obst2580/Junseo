package com.junseo.dev;

import com.junseo.chat.Message;
import com.junseo.chat.MessageRepository;
import com.junseo.comment.Comment;
import com.junseo.comment.CommentRepository;
import com.junseo.friend.Friendship;
import com.junseo.friend.FriendshipRepository;
import com.junseo.media.ImageProcessor;
import com.junseo.media.MediaStorage;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.moment.Moment;
import com.junseo.moment.MomentRepository;
import com.junseo.reaction.Reaction;
import com.junseo.reaction.ReactionRepository;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserService;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds a demo account with friends, photos, reactions, comments and chats so the app and widget have
 * something real to show. Runs once: if the demo account exists, nothing is touched.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    static final String DEMO_EMAIL = "demo@junseo.app";
    static final String PASSWORD = "password123!";

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private record Person(String key, String email, String name) {}

    private record Photo(String sender, Duration ago, Color from, Color to, String title, String caption) {}

    private record Chat(User from, User to, Moment moment, String text, Instant at, boolean read) {}

    private final UserRepository users;
    private final UserService userService;
    private final FriendshipRepository friendships;
    private final MomentRepository moments;
    private final ReactionRepository reactions;
    private final CommentRepository comments;
    private final MessageRepository messages;
    private final PasswordEncoder passwordEncoder;
    private final ImageProcessor imageProcessor;
    private final MediaStorage storage;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DevDataSeeder(
            UserRepository users,
            UserService userService,
            FriendshipRepository friendships,
            MomentRepository moments,
            ReactionRepository reactions,
            CommentRepository comments,
            MessageRepository messages,
            PasswordEncoder passwordEncoder,
            ImageProcessor imageProcessor,
            MediaStorage storage,
            TransactionTemplate tx,
            Clock clock) {
        this.users = users;
        this.userService = userService;
        this.friendships = friendships;
        this.moments = moments;
        this.reactions = reactions;
        this.comments = comments;
        this.messages = messages;
        this.passwordEncoder = passwordEncoder;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(DEMO_EMAIL)) {
            log.info("Dev seed already present ({} / {})", DEMO_EMAIL, PASSWORD);
            return;
        }
        tx.executeWithoutResult(status -> seed(clock.instant()));
        log.info("Seeded dev data. Log in as {} / {}", DEMO_EMAIL, PASSWORD);
    }

    private void seed(Instant now) {
        String hash = passwordEncoder.encode(PASSWORD);
        Map<String, User> u = new LinkedHashMap<>();
        for (Person p : List.of(
                new Person("demo", DEMO_EMAIL, "준서"),
                new Person("minji", "minji@junseo.app", "민지"),
                new Person("jiwoo", "jiwoo@junseo.app", "지우"),
                new Person("seoyeon", "seoyeon@junseo.app", "서연"),
                new Person("hajun", "hajun@junseo.app", "하준"),
                new Person("doyun", "doyun@junseo.app", "도윤"))) {
            User user = new User(p.email(), hash, p.name(), userService.newInviteCode(), now.minus(Duration.ofDays(14)));
            u.put(p.key(), users.saveAndFlush(user));
        }

        Instant friendsSince = now.minus(Duration.ofDays(10));
        for (String[] pair : new String[][] {
                {"demo", "minji"}, {"demo", "jiwoo"}, {"demo", "seoyeon"}, {"demo", "hajun"}, {"demo", "doyun"},
                {"minji", "jiwoo"}, {"minji", "seoyeon"}, {"jiwoo", "seoyeon"}, {"hajun", "doyun"}}) {
            long a = u.get(pair[0]).getId();
            long b = u.get(pair[1]).getId();
            friendships.save(new Friendship(a, b, friendsSince));
            friendships.save(new Friendship(b, a, friendsSince));
        }
        friendships.flush();

        // Inserted oldest first so ids follow time (feeds and the widget order by id).
        List<Photo> photos = new ArrayList<>(List.of(
                new Photo("hajun", Duration.ofHours(86), new Color(0x1E3C72), new Color(0x2A5298), "HAJUN", "night court"),
                new Photo("doyun", Duration.ofHours(79), new Color(0x134E5E), new Color(0x71B280), "DOYUN", "morning hike"),
                new Photo("demo", Duration.ofHours(72), new Color(0xF7971E), new Color(0xFFD200), "JUNSEO", "my cat, nabi"),
                new Photo("seoyeon", Duration.ofHours(64), new Color(0xDA4453), new Color(0x89216B), "SEOYEON", "concert!!"),
                new Photo("jiwoo", Duration.ofHours(55), new Color(0x00B4DB), new Color(0x0083B0), "JIWOO", "beach day"),
                new Photo("minji", Duration.ofHours(49), new Color(0xEECDA3), new Color(0xEF629F), "MINJI", "cafe in seongsu"),
                new Photo("doyun", Duration.ofHours(33), new Color(0x3A1C71), new Color(0xD76D77), "DOYUN", "ramen time"),
                new Photo("hajun", Duration.ofHours(27), new Color(0x56AB2F), new Color(0xA8E063), "HAJUN", "new sneakers"),
                new Photo("demo", Duration.ofHours(21), new Color(0x4568DC), new Color(0xB06AB3), "JUNSEO", "studying late"),
                new Photo("seoyeon", Duration.ofHours(13), new Color(0xFF5F6D), new Color(0xFFC371), "SEOYEON", "baking day"),
                new Photo("jiwoo", Duration.ofHours(7), new Color(0x11998E), new Color(0x38EF7D), "JIWOO", "picnic"),
                new Photo("minji", Duration.ofHours(4), new Color(0x8E2DE2), new Color(0x4A00E0), "MINJI", "bookstore"),
                new Photo("jiwoo", Duration.ofMinutes(95), new Color(0xFC5C7D), new Color(0x6A82FB), "JIWOO", "puppy cafe"),
                new Photo("minji", Duration.ofMinutes(25), new Color(0xFF7E5F), new Color(0x2B5876), "MINJI", "han river sunset")));
        photos.sort(Comparator.comparing(Photo::ago).reversed());

        List<Moment> posted = new ArrayList<>();
        int seed = 7;
        for (Photo photo : photos) {
            User sender = u.get(photo.sender());
            Moment moment = moments.save(new Moment(sender.getId(), now.minus(photo.ago())));
            moments.snapshotRecipients(moment.getId(), sender.getId());
            ImageProcessor.ProcessedImage image = imageProcessor.process(artwork(seed++, photo));
            storage.put(MediaStorage.key(moment.getId(), Variant.FULL), image.full());
            storage.put(MediaStorage.key(moment.getId(), Variant.THUMB), image.thumb());
            posted.add(moment);
        }
        Moment catPhoto = posted.get(2);
        Moment cafePhoto = posted.get(5);
        Moment studyPhoto = posted.get(8);
        Moment picnicPhoto = posted.get(10);
        Moment puppyPhoto = posted.get(12);
        Moment sunsetPhoto = posted.getLast();

        react(catPhoto, u.get("minji"), "😍", 20);
        react(catPhoto, u.get("jiwoo"), "😍", 45);
        react(catPhoto, u.get("hajun"), "🔥", 90);
        comment(catPhoto, u.get("seoyeon"), "나비 너무 귀여워ㅠㅠ", 30);
        comment(catPhoto, u.get("demo"), "오늘도 내 자리 뺏김 ㅋㅋ", 50);
        react(cafePhoto, u.get("demo"), "☕", 15);
        react(cafePhoto, u.get("jiwoo"), "😋", 40);
        comment(cafePhoto, u.get("jiwoo"), "여기 크로플 맛있어?", 25);
        react(studyPhoto, u.get("doyun"), "💪", 30);
        react(studyPhoto, u.get("minji"), "💪", 60);
        comment(studyPhoto, u.get("doyun"), "시험 화이팅!!", 35);
        react(picnicPhoto, u.get("demo"), "🧺", 10);
        react(puppyPhoto, u.get("demo"), "🥹", 12);
        react(puppyPhoto, u.get("seoyeon"), "😍", 30);
        comment(puppyPhoto, u.get("minji"), "다음엔 나도 데려가", 20);

        // The newest friend photo is what demo's widget shows: give it a real-looking stack.
        react(sunsetPhoto, u.get("jiwoo"), "😍", 3);
        react(sunsetPhoto, u.get("seoyeon"), "🔥", 6);
        react(sunsetPhoto, u.get("demo"), "🔥", 9);
        comment(sunsetPhoto, u.get("jiwoo"), "대박 여기 어디야?", 4);
        comment(sunsetPhoto, u.get("seoyeon"), "노을 미쳤다 🌅", 8);
        comment(sunsetPhoto, u.get("minji"), "한강! 다들 나와 ㅎㅎ", 12);

        User demo = u.get("demo");
        User minji = u.get("minji");
        User jiwoo = u.get("jiwoo");
        User hajun = u.get("hajun");
        Instant cafeAt = cafePhoto.getCreatedAt();
        Instant catAt = catPhoto.getCreatedAt();
        List<Chat> chats = new ArrayList<>(List.of(
                new Chat(demo, minji, cafePhoto, "이거 어디 카페야? 나도 가보고 싶다", cafeAt.plus(Duration.ofMinutes(30)), true),
                new Chat(minji, demo, null, "성수동! 다음에 같이 가자 ☕", cafeAt.plus(Duration.ofMinutes(42)), true),
                new Chat(demo, minji, null, "좋아 이번 주말 어때?", cafeAt.plus(Duration.ofMinutes(50)), true),
                new Chat(minji, demo, null, "콜 🙌 토요일 2시!", now.minus(Duration.ofMinutes(40)), false),
                new Chat(jiwoo, demo, catPhoto, "나비 너무 귀엽다ㅠㅠ", catAt.plus(Duration.ofMinutes(15)), true),
                new Chat(demo, jiwoo, null, "우리 집 막내야 ㅋㅋ", catAt.plus(Duration.ofMinutes(20)), true),
                new Chat(jiwoo, demo, null, "다음에 놀러 가서 보여줘!", now.minus(Duration.ofHours(2)), false),
                new Chat(hajun, demo, null, "내일 농구 ㄱ?", now.minus(Duration.ofHours(3)), false)));
        // Ids must follow time: conversation order and thread paging are by id.
        chats.sort(Comparator.comparing(Chat::at));
        chats.forEach(this::message);
    }

    private void react(Moment moment, User user, String emoji, int minutesAfter) {
        reactions.save(new Reaction(moment.getId(), user.getId(), emoji, moment.getCreatedAt().plus(Duration.ofMinutes(minutesAfter))));
    }

    private void comment(Moment moment, User author, String text, int minutesAfter) {
        comments.save(new Comment(moment.getId(), author.getId(), text, moment.getCreatedAt().plus(Duration.ofMinutes(minutesAfter))));
    }

    private void message(Chat c) {
        Message message = new Message(c.from().getId(), c.to().getId(), c.moment() == null ? null : c.moment().getId(), c.text(), c.at());
        if (c.read()) {
            message.markRead(c.at().plus(Duration.ofMinutes(3)));
        }
        messages.save(message);
    }

    /** A colorful generated "photo" (no binary assets in the repo); text is ASCII because the image has no CJK font. */
    static byte[] artwork(int seed, Photo photo) {
        int w = 1080;
        int h = 1440;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, photo.from(), w, h, photo.to()));
            g.fillRect(0, 0, w, h);
            Random random = new Random(seed);
            for (int i = 0; i < 9; i++) {
                int r = 80 + random.nextInt(260);
                g.setColor(new Color(255, 255, 255, 25 + random.nextInt(60)));
                g.fillOval(random.nextInt(w) - r / 2, random.nextInt(h) - r / 2, r, r);
            }
            g.setStroke(new BasicStroke(10f));
            g.setColor(new Color(255, 255, 255, 120));
            for (int i = 0; i < 4; i++) {
                int y = 200 + random.nextInt(h - 400);
                g.drawLine(0, y, w, y - 300 + random.nextInt(600));
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 150));
            int titleWidth = g.getFontMetrics().stringWidth(photo.title());
            g.drawString(photo.title(), (w - titleWidth) / 2, h / 2);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 60));
            int captionWidth = g.getFontMetrics().stringWidth(photo.caption());
            g.drawString(photo.caption(), (w - captionWidth) / 2, h / 2 + 110);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
