package com.telnet.server;

import com.telnet.protocol.TelnetCommand;
import com.telnet.protocol.TelnetOption;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Handles a single Telnet client connection on the server side.
 *
 * Implements RFC 854 Telnet protocol:
 *  - IAC command sequence parsing
 *  - Option negotiation (ECHO, SUPPRESS_GA, etc.)
 *  - Character echoing
 *  - Backspace handling
 *  - CR/LF line-ending normalization
 *
 * Each instance runs in its own thread and manages one client socket
 * for its full lifetime.
 */
public class TelnetClientHandler implements Runnable {

    private static final Logger logger = Logger.getLogger(TelnetClientHandler.class.getName());

    /** Session states for the login flow */
    private enum State { USERNAME, PASSWORD, AUTHENTICATED }

    private final Socket socket;
    private InputStream  in;
    private OutputStream out;

    private volatile boolean running = true;
    private State  state    = State.USERNAME;
    private String username = "";

    // Tracks whether the last character read was CR, so we can
    // swallow the following LF in a CRLF sequence without a double line event.
    private boolean lastWasCR = false;

    public TelnetClientHandler(Socket socket) {
        this.socket = socket;
    }

    // -------------------------------------------------------------------------
    // Main run loop
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        try {
            in  = socket.getInputStream();
            out = socket.getOutputStream();

            String remoteAddr = socket.getRemoteSocketAddress().toString();
            logger.info("New Telnet client connected: " + remoteAddr);

            // ---- RFC 854 initial option negotiation ----
            // Tell client: server WILL handle ECHO and SUPPRESS_GA,
            // and request that client also SUPPRESS_GA.
            sendCommand(TelnetCommand.WILL, TelnetOption.ECHO);
            sendCommand(TelnetCommand.WILL, TelnetOption.SUPPRESS_GA);
            sendCommand(TelnetCommand.DO,   TelnetOption.SUPPRESS_GA);

            // ---- Welcome banner ----
            sendMessage("\r\n");
            sendMessage("+----------------------------------------------+\r\n");
            sendMessage("|   Java Telnet Server  (RFC 854)              |\r\n");
            sendMessage("|   Works over WiFi / Ethernet / any network   |\r\n");
            sendMessage("+----------------------------------------------+\r\n");
            sendMessage("Connected from: " + remoteAddr + "\r\n\r\n");
            sendMessage("login: ");

            StringBuilder lineBuffer = new StringBuilder();

            // ---- Main read loop ----
            while (running && !socket.isClosed()) {
                int b = in.read();
                if (b == -1) break;

                if (b == TelnetCommand.IAC) {
                    handleIAC();
                    lastWasCR = false;
                    continue;
                }

                // --- Line ending handling ---
                // Telnet spec: lines end with CR NUL or CR LF.
                // We fire the line event on CR, then skip the following LF/NUL.
                if (b == '\r') {
                    lastWasCR = true;
                    dispatchLine(lineBuffer.toString());
                    lineBuffer.setLength(0);
                } else if (b == '\n') {
                    if (!lastWasCR) {
                        // Bare LF (some clients send only LF)
                        dispatchLine(lineBuffer.toString());
                        lineBuffer.setLength(0);
                    }
                    lastWasCR = false;
                } else if (b == 0 && lastWasCR) {
                    // CR NUL - NUL is the pad after CR; skip it
                    lastWasCR = false;
                } else {
                    lastWasCR = false;
                    handleChar(b, lineBuffer);
                }
            }

        } catch (IOException e) {
            if (running) {
                logger.warning("Client I/O error: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    // -------------------------------------------------------------------------
    // Character processing
    // -------------------------------------------------------------------------

    /**
     * Handles a single printable (or backspace) byte.
     * Echoes the character back unless we are in password state.
     */
    private void handleChar(int b, StringBuilder buffer) throws IOException {
        if (b == 8 || b == 127) {
            // Backspace (BS) or DEL
            if (buffer.length() > 0) {
                buffer.deleteCharAt(buffer.length() - 1);
                // Erase the character on the remote terminal: BS SP BS
                out.write(new byte[]{8, ' ', 8});
                out.flush();
            }
        } else if (b >= 32 && b < 127) {
            // Printable ASCII
            buffer.append((char) b);
            // Echo back - but mask password input
            if (state != State.PASSWORD) {
                out.write(b);
                out.flush();
            }
        }
        // Control characters other than BS/DEL are silently ignored
    }

    // -------------------------------------------------------------------------
    // Line dispatch (state machine)
    // -------------------------------------------------------------------------

    /**
     * Called when a complete line has been received.
     * Drives the login state machine and the authenticated command processor.
     */
    private void dispatchLine(String rawLine) throws IOException {
        String line = rawLine.trim();

        switch (state) {
            case USERNAME:
                username = line;
                sendMessage("\r\npassword: ");
                state = State.PASSWORD;
                break;

            case PASSWORD:
                // Demo server: accept any password as long as username is non-empty
                sendMessage("\r\n");
                if (!username.isEmpty()) {
                    sendMessage("Welcome, " + username + "! (demo mode - any password accepted)\r\n");
                    sendMessage("Type 'help' for available commands.\r\n\r\n");
                    state = State.AUTHENTICATED;
                    sendPrompt();
                } else {
                    sendMessage("Login failed. Try again.\r\n\r\n");
                    sendMessage("login: ");
                    state = State.USERNAME;
                }
                break;

            case AUTHENTICATED:
                sendMessage("\r\n");
                if (running) {
                    processCommand(line);
                }
                if (running) {
                    sendPrompt();
                }
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Command processor
    // -------------------------------------------------------------------------

    private void processCommand(String line) throws IOException {
        if (line.isEmpty()) return;

        switch (line.toLowerCase()) {
            case "help":
                sendMessage("Available commands:\r\n");
                sendMessage("  help      - Show this help\r\n");
                sendMessage("  status    - Show connection status\r\n");
                sendMessage("  date      - Show current date/time\r\n");
                sendMessage("  whoami    - Show current user\r\n");
                sendMessage("  network   - Show network information\r\n");
                sendMessage("  quit      - Disconnect from server\r\n");
                sendMessage("  exit      - Alias for quit\r\n");
                break;

            case "status":
                sendMessage("Connection Status:\r\n");
                sendMessage("  Protocol  : Telnet (RFC 854)\r\n");
                sendMessage("  Transport : TCP/IP (WiFi / Ethernet compatible)\r\n");
                sendMessage("  Client    : " + socket.getRemoteSocketAddress() + "\r\n");
                sendMessage("  Server    : " + socket.getLocalSocketAddress() + "\r\n");
                sendMessage("  User      : " + username + "\r\n");
                break;

            case "date":
                sendMessage("Current date/time: " + new Date() + "\r\n");
                break;

            case "whoami":
                sendMessage("Logged in as: " + username + "\r\n");
                break;

            case "network":
                sendMessage("Network Information:\r\n");
                sendMessage("  Local  address : " + socket.getLocalAddress().getHostAddress() + "\r\n");
                sendMessage("  Local  port    : " + socket.getLocalPort() + "\r\n");
                sendMessage("  Remote address : " + socket.getInetAddress().getHostAddress() + "\r\n");
                sendMessage("  Remote port    : " + socket.getPort() + "\r\n");
                sendMessage("  Note: Telnet runs over TCP/IP and works on any\r\n");
                sendMessage("        network including WiFi and mobile hotspots.\r\n");
                break;

            case "quit":
            case "exit":
            case "logout":
                sendMessage("Goodbye, " + username + "!\r\n");
                running = false;
                break;

            default:
                sendMessage("Command not found: '" + line + "'. Type 'help' for help.\r\n");
        }
    }

    // -------------------------------------------------------------------------
    // RFC 854 IAC handling
    // -------------------------------------------------------------------------

    /**
     * Called after reading an IAC byte. Reads and processes the next command.
     */
    private void handleIAC() throws IOException {
        int command = in.read();
        if (command == -1) return;

        if (command == TelnetCommand.WILL
                || command == TelnetCommand.WONT
                || command == TelnetCommand.DO
                || command == TelnetCommand.DONT) {
            int option = in.read();
            if (option == -1) return;
            logger.fine("Received: IAC " + TelnetCommand.getName(command)
                    + " " + TelnetOption.getName(option));
            negotiateOption(command, option);

        } else if (command == TelnetCommand.SB) {
            // Subnegotiation: read until we see IAC SE
            skipSubnegotiation();

        } else if (command == TelnetCommand.IAC) {
            // IAC IAC means a literal 0xFF data byte - add to buffer if needed
            // (uncommon in practice; we silently ignore here)
        }
        // NOP, GA, AYT, IP, etc. - silently ignore
    }

    /**
     * Responds to a WILL/WONT/DO/DONT option negotiation.
     */
    private void negotiateOption(int command, int option) throws IOException {
        switch (command) {
            case TelnetCommand.DO:
                // Client asks server to perform option
                if (option == TelnetOption.ECHO || option == TelnetOption.SUPPRESS_GA) {
                    sendCommand(TelnetCommand.WILL, option); // Accept
                } else {
                    sendCommand(TelnetCommand.WONT, option); // Refuse
                }
                break;

            case TelnetCommand.DONT:
                sendCommand(TelnetCommand.WONT, option);
                break;

            case TelnetCommand.WILL:
                // Client offers to perform option
                if (option == TelnetOption.SUPPRESS_GA
                        || option == TelnetOption.TERMINAL_TYPE
                        || option == TelnetOption.WINDOW_SIZE) {
                    sendCommand(TelnetCommand.DO, option);   // Accept
                } else {
                    sendCommand(TelnetCommand.DONT, option); // Refuse
                }
                break;

            case TelnetCommand.WONT:
                sendCommand(TelnetCommand.DONT, option);
                break;
        }
    }

    /** Reads and discards bytes until the IAC SE subnegotiation terminator. */
    private void skipSubnegotiation() throws IOException {
        while (true) {
            int b = in.read();
            if (b == -1) return;
            if (b == TelnetCommand.IAC) {
                int next = in.read();
                if (next == TelnetCommand.SE || next == -1) return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    /** Sends an IAC option-negotiation triplet: IAC <command> <option>. */
    private void sendCommand(int command, int option) throws IOException {
        out.write(new byte[]{
            (byte) TelnetCommand.IAC,
            (byte) command,
            (byte) option
        });
        out.flush();
    }

    /** Sends a UTF-8 string to the remote client. */
    private void sendMessage(String msg) throws IOException {
        out.write(msg.getBytes("UTF-8"));
        out.flush();
    }

    /** Sends the shell-style prompt. */
    private void sendPrompt() throws IOException {
        sendMessage("[" + username + "@telnet]$ ");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Gracefully closes the client socket. */
    public void disconnect() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("Client disconnected: " + socket.getRemoteSocketAddress());
            }
        } catch (IOException e) {
            logger.warning("Error closing client socket: " + e.getMessage());
        }
    }
}
