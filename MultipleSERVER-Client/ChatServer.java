package Hn;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.*;

public class ChatServer extends JFrame {

    private static final int PORT = 5000;
    private static final String VERSION = "1.0.0";

    private JTextArea logArea;
    private JTextArea clientListArea;
    private JLabel statusLabel;
    private JLabel clientCountLabel;
    private JLabel uptimeLabel;
    private JLabel versionLabel;
    private JTextArea statsArea;

    private ServerSocket serverSocket;
    private List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private Map<String, Integer> messageStats = new ConcurrentHashMap<>();
    private boolean isRunning = false;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private Instant startTime;

    private int totalConnections = 0;
    private int totalMessages = 0;
    private int peakConnections = 0;

    public ChatServer() {
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("Chat Server v" + VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(240, 242, 245));

        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(59, 130, 246));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Chat Server Control Panel");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statusPanel.setOpaque(false);

        versionLabel = new JLabel("v" + VERSION);
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        versionLabel.setForeground(new Color(220, 220, 220));

        uptimeLabel = new JLabel("Uptime: 00:00:00");
        uptimeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        uptimeLabel.setForeground(new Color(220, 220, 220));

        statusLabel = new JLabel("STOPPED");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(new Color(255, 200, 200));

        statusPanel.add(versionLabel);
        statusPanel.add(uptimeLabel);
        statusPanel.add(statusLabel);
        headerPanel.add(statusPanel, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Center panel with tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabbedPane.setBackground(new Color(248, 250, 252));
        tabbedPane.setForeground(new Color(59, 130, 246));

        tabbedPane.addTab("📝 SERVER LOGS", createLogsPanel());
        tabbedPane.addTab("👥 CONNECTED CLIENTS", createClientsPanel());
        tabbedPane.addTab("📊 STATISTICS", createStatisticsPanel());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(createControlPanel(), BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);

        // Start uptime timer
        Timer uptimeTimer = new Timer(1000, e -> updateUptime());
        uptimeTimer.start();
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(255, 255, 255));
        logArea.setForeground(new Color(50, 50, 50));
        logArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 2));
        scrollPane.getVerticalScrollBar().setBackground(new Color(248, 250, 252));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createClientsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(248, 250, 252));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel clientsLabel = new JLabel("CONNECTED CLIENTS");
        clientsLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        clientsLabel.setForeground(new Color(59, 130, 246));
        headerPanel.add(clientsLabel, BorderLayout.WEST);

        clientCountLabel = new JLabel("0");
        clientCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        clientCountLabel.setForeground(new Color(34, 197, 94));
        headerPanel.add(clientCountLabel, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        clientListArea = new JTextArea();
        clientListArea.setEditable(false);
        clientListArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        clientListArea.setBackground(Color.WHITE);
        clientListArea.setForeground(new Color(50, 50, 50));
        clientListArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(clientListArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
        scrollPane.getVerticalScrollBar().setBackground(new Color(248, 250, 252));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        statsArea = new JTextArea();
        statsArea.setEditable(false);
        statsArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        statsArea.setBackground(Color.WHITE);
        statsArea.setForeground(new Color(50, 50, 50));
        statsArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(statsArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 2));
        scrollPane.getVerticalScrollBar().setBackground(new Color(248, 250, 252));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 15));
        panel.setBackground(new Color(248, 250, 252));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JButton startButton = createStyledButton("▶ START SERVER", new Color(34, 197, 94));
        JButton stopButton = createStyledButton("■ STOP SERVER", new Color(239, 68, 68));
        JButton clearButton = createStyledButton("🗑 CLEAR LOGS", new Color(100, 150, 220));
        JButton kickButton = createStyledButton("👢 KICK USER", new Color(245, 158, 11));
        JButton saveButton = createStyledButton("💾 SAVE LOGS", new Color(139, 92, 246));

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
        clearButton.addActionListener(e -> logArea.setText(""));
        kickButton.addActionListener(e -> showKickDialog());
        saveButton.addActionListener(e -> saveLogs());

        panel.add(startButton);
        panel.add(stopButton);
        panel.add(clearButton);
        panel.add(kickButton);
        panel.add(saveButton);

        return panel;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        // Create a custom button that forces text to be visible
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                // Always paint background first
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Paint background
                if (getModel().isPressed()) {
                    g2d.setColor(bgColor.darker());
                } else if (getModel().isRollover()) {
                    g2d.setColor(new Color(
                            Math.min(255, bgColor.getRed() + 30),
                            Math.min(255, bgColor.getGreen() + 30),
                            Math.min(255, bgColor.getBlue() + 30)
                    ));
                } else {
                    g2d.setColor(bgColor);
                }
                g2d.fillRect(0, 0, getWidth(), getHeight());

                // Paint border
                g2d.setColor(bgColor.darker().darker());
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRect(1, 1, getWidth() - 3, getHeight() - 3);

                // Now paint the text
                super.paintComponent(g);
            }

            @Override
            public void paint(Graphics g) {
                super.paint(g);
                // Force all components to repaint
                paintComponents(g);
            }
        };

        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(Color.WHITE); // ALWAYS white text
        button.setBackground(bgColor);
        button.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // CRITICAL: Make button fully opaque
        button.setOpaque(true);
        button.setContentAreaFilled(false); // We paint our own background

        // Remove any UI defaults
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());

        // Add mouse listener for hover effects
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(Color.WHITE);
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(Color.WHITE);
                button.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                button.setForeground(Color.WHITE);
                button.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                button.setForeground(Color.WHITE);
                button.repaint();
            }
        });

        return button;
    }

    private void startServer() {
        if (isRunning) {
            log("Server is already running!");
            return;
        }

        startTime = Instant.now();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                serverSocket.setReuseAddress(true);

                isRunning = true;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("RUNNING");
                    statusLabel.setForeground(new Color(200, 255, 200));
                });

                log("Server started successfully on port " + PORT);
                log("Waiting for client connections...");

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        totalConnections++;

                        ClientHandler handler = new ClientHandler(clientSocket, this);
                        clients.add(handler);
                        new Thread(handler).start();

                        updateClientList();
                        updateStatistics();

                    } catch (SocketException e) {
                        if (isRunning) {
                            log("Server socket closed.");
                        }
                    } catch (IOException e) {
                        log("Error accepting client: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                log("Failed to start server: " + e.getMessage());
            }
        }).start();
    }

    private void stopServer() {
        if (!isRunning) {
            log("Server is not running!");
            return;
        }

        isRunning = false;
        log("Stopping server...");

        try {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    client.disconnect();
                }
                clients.clear();
            }

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("STOPPED");
                statusLabel.setForeground(new Color(255, 200, 200));
            });

            updateClientList();
            updateStatistics();
            log("Server stopped successfully");

        } catch (IOException e) {
            log("Error stopping server: " + e.getMessage());
        }
    }

    public void broadcast(String message, ClientHandler sender, boolean includeSender) {
        totalMessages++;
        log("Message from " + (sender != null ? sender.getUsername() : "System") + ": "
                + (message.length() > 100 ? message.substring(0, 100) + "..." : message));

        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (includeSender || client != sender) {
                    try {
                        client.sendMessage(message);
                    } catch (Exception e) {
                        log("Error sending to " + client.getUsername() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    public boolean sendPrivateMessage(ClientHandler sender, String targetUsername, String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != sender && client.getUsername().equals(targetUsername)) {
                    client.sendMessage("[PM from " + sender.getUsername() + "] " + message);
                    sender.sendMessage("[PM to " + targetUsername + "] " + message);
                    log("Private message: " + sender.getUsername() + " -> " + targetUsername);
                    return true;
                }
            }
        }
        return false;
    }

    public void removeClient(ClientHandler client) {
        boolean removed = clients.remove(client);
        if (removed) {
            updateClientList();
            updateStatistics();
            log(client.getUsername() + " disconnected");
            broadcast("[System] " + client.getUsername() + " has left the chat", null, true);
        }
    }

    public void clientConnected(ClientHandler client) {
        updateClientList();
        updateStatistics();
        log(client.getUsername() + " connected from "
                + client.getSocket().getInetAddress().getHostAddress());

        peakConnections = Math.max(peakConnections, clients.size());
        broadcast("[System] " + client.getUsername() + " has joined the chat", null, true);
    }

    public void logMessage(String username, String message) {
        messageStats.put(username, messageStats.getOrDefault(username, 0) + 1);
    }

    private void updateClientList() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    sb.append("• ").append(client.getUsername())
                            .append(" - ").append(client.getStatus())
                            .append("\n");
                }
            }
            clientListArea.setText(sb.toString());
            clientCountLabel.setText(String.valueOf(clients.size()));
        });
    }

    private void updateStatistics() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Server Statistics\n");
            sb.append("=================\n\n");
            sb.append("Version: ").append(VERSION).append("\n");
            sb.append("Uptime: ").append(getUptime()).append("\n");
            sb.append("Current connections: ").append(clients.size()).append("\n");
            sb.append("Peak connections: ").append(peakConnections).append("\n");
            sb.append("Total connections: ").append(totalConnections).append("\n");
            sb.append("Total messages: ").append(totalMessages).append("\n\n");

            sb.append("Message Statistics\n");
            sb.append("==================\n");
            messageStats.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(10)
                    .forEach(entry -> sb.append(entry.getKey()).append(": ")
                    .append(entry.getValue()).append(" messages\n"));

            statsArea.setText(sb.toString());
        });
    }

    private void updateUptime() {
        if (startTime != null) {
            Duration uptime = Duration.between(startTime, Instant.now());
            String uptimeStr = String.format("%02d:%02d:%02d",
                    uptime.toHours(),
                    uptime.toMinutesPart(),
                    uptime.toSecondsPart());
            uptimeLabel.setText("Uptime: " + uptimeStr);
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = timeFormat.format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showKickDialog() {
        if (clients.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No users connected.",
                    "Kick User",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] userArray = new String[clients.size()];
        for (int i = 0; i < clients.size(); i++) {
            userArray[i] = clients.get(i).getUsername();
        }

        String selectedUser = (String) JOptionPane.showInputDialog(
                this,
                "Select user to kick:",
                "Kick User",
                JOptionPane.QUESTION_MESSAGE,
                null,
                userArray,
                userArray[0]);

        if (selectedUser != null) {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client.getUsername().equals(selectedUser)) {
                        client.sendSystemMessage("You have been kicked by the server administrator.");
                        client.disconnect();
                        log("Kicked user: " + selectedUser);
                        break;
                    }
                }
            }
        }
    }

    private void saveLogs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("server_log_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(fileChooser.getSelectedFile())) {
                writer.write(logArea.getText());
                log("Logs saved to: " + fileChooser.getSelectedFile().getPath());
            } catch (IOException e) {
                log("Error saving logs: " + e.getMessage());
            }
        }
    }

    public String getServerVersion() {
        return VERSION;
    }

    public String getUptime() {
        if (startTime == null) {
            return "00:00:00";
        }
        Duration uptime = Duration.between(startTime, Instant.now());
        return String.format("%02d:%02d:%02d",
                uptime.toHours(),
                uptime.toMinutesPart(),
                uptime.toSecondsPart());
    }

    public int getOnlineCount() {
        return clients.size();
    }

    public int getTotalUsers() {
        return totalConnections;
    }

    public List<ClientHandler> getConnectedUsers() {
        return new ArrayList<>(clients);
    }

    public boolean isUsernameTaken(String username, ClientHandler requester) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != requester && client.getUsername().equals(username)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(ChatServer::new);
    }
}
