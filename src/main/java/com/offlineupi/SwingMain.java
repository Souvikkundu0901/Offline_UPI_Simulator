package com.offlineupi;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SwingMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // Keep the default Swing look and feel when the system theme is unavailable.
                }
                new UpiSwingApp().show();
            }
        });
    }
}

final class UpiSwingApp {
    private static final String LOGIN_CARD = "login";
    private static final String DASHBOARD_CARD = "dashboard";

    private static final Color BACKGROUND = new Color(246, 248, 251);
    private static final Color PANEL = Color.WHITE;
    private static final Color TEXT = new Color(28, 35, 45);
    private static final Color MUTED = new Color(96, 111, 128);
    private static final Color PRIMARY = new Color(0, 124, 137);
    private static final Color PRIMARY_DARK = new Color(0, 96, 108);
    private static final Color SUCCESS = new Color(28, 131, 91);
    private static final Color DANGER = new Color(178, 58, 72);

    private final PaymentService paymentService = new PaymentService(new StorageService());
    private final JFrame frame = new JFrame("Offline UPI");
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel root = new JPanel(cardLayout);

    private JTextField loginUpiField;
    private JPasswordField loginPinField;
    private JTextField registerNameField;
    private JTextField registerMobileField;
    private JTextField registerUpiField;
    private JPasswordField registerPinField;

    private JLabel userLabel;
    private JLabel upiLabel;
    private JLabel balanceLabel;
    private JTextField addMoneyField;
    private JTextField receiverField;
    private JTextField amountField;
    private JTextField packetIdField;
    private JPasswordField paymentPinField;
    private JLabel lastPacketLabel;
    private DefaultTableModel historyModel;
    private DefaultTableModel usersModel;

    private UserAccount currentUser;
    private PaymentPacket lastPaymentPacket;

    void show() {
        paymentService.load();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(980, 680));
        frame.setLocationByPlatform(true);
        frame.setContentPane(root);
        root.add(buildLoginPanel(), LOGIN_CARD);
        root.add(buildDashboardPanel(), DASHBOARD_CARD);
        cardLayout.show(root, LOGIN_CARD);
        frame.setVisible(true);
    }

    private JPanel buildLoginPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BACKGROUND);
        outer.setBorder(new EmptyBorder(34, 44, 34, 44));

        JPanel hero = new JPanel(new GridBagLayout());
        hero.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 34);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        hero.add(buildBrandPanel(), gbc);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.65;
        gbc.weighty = 1.0;
        hero.add(buildAuthTabs(), gbc);

        outer.add(hero, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildBrandPanel() {
        JPanel panel = roundedPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBackground(new Color(14, 59, 67));
        panel.setBorder(new EmptyBorder(34, 34, 34, 34));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1.0;

        JLabel badge = label("OFFLINE MODE", 13, Font.BOLD, new Color(170, 230, 223));
        panel.add(badge, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(18, 0, 0, 0);
        JLabel title = label("<html>Offline UPI<br>Payment Lab</html>", 42, Font.BOLD, Color.WHITE);
        panel.add(title, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(20, 0, 0, 0);
        JLabel description = label("<html>Send local payment packets, retry safely, and learn how idempotency prevents duplicate debits.</html>",
                17, Font.PLAIN, new Color(222, 241, 240));
        panel.add(description, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(28, 0, 0, 0);
        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0));
        stats.setOpaque(false);
        stats.add(statBox("Packet", "Request ID"));
        stats.add(statBox("Retry", "No double debit"));
        stats.add(statBox("Store", "Local file"));
        panel.add(stats, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);
        return panel;
    }

    private JPanel buildAuthTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabs.addTab("Login", buildLoginForm());
        tabs.addTab("Register", buildRegisterForm());

        JPanel wrapper = roundedPanel();
        wrapper.setLayout(new BorderLayout());
        wrapper.setBorder(new EmptyBorder(18, 18, 18, 18));
        wrapper.add(tabs, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildLoginForm() {
        JPanel form = formPanel();
        GridBagConstraints gbc = formConstraints();

        form.add(sectionTitle("Welcome back"), gbc);
        gbc.gridy++;
        form.add(sectionHint("Use your local UPI ID and 4-digit PIN."), gbc);

        loginUpiField = input("example@offline");
        loginPinField = passwordInput();
        addField(form, gbc, "UPI ID", loginUpiField);
        addField(form, gbc, "UPI PIN", loginPinField);

        gbc.gridy++;
        gbc.insets = new Insets(22, 0, 0, 0);
        JButton loginButton = primaryButton("Login");
        loginButton.addActionListener(event -> login());
        form.add(loginButton, gbc);
        return form;
    }

    private JPanel buildRegisterForm() {
        JPanel form = formPanel();
        GridBagConstraints gbc = formConstraints();

        form.add(sectionTitle("Create account"), gbc);
        gbc.gridy++;
        form.add(sectionHint("This creates an offline demo account only."), gbc);

        registerNameField = input("Full name");
        registerMobileField = input("10-digit mobile");
        registerUpiField = input("name@offline");
        registerPinField = passwordInput();

        addField(form, gbc, "Full name", registerNameField);
        addField(form, gbc, "Mobile number", registerMobileField);
        addField(form, gbc, "UPI ID", registerUpiField);
        addField(form, gbc, "UPI PIN", registerPinField);

        gbc.gridy++;
        gbc.insets = new Insets(22, 0, 0, 0);
        JButton registerButton = primaryButton("Register");
        registerButton.addActionListener(event -> register());
        form.add(registerButton, gbc);
        return form;
    }

    private JPanel buildDashboardPanel() {
        JPanel dashboard = new JPanel(new BorderLayout(18, 18));
        dashboard.setBackground(BACKGROUND);
        dashboard.setBorder(new EmptyBorder(22, 26, 22, 26));

        dashboard.add(buildTopBar(), BorderLayout.NORTH);
        dashboard.add(buildDashboardTabs(), BorderLayout.CENTER);
        return dashboard;
    }

    private JPanel buildTopBar() {
        JPanel top = roundedPanel();
        top.setLayout(new BorderLayout(16, 0));
        top.setBorder(new EmptyBorder(22, 24, 22, 24));

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        textPanel.setOpaque(false);
        userLabel = label("Welcome", 24, Font.BOLD, TEXT);
        upiLabel = label("", 14, Font.PLAIN, MUTED);
        textPanel.add(userLabel);
        textPanel.add(upiLabel);

        balanceLabel = label("Rs. 0.00", 26, Font.BOLD, SUCCESS);
        balanceLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JButton logoutButton = secondaryButton("Logout");
        logoutButton.addActionListener(event -> logout());

        JPanel right = new JPanel(new BorderLayout(16, 0));
        right.setOpaque(false);
        right.add(balanceLabel, BorderLayout.CENTER);
        right.add(logoutButton, BorderLayout.EAST);

        top.add(textPanel, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        return top;
    }

    private JTabbedPane buildDashboardTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabs.addTab("Pay", buildPayPanel());
        tabs.addTab("History", buildHistoryPanel());
        tabs.addTab("Users", buildUsersPanel());
        return tabs;
    }

    private JPanel buildPayPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 18, 0));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));
        panel.add(buildPaymentCard());
        panel.add(buildWalletCard());
        return panel;
    }

    private JPanel buildPaymentCard() {
        JPanel card = roundedPanel();
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(24, 24, 24, 24));
        GridBagConstraints gbc = formConstraints();

        card.add(sectionTitle("Send payment packet"), gbc);
        gbc.gridy++;
        card.add(sectionHint("Use the same packet ID to retry safely without duplicate debit."), gbc);

        receiverField = input("receiver@offline");
        amountField = input("100.00");
        packetIdField = input("Auto-generated if empty");
        paymentPinField = passwordInput();

        addField(card, gbc, "Receiver UPI ID", receiverField);
        addField(card, gbc, "Amount", amountField);
        addField(card, gbc, "Packet request ID", packetIdField);
        addField(card, gbc, "UPI PIN", paymentPinField);

        gbc.gridy++;
        gbc.insets = new Insets(22, 0, 0, 0);
        JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0));
        actions.setOpaque(false);
        JButton sendButton = primaryButton("Send Packet");
        sendButton.addActionListener(event -> sendPacket());
        JButton retryButton = secondaryButton("Retry Last");
        retryButton.addActionListener(event -> retryLastPacket());
        actions.add(sendButton);
        actions.add(retryButton);
        card.add(actions, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(18, 0, 0, 0);
        lastPacketLabel = label("No packet sent in this session.", 13, Font.PLAIN, MUTED);
        card.add(lastPacketLabel, gbc);
        return card;
    }

    private JPanel buildWalletCard() {
        JPanel card = roundedPanel();
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(24, 24, 24, 24));
        GridBagConstraints gbc = formConstraints();

        card.add(sectionTitle("Wallet controls"), gbc);
        gbc.gridy++;
        card.add(sectionHint("Add demo money for offline testing."), gbc);

        addMoneyField = input("500.00");
        addField(card, gbc, "Amount to add", addMoneyField);

        gbc.gridy++;
        gbc.insets = new Insets(22, 0, 0, 0);
        JButton addButton = primaryButton("Add Demo Money");
        addButton.addActionListener(event -> addMoney());
        card.add(addButton, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(26, 0, 0, 0);
        card.add(infoBox("Idempotency rule",
                "<html>A request ID can be processed once. Repeating the same packet returns the stored result. Reusing the ID with different payment details is rejected.</html>"),
                gbc);
        return card;
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));

        historyModel = new DefaultTableModel(new Object[]{"Txn ID", "Date", "Type", "Amount", "Details"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = styledTable(historyModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton refresh = secondaryButton("Refresh History");
        refresh.addActionListener(event -> refreshDashboard());
        panel.add(refresh, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));

        usersModel = new DefaultTableModel(new Object[]{"Name", "UPI ID", "Mobile"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = styledTable(usersModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton refresh = secondaryButton("Refresh Users");
        refresh.addActionListener(event -> refreshDashboard());
        panel.add(refresh, BorderLayout.SOUTH);
        return panel;
    }

    private void login() {
        String upiId = loginUpiField.getText().trim().toLowerCase();
        String pin = new String(loginPinField.getPassword()).trim();
        Optional<UserAccount> account = paymentService.login(upiId, pin);
        if (!account.isPresent()) {
            showError("Invalid UPI ID or PIN.");
            return;
        }
        currentUser = account.get();
        loginPinField.setText("");
        refreshDashboard();
        cardLayout.show(root, DASHBOARD_CARD);
    }

    private void register() {
        OperationResult result = paymentService.register(
                registerNameField.getText().trim(),
                registerMobileField.getText().trim(),
                registerUpiField.getText().trim().toLowerCase(),
                new String(registerPinField.getPassword()).trim()
        );
        if (result.isSuccess()) {
            showInfo(result.getMessage() + " You can login now.");
            registerNameField.setText("");
            registerMobileField.setText("");
            registerUpiField.setText("");
            registerPinField.setText("");
        } else {
            showError(result.getMessage());
        }
    }

    private void addMoney() {
        Optional<BigDecimal> amount = parseAmount(addMoneyField.getText());
        if (!amount.isPresent()) {
            showError("Enter a valid amount greater than zero.");
            return;
        }
        OperationResult result = paymentService.addMoney(currentUser.upiId(), amount.get());
        showResult(result);
        addMoneyField.setText("");
        refreshDashboard();
    }

    private void sendPacket() {
        Optional<BigDecimal> amount = parseAmount(amountField.getText());
        if (!amount.isPresent()) {
            showError("Enter a valid amount greater than zero.");
            return;
        }

        String requestId = packetIdField.getText().trim();
        if (requestId.isEmpty()) {
            requestId = "REQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            packetIdField.setText(requestId);
        }

        PaymentPacket packet = new PaymentPacket(
                requestId,
                currentUser.upiId(),
                receiverField.getText().trim().toLowerCase(),
                amount.get(),
                new String(paymentPinField.getPassword()).trim()
        );
        lastPaymentPacket = packet;
        OperationResult result = paymentService.processPaymentPacket(packet);
        lastPacketLabel.setText("Last packet: " + packet.getRequestId());
        showResult(result);
        paymentPinField.setText("");
        refreshDashboard();
    }

    private void retryLastPacket() {
        if (lastPaymentPacket == null) {
            showError("No packet has been sent in this session.");
            return;
        }
        OperationResult result = paymentService.processPaymentPacket(lastPaymentPacket);
        showResult(result);
        refreshDashboard();
    }

    private void logout() {
        paymentService.save();
        currentUser = null;
        lastPaymentPacket = null;
        cardLayout.show(root, LOGIN_CARD);
    }

    private void refreshDashboard() {
        if (currentUser == null) {
            return;
        }
        currentUser = paymentService.findByUpiId(currentUser.upiId()).orElse(currentUser);
        userLabel.setText("Welcome, " + currentUser.name());
        upiLabel.setText(currentUser.upiId());
        balanceLabel.setText("Rs. " + formatAmount(currentUser.balance()));
        refreshHistory();
        refreshUsers();
    }

    private void refreshHistory() {
        historyModel.setRowCount(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
        List<Transaction> transactions = paymentService.transactionsFor(currentUser.upiId());
        transactions.sort(Comparator.comparing(Transaction::getCreatedAt).reversed());
        for (Transaction transaction : transactions) {
            historyModel.addRow(new Object[]{
                    transaction.getId(),
                    transaction.getCreatedAt().format(formatter),
                    transaction.getType().name(),
                    "Rs. " + formatAmount(transaction.getAmount()),
                    transaction.getNote()
            });
        }
    }

    private void refreshUsers() {
        usersModel.setRowCount(0);
        for (UserAccount user : paymentService.allUsers()) {
            usersModel.addRow(new Object[]{user.name(), user.upiId(), user.mobile()});
        }
    }

    private Optional<BigDecimal> parseAmount(String rawAmount) {
        try {
            BigDecimal amount = new BigDecimal(rawAmount.trim()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }
            return Optional.of(amount);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private void showResult(OperationResult result) {
        if (result.isSuccess()) {
            showInfo(result.getMessage());
        } else {
            showError(result.getMessage());
        }
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(frame, message, "Offline UPI", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Offline UPI", JOptionPane.ERROR_MESSAGE);
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL);
        panel.setBorder(new EmptyBorder(24, 24, 24, 24));
        return panel;
    }

    private GridBagConstraints formConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        return gbc;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, String label, Component field) {
        gbc.gridy++;
        gbc.insets = new Insets(18, 0, 6, 0);
        panel.add(label(label, 13, Font.BOLD, TEXT), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(field, gbc);
    }

    private JTextField input(String placeholder) {
        JTextField field = new JTextField();
        field.setToolTipText(placeholder);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        field.setForeground(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 218, 228)),
                new EmptyBorder(10, 12, 10, 12)
        ));
        return field;
    }

    private JPasswordField passwordInput() {
        JPasswordField field = new JPasswordField();
        field.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 218, 228)),
                new EmptyBorder(10, 12, 10, 12)
        ));
        return field;
    }

    private JButton primaryButton(String text) {
        JButton button = button(text, PRIMARY, Color.WHITE);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(PRIMARY_DARK);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(PRIMARY);
            }
        });
        return button;
    }

    private JButton secondaryButton(String text) {
        return button(text, new Color(232, 239, 243), TEXT);
    }

    private JButton button(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setBorder(new EmptyBorder(12, 16, 12, 16));
        return button;
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(34);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(232, 239, 243));
        table.getTableHeader().setForeground(TEXT);
        table.setGridColor(new Color(226, 232, 240));
        table.setSelectionBackground(new Color(206, 236, 235));
        return table;
    }

    private JPanel roundedPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 231, 238)),
                new EmptyBorder(18, 18, 18, 18)
        ));
        return panel;
    }

    private JPanel statBox(String title, String value) {
        JPanel box = new JPanel(new GridLayout(2, 1, 0, 4));
        box.setBackground(new Color(26, 83, 92));
        box.setBorder(new EmptyBorder(14, 14, 14, 14));
        box.add(label(title, 19, Font.BOLD, Color.WHITE));
        box.add(label(value, 12, Font.PLAIN, new Color(200, 230, 228)));
        return box;
    }

    private JPanel infoBox(String title, String body) {
        JPanel box = new JPanel(new BorderLayout(0, 8));
        box.setBackground(new Color(247, 250, 252));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(223, 231, 239)),
                new EmptyBorder(16, 16, 16, 16)
        ));
        box.add(label(title, 15, Font.BOLD, TEXT), BorderLayout.NORTH);
        box.add(label(body, 13, Font.PLAIN, MUTED), BorderLayout.CENTER);
        return box;
    }

    private JLabel sectionTitle(String text) {
        return label(text, 22, Font.BOLD, TEXT);
    }

    private JLabel sectionHint(String text) {
        return label(text, 13, Font.PLAIN, MUTED);
    }

    private JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", style, size));
        label.setForeground(color);
        return label;
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
