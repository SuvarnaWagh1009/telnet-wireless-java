package com.telnet.client;

import com.telnet.protocol.TelnetCommand;
import com.telnet.protocol.TelnetOption;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

/**
 * RFC 854 Telnet client implementation.
 *
 * Connects to any Telnet-compatible server (this project's server, or any
 * system running a standard Telnet daemon on port 23/2323/etc.).
 *
 * Features:
 *  - IAC option negotiation (ECHO, SUPPRESS_GA)
 *  - Concurrent read (from server) and write (from console) threads
 *  - Subnegotiation skipping
 *  - Clean disconnect
 *
 * Works over any TCP/IP network: WiFi, Ethernet, VPN, mobile hotspot.
 */
public class TelnetClient {

    private static final Logger logger = Logger.getLogger(TelnetClient.class.getName());

    private final String host;
    private final int    port;

    private Socket       socket;
    private InputStream  in;
    private OutputStream out;
    private volatile boolean connected = false;

    /**
     * Creates a new Telnet client targeting the given host and port.
     *
     * @param host hostname or IP address (works with local IP for WiFi)
     * @param port TCP port of the Telnet server
     */
    public TelnetClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // -------------------------------------------------------------------------
    // Connection management
    // -------------------------------------------------------------------------

    /**
     * Opens the TCP connection to the Telnet server.
     *
     * @throws IOException if the connection cannot be established
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(60_000); // 60-second read timeout
        in        = socket.getInputStream();
        out       = socket.getOutputStream();
        connected = true;
        logger.info("Connected to " + host + ":" + port);
    }

    /**
     * Starts an interactive Telnet session.
     *
     * Spawns a background thread that streams data from the server to stdout.
     * The current thread reads from stdin and sends lines to the server.
     * Blocks until the user types "quit"/"exit" or the server closes the
     * connection.
     *
     * @throws IOException          if an I/O error occurs
     * @throws IllegalStateException if called before {@link #connect()}
     */
    public void startInteractiveSession() throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected. Call connect() first.");
        }

        // Background thread: server -> stdout
        Thread readerThread = new Thread(this::readFromServer, "TelnetClientReader");
        readerThread.setDaemon(true);
        readerThread.start();

        System.out.println("[Telnet] Session started. Type 'quit' or 'exit' to disconnect.");

        // Foreground: stdin -> server
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while (connected && (line = console.readLine()) != null) {
            sendLine(line);
            String trimmed = line.trim().toLowerCase();
            if (trimmed.equals("quit") || trimmed.equals("exit") || trimmed.equals("logout")) {
                break;
            }
        }

        disconnect();
    }

    // -------------------------------------------------------------------------
    // Server reader (runs on background thread)
    // -------------------------------------------------------------------------

    private void readFromServer() {
        try {
            while (connected) {
                int b = in.read();
                if (b == -1) {
                    System.out.println("\r\n[Telnet] Connection closed by server.");
                    break;
                }

                if (b == TelnetCommand.IAC) {
                    handleServerIAC();
                } else {
                    // Print raw character to console
                    System.out.print((char) b);
                    System.out.flush();
                }
            }
        } catch (SocketTimeoutException e) {
            logger.fine("Read timeout - connection still alive.");
        } catch (IOException e) {
            if (connected) {
                System.out.println("\r\n[Telnet] Connection lost: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    // -------------------------------------------------------------------------
    // RFC 854 IAC handling (client side)
    // -------------------------------------------------------------------------

    /**
     * Processes an IAC sequence received from the server.
     * Responds to WILL/WONT/DO/DONT negotiations.
     */
    private void handleServerIAC() throws IOException {
        int command = in.read();
        if (command == -1) return;

        if (command == TelnetCommand.WILL
                || command == TelnetCommand.WONT
                || command == TelnetCommand.DO
                || command == TelnetCommand.DONT) {
            int option = in.read();
            if (option == -1) return;
            logger.fine("Server sent: IAC " + TelnetCommand.getName(command)
                    + " " + TelnetOption.getName(option));
            respondToNegotiation(command, option);

        } else if (command == TelnetCommand.SB) {
            skipSubnegotiation();

        } else if (command == TelnetCommand.IAC) {
            // IAC IAC = literal 0xFF data byte
            System.out.print((char) 0xFF);
            System.out.flush();
        }
        // NOP, GA, AYT, etc. - silently ignore
    }

    /**
     * Generates the appropriate response to a server-side option negotiation.
     *
     * Client accepts ECHO and SUPPRESS_GA from the server; refuses most others
     * to keep behaviour simple and predictable.
     */
    private void respondToNegotiation(int command, int option) throws IOException {
        synchronized (out) {
            switch (command) {
                case TelnetCommand.WILL:
                    // Server offers to enable an option
                    if (option == TelnetOption.ECHO || option == TelnetOption.SUPPRESS_GA) {
                        sendCommand(TelnetCommand.DO, option);
                    } else {
                        sendCommand(TelnetCommand.DONT, option);
                    }
                    break;

                case TelnetCommand.WONT:
                    sendCommand(TelnetCommand.DONT, option);
                    break;

                case TelnetCommand.DO:
                    // Server asks client to enable an option - we refuse everything
                    sendCommand(TelnetCommand.WONT, option);
                    break;

                case TelnetCommand.DONT:
                    sendCommand(TelnetCommand.WONT, option);
                    break;
            }
        }
    }

    /** Reads and discards a subnegotiation block until IAC SE. */
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
    // Sending helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a line of text followed by Telnet CRLF (\r\n).
     *
     * @param line the line to send (without line terminator)
     * @throws IOException if the send fails
     */
    public void sendLine(String line) throws IOException {
        synchronized (out) {
            out.write((line + "\r\n").getBytes("UTF-8"));
            out.flush();
        }
    }

    /**
     * Sends raw bytes to the server (for advanced use / testing).
     *
     * @param data the raw bytes to send
     * @throws IOException if the send fails
     */
    public void sendRaw(byte[] data) throws IOException {
        synchronized (out) {
            out.write(data);
            out.flush();
        }
    }

    /** Sends an IAC option-negotiation triplet. */
    private void sendCommand(int command, int option) throws IOException {
        // Must NOT synchronize here if already called inside a synchronized block
        out.write(new byte[]{
            (byte) TelnetCommand.IAC,
            (byte) command,
            (byte) option
        });
        out.flush();
    }

    // -------------------------------------------------------------------------
    // Disconnect
    // -------------------------------------------------------------------------

    /** Closes the connection to the server. */
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("Disconnected from " + host + ":" + port);
            }
        } catch (IOException e) {
            logger.warning("Error while disconnecting: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the target hostname or IP address. */
    public String getHost() { return host; }

    /** Returns the target TCP port. */
    public int getPort() { return port; }

    /** Returns {@code true} if the client is currently connected. */
    public boolean isConnected() { return connected; }
}
