package com.junseo.city.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 데이터베이스 연결과 비동기 실행.
 * - SQLite: 개발·소규모 (파일 하나, 쓰기 스레드 1개)
 * - MySQL/MariaDB: 운영 (스레드마다 연결 1개 = 작은 연결 풀)
 * 게임 스레드는 절대 DB 를 기다리지 않습니다. 모든 작업은 {@link #submit} 로 보냅니다.
 */
public final class Database implements AutoCloseable {

    public enum Dialect { SQLITE, MYSQL }

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Dialect dialect;
    private final String url;
    private final String user;
    private final String password;
    private final Logger logger;
    private final ExecutorService executor;
    private final ThreadLocal<Connection> connections = new ThreadLocal<>();
    private final List<Connection> allConnections = new ArrayList<>();

    public Database(Dialect dialect, String url, String user, String password, int threads, Logger logger) {
        this.dialect = dialect;
        this.url = url;
        this.user = user;
        this.password = password;
        this.logger = logger;
        loadDriver(dialect == Dialect.SQLITE ? "org.sqlite.JDBC" : "com.mysql.cj.jdbc.Driver");
        int poolSize = dialect == Dialect.SQLITE ? 1 : Math.max(1, threads);
        AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "JunseoCity-DB-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Paper 서버에 들어 있는 JDBC 드라이버를 확실히 등록합니다. */
    private void loadDriver(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            logger.warning("JDBC 드라이버를 찾지 못했어요: " + className + " (서버에 포함되어 있는지 확인)");
        }
    }

    public static Database sqlite(String filePath, Logger logger) {
        return new Database(Dialect.SQLITE, "jdbc:sqlite:" + filePath, null, null, 1, logger);
    }

    public static Database mysql(String host, int port, String database, String user, String password, int threads,
                                 Logger logger) {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        return new Database(Dialect.MYSQL, url, user, password, threads, logger);
    }

    public Dialect dialect() {
        return dialect;
    }

    /** DB 작업을 DB 스레드에서 실행하고 결과를 나중에 돌려줍니다. */
    public <T> CompletableFuture<T> submit(SqlWork<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(work.run(connection()));
            } catch (Throwable e) {
                logger.log(Level.WARNING, "DB 작업 실패", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** 결과가 필요 없는 쓰기. */
    public void execute(SqlWork<?> work) {
        submit(work);
    }

    /** 시작할 때 한 번: 테이블 만들기. 끝날 때까지 기다립니다. */
    public void migrate() {
        submit(c -> {
            try (Statement st = c.createStatement()) {
                String autoId = dialect == Dialect.SQLITE ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
                String text = dialect == Dialect.SQLITE ? "TEXT" : "VARCHAR(64)";
                st.executeUpdate("CREATE TABLE IF NOT EXISTS characters ("
                        + "id " + autoId + ", "
                        + "uuid " + text + " NOT NULL UNIQUE, "
                        + "citizen_id " + text + " UNIQUE, "
                        + "name " + text + " NOT NULL UNIQUE, "
                        + "cash BIGINT NOT NULL DEFAULT 0, "
                        + "bank BIGINT NOT NULL DEFAULT 0, "
                        + "job " + text + " NOT NULL DEFAULT 'citizen', "
                        + "job_grade INT NOT NULL DEFAULT 0, "
                        + "jail_seconds INT NOT NULL DEFAULT 0, "
                        + "created_at BIGINT NOT NULL, "
                        + "last_seen BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS money_log ("
                        + "id " + autoId + ", "
                        + "ts BIGINT NOT NULL, "
                        + "citizen_id " + text + " NOT NULL, "
                        + "amount BIGINT NOT NULL, "
                        + "balance_kind " + text + " NOT NULL, "
                        + "reason " + text + " NOT NULL, "
                        + "other " + text
                        + (dialect == Dialect.MYSQL ? ", INDEX money_log_cid (citizen_id, ts)" : "") + ")");
                if (dialect == Dialect.SQLITE) {
                    st.executeUpdate("CREATE INDEX IF NOT EXISTS money_log_cid ON money_log(citizen_id, ts)");
                }
                // 차: 주인(플레이어 uuid), 차종, 색, 번호판, 상태(garage/out/impound), 마지막 차고
                st.executeUpdate("CREATE TABLE IF NOT EXISTS vehicles ("
                        + "id " + text + " PRIMARY KEY, "
                        + "owner " + text + " NOT NULL, "
                        + "model " + text + " NOT NULL, "
                        + "paint " + text + " NOT NULL, "
                        + "plate " + text + " NOT NULL UNIQUE, "
                        + "health INT NOT NULL DEFAULT 100, "
                        + "state " + text + " NOT NULL DEFAULT 'garage', "
                        + "garage " + (dialect == Dialect.SQLITE ? "TEXT" : "VARCHAR(128)") + ", "
                        + "created_at BIGINT NOT NULL)");
                // 운전면허 (나중에 생긴 칸이라 없으면 더함)
                if (!hasColumn(c, "characters", "license")) {
                    st.executeUpdate("ALTER TABLE characters ADD COLUMN license INT NOT NULL DEFAULT 0");
                }
            }
            return null;
        }).join();
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (java.sql.ResultSet rs = c.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Connection connection() throws SQLException {
        Connection c = connections.get();
        if (c == null || c.isClosed() || (dialect == Dialect.MYSQL && !c.isValid(2))) {
            c = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);
            if (dialect == Dialect.SQLITE) {
                try (Statement st = c.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA synchronous=NORMAL");
                }
            }
            connections.set(c);
            synchronized (allConnections) {
                allConnections.add(c);
            }
        }
        return c;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("DB 작업이 10초 안에 끝나지 않았어요.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (allConnections) {
            for (Connection c : allConnections) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // 끝날 때라 무시
                }
            }
            allConnections.clear();
        }
    }
}
