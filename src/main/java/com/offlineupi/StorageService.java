package com.offlineupi;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StorageService {
    private static final Path DB_PATH = Paths.get("data", "offline-upi.db");
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH.toString();

    AppData load() {
        AppData appData = new AppData();
        try {
            openDatabase();
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                createTables(connection);
                loadUsers(connection, appData);
                loadTransactions(connection, appData);
                loadReceipts(connection, appData);
            }
        } catch (ClassNotFoundException ex) {
            System.out.println("SQLite JDBC driver not found. Add sqlite-jdbc.jar to the classpath.");
        } catch (Exception ex) {
            System.out.println("Could not load SQLite data: " + ex.getMessage());
        }
        return appData;
    }

    void save(AppData appData) {
        try {
            openDatabase();
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                createTables(connection);
                connection.setAutoCommit(false);
                try {
                    clearTables(connection);
                    saveUsers(connection, appData);
                    saveTransactions(connection, appData);
                    saveReceipts(connection, appData);
                    connection.commit();
                } catch (SQLException ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (ClassNotFoundException ex) {
            System.out.println("SQLite JDBC driver not found. Add sqlite-jdbc.jar to the classpath.");
        } catch (Exception ex) {
            System.out.println("Could not save SQLite data: " + ex.getMessage());
        }
    }

    private void openDatabase() throws Exception {
        Files.createDirectories(DB_PATH.getParent());
        Class.forName("org.sqlite.JDBC");
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS users ("
                            + "upi_id TEXT PRIMARY KEY,"
                            + "name TEXT NOT NULL,"
                            + "mobile TEXT NOT NULL UNIQUE,"
                            + "pin TEXT NOT NULL,"
                            + "balance TEXT NOT NULL"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS transactions ("
                            + "row_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "account_upi_id TEXT NOT NULL,"
                            + "transaction_id TEXT NOT NULL,"
                            + "from_upi_id TEXT NOT NULL,"
                            + "to_upi_id TEXT NOT NULL,"
                            + "amount TEXT NOT NULL,"
                            + "type TEXT NOT NULL,"
                            + "note TEXT NOT NULL,"
                            + "created_at TEXT NOT NULL"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS packet_receipts ("
                            + "request_id TEXT PRIMARY KEY,"
                            + "fingerprint TEXT NOT NULL,"
                            + "success INTEGER NOT NULL,"
                            + "message TEXT NOT NULL,"
                            + "created_at TEXT NOT NULL"
                            + ")"
            );
        }
    }

    private void loadUsers(Connection connection, AppData appData) throws SQLException {
        String sql = "SELECT name, mobile, upi_id, pin, balance FROM users ORDER BY name";
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                UserAccount user = new UserAccount(
                        results.getString("name"),
                        results.getString("mobile"),
                        results.getString("upi_id"),
                        results.getString("pin")
                );
                user.credit(new BigDecimal(results.getString("balance")));
                appData.users().add(user);
            }
        }
    }

    private void loadTransactions(Connection connection, AppData appData) throws SQLException {
        String sql = "SELECT account_upi_id, transaction_id, from_upi_id, to_upi_id, amount, type, note, created_at "
                + "FROM transactions ORDER BY row_id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                Optional<UserAccount> account = appData.findUser(results.getString("account_upi_id"));
                if (account.isPresent()) {
                    account.get().transactions().add(new Transaction(
                            results.getString("transaction_id"),
                            results.getString("from_upi_id"),
                            results.getString("to_upi_id"),
                            new BigDecimal(results.getString("amount")),
                            TransactionType.valueOf(results.getString("type")),
                            results.getString("note"),
                            LocalDateTime.parse(results.getString("created_at"))
                    ));
                }
            }
        }
    }

    private void loadReceipts(Connection connection, AppData appData) throws SQLException {
        String sql = "SELECT request_id, fingerprint, success, message, created_at FROM packet_receipts";
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                appData.saveReceipt(new PacketReceipt(
                        results.getString("request_id"),
                        results.getString("fingerprint"),
                        results.getInt("success") == 1,
                        results.getString("message"),
                        LocalDateTime.parse(results.getString("created_at"))
                ));
            }
        }
    }

    private void clearTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM packet_receipts");
            statement.executeUpdate("DELETE FROM transactions");
            statement.executeUpdate("DELETE FROM users");
        }
    }

    private void saveUsers(Connection connection, AppData appData) throws SQLException {
        String sql = "INSERT INTO users (upi_id, name, mobile, pin, balance) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (UserAccount user : appData.users()) {
                statement.setString(1, user.upiId());
                statement.setString(2, user.name());
                statement.setString(3, user.mobile());
                statement.setString(4, user.pin());
                statement.setString(5, user.balance().toPlainString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void saveTransactions(Connection connection, AppData appData) throws SQLException {
        String sql = "INSERT INTO transactions "
                + "(account_upi_id, transaction_id, from_upi_id, to_upi_id, amount, type, note, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (UserAccount user : appData.users()) {
                for (Transaction transaction : user.transactions()) {
                    statement.setString(1, user.upiId());
                    statement.setString(2, transaction.getId());
                    statement.setString(3, transaction.getFromUpiId());
                    statement.setString(4, transaction.getToUpiId());
                    statement.setString(5, transaction.getAmount().toPlainString());
                    statement.setString(6, transaction.getType().name());
                    statement.setString(7, transaction.getNote());
                    statement.setString(8, transaction.getCreatedAt().toString());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void saveReceipts(Connection connection, AppData appData) throws SQLException {
        String sql = "INSERT INTO packet_receipts (request_id, fingerprint, success, message, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (PacketReceipt receipt : appData.receipts()) {
                statement.setString(1, receipt.getRequestId());
                statement.setString(2, receipt.getFingerprint());
                statement.setInt(3, receipt.isSuccess() ? 1 : 0);
                statement.setString(4, receipt.getMessage());
                statement.setString(5, receipt.getCreatedAt().toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}

final class AppData implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final List<UserAccount> users = new ArrayList<>();
    private Map<String, PacketReceipt> processedPacketReceipts = new HashMap<>();

    List<UserAccount> users() {
        return users;
    }

    Optional<UserAccount> findUser(String upiId) {
        return users.stream()
                .filter(user -> user.upiId().equalsIgnoreCase(upiId))
                .findFirst();
    }

    Optional<PacketReceipt> receiptFor(String requestId) {
        ensureReceiptMap();
        return Optional.ofNullable(processedPacketReceipts.get(requestId));
    }

    void saveReceipt(PacketReceipt receipt) {
        ensureReceiptMap();
        processedPacketReceipts.put(receipt.getRequestId(), receipt);
    }

    List<PacketReceipt> receipts() {
        ensureReceiptMap();
        return new ArrayList<>(processedPacketReceipts.values());
    }

    private void ensureReceiptMap() {
        if (processedPacketReceipts == null) {
            processedPacketReceipts = new HashMap<>();
        }
    }
}

final class PacketReceipt implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final String requestId;
    private final String fingerprint;
    private final boolean success;
    private final String message;
    private final java.time.LocalDateTime createdAt;

    PacketReceipt(String requestId, String fingerprint, boolean success, String message,
            java.time.LocalDateTime createdAt) {
        this.requestId = requestId;
        this.fingerprint = fingerprint;
        this.success = success;
        this.message = message;
        this.createdAt = createdAt;
    }

    String getRequestId() {
        return requestId;
    }

    String getFingerprint() {
        return fingerprint;
    }

    boolean isSuccess() {
        return success;
    }

    String getMessage() {
        return message;
    }

    java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
