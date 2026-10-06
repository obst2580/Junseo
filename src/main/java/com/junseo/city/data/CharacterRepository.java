package com.junseo.city.data;

import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Identity;
import com.junseo.city.logic.Job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** characters / money_log 테이블 읽기·쓰기 (순수 JDBC, 서버 API 없음 → 테스트 가능). */
public final class CharacterRepository {

    public record MoneyEntry(long timestamp, long amount, String kind, String reason, String other) {
    }

    private final ZoneId zone;

    public CharacterRepository(ZoneId zone) {
        this.zone = zone;
    }

    public Optional<CharacterData> findByUuid(Connection c, UUID uuid) throws SQLException {
        return findOne(c, "SELECT * FROM characters WHERE uuid = ?", uuid.toString());
    }

    public Optional<CharacterData> findByName(Connection c, String name) throws SQLException {
        return findOne(c, "SELECT * FROM characters WHERE LOWER(name) = ?", name.toLowerCase(Locale.ROOT));
    }

    public Optional<CharacterData> findByCitizenId(Connection c, String citizenId) throws SQLException {
        return findOne(c, "SELECT * FROM characters WHERE citizen_id = ?", citizenId);
    }

    public boolean nameTaken(Connection c, String name) throws SQLException {
        return findByName(c, name).isPresent();
    }

    /** 새 캐릭터를 만들고 주민번호를 붙입니다. */
    public CharacterData create(Connection c, UUID uuid, String name, long cash, long bank, long now) throws SQLException {
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            long id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO characters (uuid, name, cash, bank, job, job_grade, jail_seconds, created_at, last_seen) "
                            + "VALUES (?, ?, ?, ?, 'citizen', 0, 0, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, cash);
                ps.setLong(4, bank);
                ps.setLong(5, now);
                ps.setLong(6, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("생성된 id 를 받지 못했어요");
                    }
                    id = keys.getLong(1);
                }
            }
            LocalDate day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            String citizenId = Identity.citizenId(day, id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE characters SET citizen_id = ? WHERE id = ?")) {
                ps.setString(1, citizenId);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            c.commit();
            CharacterData data = new CharacterData(id, uuid, citizenId, name, now);
            data.setCash(cash);
            data.setBank(bank);
            data.markClean();
            return data;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    public void save(Connection c, CharacterData.Snapshot s, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE characters SET cash = ?, bank = ?, job = ?, job_grade = ?, jail_seconds = ?, license = ?, last_seen = ? WHERE id = ?")) {
            ps.setLong(1, s.cash());
            ps.setLong(2, s.bank());
            ps.setString(3, s.job());
            ps.setInt(4, s.jobGrade());
            ps.setInt(5, s.jailSeconds());
            ps.setInt(6, s.license() ? 1 : 0);
            ps.setLong(7, now);
            ps.setLong(8, s.id());
            ps.executeUpdate();
        }
    }

    public void logMoney(Connection c, String citizenId, long amount, String kind, String reason, String other, long now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO money_log (ts, citizen_id, amount, balance_kind, reason, other) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, now);
            ps.setString(2, citizenId);
            ps.setLong(3, amount);
            ps.setString(4, kind);
            ps.setString(5, reason);
            ps.setString(6, other);
            ps.executeUpdate();
        }
    }

    public List<MoneyEntry> recentMoney(Connection c, String citizenId, int limit) throws SQLException {
        List<MoneyEntry> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT ts, amount, balance_kind, reason, other FROM money_log WHERE citizen_id = ? ORDER BY ts DESC, id DESC LIMIT ?")) {
            ps.setString(1, citizenId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MoneyEntry(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return out;
    }

    private Optional<CharacterData> findOne(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                CharacterData data = new CharacterData(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                        rs.getString("citizen_id"), rs.getString("name"), rs.getLong("created_at"));
                data.setCash(rs.getLong("cash"));
                data.setBank(rs.getLong("bank"));
                Job job = Job.parse(rs.getString("job"));
                data.setJob(job == null ? Job.CITIZEN : job, rs.getInt("job_grade"));
                data.setJailSeconds(rs.getInt("jail_seconds"));
                data.setLicense(rs.getInt("license") != 0);
                data.markClean();
                return Optional.of(data);
            }
        }
    }
}
