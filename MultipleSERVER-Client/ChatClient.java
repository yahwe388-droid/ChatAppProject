package Hn;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatClient extends JFrame {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private JTextArea chatArea;
    private JTextField messageField;
    private JTextField usernameField;
    private JButton sendButton;
    private JButton connectButton;
    private JButton refreshButton;
    private JLabel statusLabel;
    private JLabel connectionIndicator;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private boolean isConnected = false;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public ChatClient() {
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(600, 500));

        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(240, 242, 245));

        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(59, 130, 246));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Chat Client");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusPanel.setOpaque(false);

        connectionIndicator = new JLabel("●");
        connectionIndicator.setFont(new Font("Segoe UI", Font.BOLD, 18));
        connectionIndicator.setForeground(new Color(255, 100, 100));

        statusLabel = new JLabel("Disconnected");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLabel.setForeground(new Color(255, 255, 255));

        statusPanel.add(connectionIndicator);
        statusPanel.add(statusLabel);
        headerPanel.add(statusPanel, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Connection panel
        JPanel connectionPanel = new JPanel(new BorderLayout(10, 0));
        connectionPanel.setBackground(new Color(255, 255, 255));
        connectionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftPanel.setOpaque(false);

        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userLabel.setForeground(new Color(71, 85, 105));
        leftPanel.add(userLabel);

        usernameField = new JTextField(15);
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        usernameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 2),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        usernameField.setBackground(new Color(255, 255, 255));
        leftPanel.add(usernameField);

        connectButton = createUltraVisibleButton("CONNECT", new Color(34, 197, 94));
        connectButton.addActionListener(e -> toggleConnection());
        leftPanel.add(connectButton);

        connectionPanel.add(leftPanel, BorderLayout.CENTER);
        mainPanel.add(connectionPanel, BorderLayout.NORTH);

        // Center panel with split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(550);
        splitPane.setDividerSize(2);
        splitPane.setBackground(new Color(240, 242, 245));

        // Chat panel
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(Color.WHITE);
        chatPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 10));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(Color.WHITE);
        chatArea.setForeground(new Color(30, 41, 59));
        chatArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        chatScroll.getVerticalScrollBar().setBackground(new Color(248, 250, 252));

        // Context menu for chat
        JPopupMenu chatMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy");
        JMenuItem clearItem = new JMenuItem("Clear Chat");

        copyItem.addActionListener(e -> chatArea.copy());
        clearItem.addActionListener(e -> chatArea.setText(""));

        chatMenu.add(copyItem);
        chatMenu.addSeparator();
        chatMenu.add(clearItem);

        chatArea.setComponentPopupMenu(chatMenu);

        chatPanel.add(chatScroll, BorderLayout.CENTER);

        // User list panel
        JPanel userListPanel = new JPanel(new BorderLayout());
        userListPanel.setBackground(Color.WHITE);
        userListPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 15));
        userListPanel.setPreferredSize(new Dimension(200, 0));

        JPanel userListHeader = new JPanel(new BorderLayout());
        userListHeader.setBackground(new Color(248, 250, 252));
        userListHeader.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel userListLabel = new JLabel("Online Users");
        userListLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        userListLabel.setForeground(new Color(59, 130, 246));
        userListHeader.add(userListLabel, BorderLayout.WEST);

        userListPanel.add(userListHeader, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setBackground(new Color(255, 255, 255));
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(null);
        userScroll.getVerticalScrollBar().setBackground(new Color(248, 250, 252));
        userListPanel.add(userScroll, BorderLayout.CENTER);

        // Refresh button for user list - ULTRA VISIBLE
        refreshButton = createUltraVisibleButton("⟳ REFRESH", new Color(100, 150, 220));
        refreshButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        refreshButton.addActionListener(e -> sendCommand("/users"));
        userListPanel.add(refreshButton, BorderLayout.SOUTH);

        splitPane.setLeftComponent(chatPanel);
        splitPane.setRightComponent(userListPanel);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Message input panel with ULTRA VISIBLE Send button
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(new Color(255, 255, 255));
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        // Use BorderLayout for simple arrangement
        JPanel messagePanel = new JPanel(new BorderLayout(10, 0));
        messagePanel.setBackground(new Color(255, 255, 255));

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 2),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        messageField.setBackground(new Color(255, 255, 255));
        messageField.setEnabled(false);
        messageField.addActionListener(e -> sendMessage());

        // Create ULTRA VISIBLE send button
        sendButton = createUltraVisibleButton("✉ SEND", new Color(59, 130, 246));
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());
        sendButton.setPreferredSize(new Dimension(120, 50));

        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        inputPanel.add(messagePanel, BorderLayout.CENTER);

        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);

        // Focus on username field
        usernameField.requestFocus();
    }

    private JButton createUltraVisibleButton(String text, Color bgColor) {
        JButton button = new JButton(text) {
            // Override paintComponent to ensure text is ALWAYS painted
            @Override
            protected void paintComponent(Graphics g) {
                // Paint solid background first
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Paint background with shadow effect
                if (getModel().isPressed()) {
                    g2d.setColor(bgColor.darker().darker());
                } else if (getModel().isRollover()) {
                    g2d.setColor(bgColor.brighter());
                } else {
                    g2d.setColor(bgColor);
                }
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                // Paint border
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);

                // Now paint the text - FORCE IT TO BE VISIBLE
                super.paintComponent(g);
            }

            @Override
            public void paint(Graphics g) {
                // Ensure everything gets painted
                super.paint(g);
            }
        };

        // TEXT SETTINGS - CRITICAL FOR VISIBILITY
        button.setFont(new Font("Segoe UI", Font.BOLD, 14)); // Larger font
        button.setForeground(Color.WHITE); // White text
        button.setBackground(bgColor);

        // Remove default border
        button.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30)); // Extra large padding

        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // CRITICAL: Force button to be completely opaque
        button.setOpaque(true);
        button.setContentAreaFilled(false); // We paint our own background

        // Add property change listener to ensure text stays white
        button.addPropertyChangeListener("foreground", e -> {
            if (!button.getForeground().equals(Color.WHITE)) {
                button.setForeground(Color.WHITE);
            }
        });

        // Add text shadow effect for extra visibility
        button.setLayout(new OverlayLayout(button));

        // Simple hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                button.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                button.repaint();
            }
        });

        return button;
    }

    private void toggleConnection() {
        if (isConnected) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        username = usernameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a username!",
                    "Username Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!username.matches("[a-zA-Z0-9_]+")) {
            JOptionPane.showMessageDialog(this,
                    "Username can only contain letters, numbers, and underscores!",
                    "Invalid Username",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (username.length() > 15) {
            username = username.substring(0, 15);
            usernameField.setText(username);
        }

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), 10000);

            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(username);

            isConnected = true;
            updateConnectionStatus(true);

            new Thread(this::listenForMessages).start();

            appendMessage("System", "Connected to server as " + username);
            appendMessage("System", "Type /help for available commands");

        } catch (SocketTimeoutException e) {
            appendMessage("System", "Connection timeout. Server may be unreachable.");
            JOptionPane.showMessageDialog(this,
                    "Connection timeout. Please check if the server is running.",
                    "Connection Failed",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            appendMessage("System", "Failed to connect: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Could not connect to server.\nMake sure the server is running!",
                    "Connection Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect() {
        try {
            isConnected = false;

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            updateConnectionStatus(false);
            appendMessage("System", "Disconnected from server");
            userListModel.clear();

        } catch (IOException e) {
            appendMessage("System", "Error disconnecting: " + e.getMessage());
        }
    }

    private void updateConnectionStatus(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                connectionIndicator.setForeground(new Color(34, 197, 94));
                statusLabel.setText("Connected");
                statusLabel.setForeground(new Color(255, 255, 255));
                connectButton.setText("✖ DISCONNECT");
                connectButton.setBackground(new Color(239, 68, 68)); // Red for disconnect
                usernameField.setEnabled(false);
                messageField.setEnabled(true);
                sendButton.setEnabled(true);
                refreshButton.setEnabled(true);
                messageField.requestFocus();
            } else {
                connectionIndicator.setForeground(new Color(255, 100, 100));
                statusLabel.setText("Disconnected");
                statusLabel.setForeground(new Color(255, 255, 255));
                connectButton.setText("✓ CONNECT");
                connectButton.setBackground(new Color(34, 197, 94)); // Green for connect
                usernameField.setEnabled(true);
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
                refreshButton.setEnabled(false);
            }
            // Force repaint to update button appearance
            connectButton.repaint();
            sendButton.repaint();
            refreshButton.repaint();
        });
    }

    private void listenForMessages() {
        try {
            String message;
            while (isConnected && (message = in.readLine()) != null) {
                final String msg = message;
                SwingUtilities.invokeLater(() -> processMessage(msg));
            }
        } catch (SocketException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("System", "Server closed the connection.");
                    disconnect();
                });
            }
        } catch (IOException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("System", "Connection error: " + e.getMessage());
                    disconnect();
                });
            }
        }
    }

    private void processMessage(String message) {
        if (message.startsWith("[System]")) {
            appendMessage("System", message.substring(9));
        } else if (message.startsWith("[PM from")) {
            // Handle private messages
            String pmPattern = "\\[PM from (.*?)\\] (.*)";
            String pmMessage = message.replaceAll(pmPattern, "[Private from $1] $2");
            appendMessage("Private", pmMessage);
        } else if (message.startsWith("USERLIST:")) {
            updateUserList(message.substring(9));
        } else if (message.equals("CLEAR_CHAT")) {
            chatArea.setText("");
        } else {
            appendMessage("Chat", message);
        }
    }

    private void appendMessage(String type, String message) {
        String time = "[" + timeFormat.format(new Date()) + "] ";

        switch (type) {
            case "System":
                chatArea.append(time + "[System] " + message + "\n");
                break;
            case "Chat":
                chatArea.append(time + message + "\n");
                break;
            case "Private":
                chatArea.append(time + message + "\n");
                break;
        }

        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void updateUserList(String userListStr) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            String[] users = userListStr.split(",");
            for (String user : users) {
                if (!user.trim().isEmpty()) {
                    userListModel.addElement(user.trim());
                }
            }
        });
    }

    private void sendMessage() {
        if (!isConnected || out == null) {
            return;
        }

        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            messageField.setText("");

            if (!message.startsWith("/")) {
                appendMessage("Chat", "You: " + message);
            }
        }
    }

    private void sendCommand(String command) {
        if (isConnected && out != null) {
            out.println(command);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(ChatClient::new);
    }
}
