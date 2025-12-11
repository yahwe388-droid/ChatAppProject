package Hn;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {

    private Socket socket;
    private ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private String clientId;
    private UserStatus status = UserStatus.ONLINE;
    
    public enum UserStatus {
        ONLINE, AWAY, BUSY, OFFLINE
    }

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.clientId = generateClientId();
        
        try {
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Remove timeout to prevent automatic disconnection
            socket.setKeepAlive(true);
            
        } catch (IOException e) {
            System.err.println("Error creating client handler: " + e.getMessage());
            close();
        }
    }

    @Override
    public void run() {
        try {
            username = in.readLine();
            if (username == null || username.trim().isEmpty()) {
                username = "Anonymous_" + clientId.substring(0, 4);
            }
            
            username = validateUsername(username);
            
            if (server.isUsernameTaken(username, this)) {
                sendSystemMessage("Username '" + username + "' is already taken. Using '" + username + "_" + clientId.substring(0, 4) + "' instead.");
                username = username + "_" + clientId.substring(0, 4);
            }
            
            server.clientConnected(this);
            
            sendSystemMessage("Welcome to the Chat Server, " + username + "!");
            sendSystemMessage("Type /help for available commands");
            
            String message;
            while (isRunning.get() && (message = in.readLine()) != null) {
                if (!message.trim().isEmpty()) {
                    if (message.startsWith("/")) {
                        handleCommand(message);
                    } else {
                        if (status != UserStatus.AWAY) {
                            String formattedMessage = formatMessage(message);
                            server.broadcast(formattedMessage, this, false);
                            server.logMessage(username, message);
                        } else {
                            sendSystemMessage("You are marked as AWAY. Type /back to resume chatting.");
                        }
                    }
                }
            }
        } catch (SocketException e) {
            // Socket closed by server or client
            System.err.println("Socket closed for " + username + ": " + e.getMessage());
        } catch (IOException e) {
            if (isRunning.get()) {
                System.err.println("Error handling client " + username + ": " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }
    
    private void handleCommand(String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1] : "";
        
        switch (cmd) {
            case "/help":
                sendSystemMessage("Available commands:");
                sendSystemMessage("/users - Show online users");
                sendSystemMessage("/msg <user> <message> - Send private message");
                sendSystemMessage("/away <message> - Set away status");
                sendSystemMessage("/back - Return from away status");
                sendSystemMessage("/status <online|away|busy> - Change status");
                sendSystemMessage("/clear - Clear your chat");
                sendSystemMessage("/info - Show server info");
                break;
                
            case "/users":
                sendSystemMessage("Online users (" + server.getOnlineCount() + "):");
                for (ClientHandler user : server.getConnectedUsers()) {
                    String userInfo = "- " + user.getUsername();
                    if (user != this) {
                        userInfo += " (" + user.getStatus() + ")";
                    }
                    sendSystemMessage(userInfo);
                }
                break;
                
            case "/msg":
                handlePrivateMessage(argument);
                break;
                
            case "/away":
                setStatus(UserStatus.AWAY);
                sendSystemMessage("You are now AWAY" + (argument.isEmpty() ? "" : ": " + argument));
                server.broadcast("[System] " + username + " is now away", this, true);
                break;
                
            case "/back":
                setStatus(UserStatus.ONLINE);
                sendSystemMessage("Welcome back!");
                server.broadcast("[System] " + username + " is back online", this, true);
                break;
                
            case "/status":
                handleStatusChange(argument);
                break;
                
            case "/clear":
                sendMessage("CLEAR_CHAT");
                break;
                
            case "/info":
                sendSystemMessage("Server Information:");
                sendSystemMessage("Version: " + server.getServerVersion());
                sendSystemMessage("Uptime: " + server.getUptime());
                sendSystemMessage("Active connections: " + server.getOnlineCount());
                break;
                
            default:
                sendSystemMessage("Unknown command. Type /help for available commands.");
        }
    }
    
    private void handlePrivateMessage(String argument) {
        String[] parts = argument.split(" ", 2);
        if (parts.length < 2) {
            sendSystemMessage("Usage: /msg <username> <message>");
            return;
        }
        
        String targetUser = parts[0];
        String privateMsg = parts[1];
        
        if (server.sendPrivateMessage(this, targetUser, privateMsg)) {
            sendMessage("[PM to " + targetUser + "] " + privateMsg);
        } else {
            sendSystemMessage("User '" + targetUser + "' not found or offline.");
        }
    }
    
    private void handleStatusChange(String status) {
        try {
            UserStatus newStatus = UserStatus.valueOf(status.toUpperCase());
            setStatus(newStatus);
            sendSystemMessage("Status changed to: " + newStatus);
            server.broadcast("[System] " + username + " is now " + newStatus, this, true);
        } catch (IllegalArgumentException e) {
            sendSystemMessage("Invalid status. Use: online, away, busy");
        }
    }
    
    private String formatMessage(String message) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        String timestamp = now.format(formatter);
        
        return "[" + timestamp + "] " + username + ": " + message;
    }
    
    private String validateUsername(String username) {
        username = username.replaceAll("[^a-zA-Z0-9_]", "").trim();
        if (username.length() > 15) {
            username = username.substring(0, 15);
        }
        if (username.isEmpty()) {
            username = "User_" + clientId.substring(0, 4);
        }
        return username;
    }
    
    public void sendMessage(String message) {
        if (out != null && !socket.isClosed()) {
            out.println(message);
            out.flush();
        }
    }
    
    public void sendSystemMessage(String message) {
        sendMessage("[System] " + message);
    }
    
    public void disconnect() {
        if (isRunning.compareAndSet(true, false)) {
            setStatus(UserStatus.OFFLINE);
            close();
            server.removeClient(this);
        }
    }
    
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }
    }
    
    private String generateClientId() {
        return String.valueOf(System.currentTimeMillis()) + "_" + 
               Thread.currentThread().getId();
    }
    
    public String getUsername() {
        return username != null ? username : "Unknown";
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public UserStatus getStatus() {
        return status;
    }
    
    public void setStatus(UserStatus status) {
        this.status = status;
    }
    
    public Socket getSocket() {
        return socket;
    }
}