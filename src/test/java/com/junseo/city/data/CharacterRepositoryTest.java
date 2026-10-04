package com.junseo.city.data;

import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Job;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SQLite 로 실제 SQL 을 돌려 보는 테스트. */
class CharacterRepositoryTest {
    @TempDir
    Path dir;

    private Database db;
    private final CharacterRepository repo = new CharacterRepository(ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void open() {
        db = Database.sqlite(dir.resolve("city.db").toString(), Logger.getLogger("test"));
        db.migrate();
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    void createAssignsSequentialCitizenIds() {
        long day = LocalDate.of(2026, 10, 4).atStartOfDay(ZoneOffset.ofHours(9)).toEpochSecond() * 1000 + 3_600_000;
        CharacterData a = db.submit(c -> repo.create(c, UUID.randomUUID(), "김준서", 500, 2000, day)).join();
        CharacterData b = db.submit(c -> repo.create(c, UUID.randomUUID(), "Minho", 500, 2000, day)).join();
        assertEquals("261004-1000001", a.citizenId());
        assertEquals("261004-1000002", b.citizenId());
        assertEquals(500, a.cash());
        assertEquals(2000, a.bank());
    }

    @Test
    void namesAreUniqueIgnoringCase() {
        db.submit(c -> repo.create(c, UUID.randomUUID(), "Junseo", 0, 0, 1L)).join();
        assertTrue(db.submit(c -> repo.nameTaken(c, "junseo")).join());
        assertFalse(db.submit(c -> repo.nameTaken(c, "준서")).join());
        CompletionException e = assertThrows(CompletionException.class,
                () -> db.submit(c -> repo.create(c, UUID.randomUUID(), "Junseo", 0, 0, 2L)).join());
        assertInstanceOf(SQLException.class, e.getCause());
    }

    @Test
    void saveAndLoadRoundTrip() {
        UUID uuid = UUID.randomUUID();
        CharacterData data = db.submit(c -> repo.create(c, uuid, "박경찰", 100, 200, 1L)).join();
        data.setCash(1234);
        data.setBank(5678);
        data.setJob(Job.POLICE, 3);
        data.setJailSeconds(45);
        CharacterData.Snapshot snapshot = data.snapshot();
        db.submit(c -> {
            repo.save(c, snapshot, 99L);
            return null;
        }).join();

        Optional<CharacterData> loaded = db.submit(c -> repo.findByUuid(c, uuid)).join();
        assertTrue(loaded.isPresent());
        CharacterData d = loaded.get();
        assertEquals("박경찰", d.name());
        assertEquals(data.citizenId(), d.citizenId());
        assertEquals(1234, d.cash());
        assertEquals(5678, d.bank());
        assertEquals(Job.POLICE, d.job());
        assertEquals(3, d.jobGrade());
        assertEquals(45, d.jailSeconds());
        assertFalse(d.isDirty());
        assertTrue(db.submit(c -> repo.findByUuid(c, UUID.randomUUID())).join().isEmpty());
    }

    @Test
    void moneyLogNewestFirst() {
        CharacterData data = db.submit(c -> repo.create(c, UUID.randomUUID(), "이은행", 0, 0, 1L)).join();
        db.submit(c -> {
            repo.logMoney(c, data.citizenId(), 1000, "bank", "월급", null, 10L);
            repo.logMoney(c, data.citizenId(), -300, "bank", "송금", "261004-1000009", 20L);
            return null;
        }).join();
        List<CharacterRepository.MoneyEntry> entries = db.submit(c -> repo.recentMoney(c, data.citizenId(), 10)).join();
        assertEquals(2, entries.size());
        assertEquals(-300, entries.get(0).amount());
        assertEquals("송금", entries.get(0).reason());
        assertEquals(1000, entries.get(1).amount());
    }
}
