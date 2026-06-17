package com.telnet.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Multi-threaded Telnet server implementing RFC 854.
 *
 * Listens on a TCP port and spawns a new {@link TelnetClientHandler} thread
 * for every incoming connection. Supports up to MAX_THREADS simultaneous
 * sessions.
 *
 * Defaults to port 2323 (avoids requiring elevated privileges needed for
 * the standard Telnet port 23). You can change this at construction time.
 *
 * The server works over any TCP/IP network - wired Ethernet, WiFi,
 * mobile hotspot, VPN, etc.
 */
public class TelnetServer {

    private static final Logger logger      = Logger.getLogger(TelnetServer.class.getName());
    public  static final int    DEFAULT_PORT = 2323;
    private static final int    MAX_THREADS  = 20;

    private final int           port;
    private ServerSocket        serverSocket;
    private final ExecutorService threadPool;
    private volatile boolean    running = false;

    /**
     * Creates a server bound to the specified port.
     *
     * @param port TCP port to listen on (1-65535)
     */
    public TelnetServer(int port) {
        this.port       = port;
        this.threadPool = Executors.newFixedThreadPool(MAX_THREADS);
    }

    /** Creates a server using the default port (2323). */
    public TelnetServer() {
        this(DEFAULT_PORT);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the server and blocks until {@link #stop()} is called.
     * Call this from a dedicated thread if you need non-blocking startup.
     *
     * @throws IOException if the server socket cannot be opened
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        running = true;

        logger.info("Telnet server listening on port " + port);
        logger.info("Connect with: telnet localhost " + port);
        logger.info("  or over WiFi: telnet <your-ip> " + port);
        logger.info("Press Ctrl+C to stop the server.");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                TelnetClientHandler handler = new TelnetClientHandler(clientSocket);
                threadPool.execute(handler);
            } catch (IOException e) {
                if (running) {
                    logger.warning("Error accepting connection: " + e.getMessage());
                }
                // If !running the server was intentionally stopped
            }
        }
    }

    /**
     * Stops the server, closes the listening socket, and shuts down the
     * thread pool. Already-connected clients are given 5 seconds to finish.
     *
     * @throws IOException if the server socket cannot be closed
     */
    public void stop() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Telnet server stopped.");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the TCP port the server listens on. */
    public int getPort() {
        return port;
    }

    /** Returns {@code true} if the server is currently running. */
    public boolean isRunning() {
        return running;
    }
}
