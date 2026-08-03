package com.offlineupi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

public class Main {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final PaymentService PAYMENT_SERVICE = new PaymentService(new StorageService());
    private static PaymentPacket lastPaymentPacket;

    public static void main(String[] args) {
        PAYMENT_SERVICE.load();
        System.out.println("Offline UPI Simulator");

        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose option: ");

            switch (readLine()) {
                case "1":
                    register();
                    break;
                case "2":
                    login();
                    break;
                case "3":
                    PAYMENT_SERVICE.save();
                    running = false;
                    System.out.println("Goodbye.");
                    break;
                default:
                    System.out.println("Invalid option.");
                    break;
            }
        }
    }

    private static void register() {
        System.out.println();
        System.out.println("Register new user");
        System.out.print("Full name: ");
        String name = readLine();
        System.out.print("10-digit mobile number: ");
        String mobile = readLine();
        System.out.print("Choose UPI ID, example souvik@offline: ");
        String upiId = readLine().toLowerCase();
        System.out.print("Create 4-digit UPI PIN: ");
        String pin = readLine();

        OperationResult result = PAYMENT_SERVICE.register(name, mobile, upiId, pin);
        System.out.println(result.getMessage());
    }

    private static void login() {
        System.out.println();
        System.out.println("Login");
        System.out.print("UPI ID: ");
        String upiId = readLine().toLowerCase();
        System.out.print("UPI PIN: ");
        String pin = readLine();

        Optional<UserAccount> account = PAYMENT_SERVICE.login(upiId, pin);
        if (!account.isPresent()) {
            System.out.println("Invalid UPI ID or PIN.");
            return;
        }

        userMenu(account.get());
    }

    private static void userMenu(UserAccount account) {
        boolean loggedIn = true;
        while (loggedIn) {
            UserAccount refreshed = PAYMENT_SERVICE.findByUpiId(account.upiId()).orElse(account);
            System.out.println();
            System.out.println("Welcome, " + refreshed.name());
            System.out.println("1. View balance");
            System.out.println("2. Add demo money");
            System.out.println("3. Send money packet");
            System.out.println("4. Retry last payment packet");
            System.out.println("5. View transaction history");
            System.out.println("6. List users");
            System.out.println("7. Logout");
            System.out.print("Choose option: ");

            switch (readLine()) {
                case "1":
                    showBalance(refreshed);
                    break;
                case "2":
                    addMoney(refreshed);
                    break;
                case "3":
                    sendMoneyPacket(refreshed);
                    break;
                case "4":
                    retryLastPaymentPacket(refreshed);
                    break;
                case "5":
                    showHistory(refreshed);
                    break;
                case "6":
                    listUsers(refreshed);
                    break;
                case "7":
                    PAYMENT_SERVICE.save();
                    loggedIn = false;
                    break;
                default:
                    System.out.println("Invalid option.");
                    break;
            }
        }
    }

    private static void showBalance(UserAccount account) {
        System.out.println("Available balance: Rs. " + formatAmount(account.balance()));
    }

    private static void addMoney(UserAccount account) {
        System.out.print("Amount to add: Rs. ");
        Optional<BigDecimal> amount = readAmount();
        if (!amount.isPresent()) {
            System.out.println("Invalid amount.");
            return;
        }

        OperationResult result = PAYMENT_SERVICE.addMoney(account.upiId(), amount.get());
        System.out.println(result.getMessage());
    }

    private static void sendMoneyPacket(UserAccount sender) {
        System.out.print("Receiver UPI ID: ");
        String receiverUpiId = readLine().toLowerCase();
        System.out.print("Amount: Rs. ");
        Optional<BigDecimal> amount = readAmount();
        if (!amount.isPresent()) {
            System.out.println("Invalid amount.");
            return;
        }
        System.out.print("Packet request ID, press Enter for auto: ");
        String requestId = readLine();
        if (requestId.isEmpty()) {
            requestId = "REQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
        System.out.print("Enter UPI PIN: ");
        String pin = readLine();

        PaymentPacket packet = new PaymentPacket(requestId, sender.upiId(), receiverUpiId, amount.get(), pin);
        lastPaymentPacket = packet;
        OperationResult result = PAYMENT_SERVICE.processPaymentPacket(packet);
        System.out.println("Packet ID: " + packet.getRequestId());
        System.out.println(result.getMessage());
    }

    private static void retryLastPaymentPacket(UserAccount sender) {
        if (lastPaymentPacket == null || !lastPaymentPacket.getSenderUpiId().equalsIgnoreCase(sender.upiId())) {
            System.out.println("No payment packet available for retry in this login session.");
            return;
        }

        OperationResult result = PAYMENT_SERVICE.processPaymentPacket(lastPaymentPacket);
        System.out.println("Retried packet ID: " + lastPaymentPacket.getRequestId());
        System.out.println(result.getMessage());
    }

    private static void showHistory(UserAccount account) {
        List<Transaction> history = PAYMENT_SERVICE.transactionsFor(account.upiId());
        if (history.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
        System.out.println();
        System.out.println("Transaction history");
        history.stream()
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .forEach(transaction -> {
                    String date = transaction.getCreatedAt().format(formatter);
                    System.out.println(transaction.getId() + " | " + date + " | " + transaction.getType()
                            + " | Rs. " + formatAmount(transaction.getAmount())
                            + " | " + transaction.getNote());
                });
    }

    private static void listUsers(UserAccount currentUser) {
        List<UserAccount> users = PAYMENT_SERVICE.allUsers();
        if (users.size() <= 1) {
            System.out.println("No other registered users.");
            return;
        }

        System.out.println("Registered local users");
        users.stream()
                .filter(user -> !user.upiId().equals(currentUser.upiId()))
                .forEach(user -> System.out.println(user.name() + " - " + user.upiId()));
    }

    private static Optional<BigDecimal> readAmount() {
        try {
            BigDecimal amount = new BigDecimal(readLine()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }
            return Optional.of(amount);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static String readLine() {
        return SCANNER.nextLine().trim();
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

final class OperationResult {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;

    OperationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    boolean isSuccess() {
        return success;
    }

    String getMessage() {
        return message;
    }
}

final class PaymentPacket implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final String requestId;
    private final String senderUpiId;
    private final String receiverUpiId;
    private final BigDecimal amount;
    private final String pin;

    PaymentPacket(String requestId, String senderUpiId, String receiverUpiId, BigDecimal amount, String pin) {
        this.requestId = requestId;
        this.senderUpiId = senderUpiId;
        this.receiverUpiId = receiverUpiId;
        this.amount = amount;
        this.pin = pin;
    }

    String getRequestId() {
        return requestId;
    }

    String getSenderUpiId() {
        return senderUpiId;
    }

    String getReceiverUpiId() {
        return receiverUpiId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getPin() {
        return pin;
    }

    String fingerprint() {
        return senderUpiId.toLowerCase() + "|" + receiverUpiId.toLowerCase() + "|" + amount.toPlainString();
    }
}

final class Transaction implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String fromUpiId;
    private final String toUpiId;
    private final BigDecimal amount;
    private final TransactionType type;
    private final String note;
    private final LocalDateTime createdAt;

    Transaction(String id, String fromUpiId, String toUpiId, BigDecimal amount, TransactionType type,
            String note, LocalDateTime createdAt) {
        this.id = id;
        this.fromUpiId = fromUpiId;
        this.toUpiId = toUpiId;
        this.amount = amount;
        this.type = type;
        this.note = note;
        this.createdAt = createdAt;
    }

    String getId() {
        return id;
    }

    String getFromUpiId() {
        return fromUpiId;
    }

    String getToUpiId() {
        return toUpiId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    TransactionType getType() {
        return type;
    }

    String getNote() {
        return note;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

enum TransactionType {
    CREDIT,
    DEBIT
}

final class UserAccount implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String mobile;
    private final String upiId;
    private final String pin;
    private BigDecimal balance;
    private final List<Transaction> transactions;

    UserAccount(String name, String mobile, String upiId, String pin) {
        this.name = name;
        this.mobile = mobile;
        this.upiId = upiId;
        this.pin = pin;
        this.balance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.transactions = new ArrayList<>();
    }

    String name() {
        return name;
    }

    String mobile() {
        return mobile;
    }

    String upiId() {
        return upiId;
    }

    String pin() {
        return pin;
    }

    BigDecimal balance() {
        return balance;
    }

    List<Transaction> transactions() {
        return transactions;
    }

    boolean pinMatches(String pinToCheck) {
        return pin.equals(pinToCheck);
    }

    void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
    }
}
