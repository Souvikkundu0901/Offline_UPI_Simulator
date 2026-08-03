package com.offlineupi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

final class PaymentService {
    private static final Pattern MOBILE_PATTERN = Pattern.compile("\\d{10}");
    private static final Pattern PIN_PATTERN = Pattern.compile("\\d{4}");
    private static final Pattern UPI_PATTERN = Pattern.compile("[a-z0-9._-]{3,30}@[a-z]{3,20}");

    private final StorageService storageService;
    private AppData appData = new AppData();

    PaymentService(StorageService storageService) {
        this.storageService = storageService;
    }

    void load() {
        appData = storageService.load();
    }

    void save() {
        storageService.save(appData);
    }

    OperationResult register(String name, String mobile, String upiId, String pin) {
        if (name == null || name.trim().isEmpty()) {
            return new OperationResult(false, "Name cannot be empty.");
        }
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            return new OperationResult(false, "Mobile number must contain exactly 10 digits.");
        }
        if (!UPI_PATTERN.matcher(upiId).matches()) {
            return new OperationResult(false, "UPI ID format is invalid. Example: name@offline");
        }
        if (!PIN_PATTERN.matcher(pin).matches()) {
            return new OperationResult(false, "PIN must contain exactly 4 digits.");
        }
        if (findByUpiId(upiId).isPresent()) {
            return new OperationResult(false, "This UPI ID is already registered.");
        }
        boolean mobileExists = appData.users().stream().anyMatch(user -> user.mobile().equals(mobile));
        if (mobileExists) {
            return new OperationResult(false, "This mobile number is already registered.");
        }

        appData.users().add(new UserAccount(name.trim(), mobile, upiId, pin));
        save();
        return new OperationResult(true, "Registration successful.");
    }

    Optional<UserAccount> login(String upiId, String pin) {
        return findByUpiId(upiId).filter(account -> account.pinMatches(pin));
    }

    Optional<UserAccount> findByUpiId(String upiId) {
        return appData.users().stream()
                .filter(user -> user.upiId().equalsIgnoreCase(upiId))
                .findFirst();
    }

    List<UserAccount> allUsers() {
        return new ArrayList<>(appData.users());
    }

    OperationResult addMoney(String upiId, BigDecimal amount) {
        Optional<UserAccount> account = findByUpiId(upiId);
        if (!account.isPresent()) {
            return new OperationResult(false, "Account not found.");
        }

        UserAccount user = account.get();
        user.credit(amount);
        user.transactions().add(new Transaction(
                createTransactionId(),
                "CASH",
                user.upiId(),
                amount,
                TransactionType.CREDIT,
                "Demo money added",
                LocalDateTime.now()
        ));
        save();
        return new OperationResult(true, "Money added successfully.");
    }

    OperationResult processPaymentPacket(PaymentPacket packet) {
        if (packet.getRequestId() == null || packet.getRequestId().trim().isEmpty()) {
            return new OperationResult(false, "Packet request ID is required.");
        }

        Optional<PacketReceipt> existingReceipt = appData.receiptFor(packet.getRequestId());
        if (existingReceipt.isPresent()) {
            PacketReceipt receipt = existingReceipt.get();
            if (!receipt.getFingerprint().equals(packet.fingerprint())) {
                return new OperationResult(false,
                        "Idempotency conflict: this packet request ID was already used for another payment.");
            }
            return new OperationResult(receipt.isSuccess(),
                    "Duplicate packet detected. Returning saved result: " + receipt.getMessage());
        }

        OperationResult result = sendMoney(packet.getSenderUpiId(), packet.getReceiverUpiId(),
                packet.getAmount(), packet.getPin());
        appData.saveReceipt(new PacketReceipt(
                packet.getRequestId(),
                packet.fingerprint(),
                result.isSuccess(),
                result.getMessage(),
                LocalDateTime.now()
        ));
        save();
        return result;
    }

    private OperationResult sendMoney(String senderUpiId, String receiverUpiId, BigDecimal amount, String pin) {
        if (senderUpiId.equalsIgnoreCase(receiverUpiId)) {
            return new OperationResult(false, "You cannot send money to your own UPI ID.");
        }

        Optional<UserAccount> senderResult = findByUpiId(senderUpiId);
        Optional<UserAccount> receiverResult = findByUpiId(receiverUpiId);
        if (!senderResult.isPresent()) {
            return new OperationResult(false, "Sender account not found.");
        }
        if (!receiverResult.isPresent()) {
            return new OperationResult(false, "Receiver account not found.");
        }

        UserAccount sender = senderResult.get();
        UserAccount receiver = receiverResult.get();
        if (!sender.pinMatches(pin)) {
            return new OperationResult(false, "Incorrect UPI PIN.");
        }
        if (sender.balance().compareTo(amount) < 0) {
            return new OperationResult(false, "Insufficient balance.");
        }

        sender.debit(amount);
        receiver.credit(amount);

        String transactionId = createTransactionId();
        LocalDateTime now = LocalDateTime.now();
        sender.transactions().add(new Transaction(
                transactionId,
                sender.upiId(),
                receiver.upiId(),
                amount,
                TransactionType.DEBIT,
                "Paid to " + receiver.name() + " (" + receiver.upiId() + ")",
                now
        ));
        receiver.transactions().add(new Transaction(
                transactionId,
                sender.upiId(),
                receiver.upiId(),
                amount,
                TransactionType.CREDIT,
                "Received from " + sender.name() + " (" + sender.upiId() + ")",
                now
        ));
        return new OperationResult(true, "Payment successful. Transaction ID: " + transactionId);
    }

    List<Transaction> transactionsFor(String upiId) {
        return findByUpiId(upiId)
                .map(user -> new ArrayList<>(user.transactions()))
                .orElseGet(ArrayList::new);
    }

    private String createTransactionId() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
