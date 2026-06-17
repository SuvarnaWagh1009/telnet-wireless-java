package com.telnet;

import com.telnet.client.TelnetClient;
import com.telnet.server.TelnetServer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the Java Telnet Wireless Protocol application.
 *
 * Usage:
 *   java -jar telnet-wireless.jar server [port]
 *   java -jar telnet-wireless.jar client [host] [port]
 *
 * Defaults:
 *   port = 2323
 *   host = localhost
 */
public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            System.exit(0);
        }

        switch (args[0].toLowerCase()) {
            case "server":
                int serverPort = args.length > 1 ? parsePort(args[1]) : TelnetServer.DEFAULT_PORT;
                startServer(serverPort);
                break;

            case "client":
                String host       = args.length > 1 ? args[1] : "localhost";
                int    clientPort = args.length > 2 ? parsePort(args[2]) : TelnetServer.DEFAULT_PORT;
                startClient(host, clientPort);
                break;

            default:
                System.err.println("Unknown mode: " + args[0]);
                printUsage();
                System.exit(1);
        }
    }

    // -------------------------------------------------------------------------

    private static void startServer(int port) throws IOException {
        TelnetServer server = new TelnetServer(port);

        // Register a shutdown hook so Ctrl+C stops the server cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            try {
                server.stop();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error stopping server", e);
            }
        }, "ServerShutdownHook"));

        System.out.println("Starting Telnet server on port " + port + " ...");
        System.out.println("To connect from this machine : telnet localhost " + port);
        System.out.println("To connect over WiFi/network : telnet <YOUR_IP> " + port);
        System.out.println("Press Ctrl+C to stop.\n");

        server.start(); // Blocks until stopped
    }

    private static void startClient(String host, int port) throws IOException {
        System.out.println("Connecting to " + host + ":" + port + " ...");
        TelnetClient client = new TelnetClient(host, port);
        client.connect();
        client.startInteractiveSession(); // Blocks until quit
    }

    // -------------------------------------------------------------------------

    private static int parsePort(String s) {
        try {
            int port = Integer.parseInt(s);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port out of range: " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + s);
        }
    }

    private static void printUsage() {
        System.out.println();
        System.out.println("=================================================");
        System.out.println("  Java Telnet Wireless Protocol  (RFC 854)");
        System.out.println("=================================================");
        System.out.println("USAGE:");
        System.out.println("  java -jar telnet-wireless.jar server [port]");
        System.out.println("  java -jar telnet-wireless.jar client [host] [port]");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  # Start server on default port 2323");
        System.out.println("  java -jar telnet-wireless.jar server");
        System.out.println();
        System.out.println("  # Start server on custom port");
        System.out.println("  java -jar telnet-wireless.jar server 5000");
        System.out.println();
        System.out.println("  # Connect to localhost");
        System.out.println("  java -jar telnet-wireless.jar client");
        System.out.println();
        System.out.println("  # Connect to remote host over WiFi");
        System.out.println("  java -jar telnet-wireless.jar client 192.168.1.42 2323");
        System.out.println();
        System.out.println("DEFAULTS:  port=2323  host=localhost");
        System.out.println("=================================================");
    }
}
