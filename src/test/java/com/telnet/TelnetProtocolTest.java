package com.telnet;

import com.telnet.client.TelnetClient;
import com.telnet.protocol.TelnetCommand;
import com.telnet.protocol.TelnetOption;
import com.telnet.server.TelnetServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 test suite for the Telnet protocol implementation.
 *
 * Tests cover:
 *  - RFC 854 constant values
 *  - Name-lookup helpers
 *  - Server lifecycle (start / stop)
 *  - TCP connectivity (raw socket)
 *  - IAC negotiation bytes sent on connect
 *  - TelnetClient lifecycle
 *  - Multi-client concurrency
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TelnetProtocolTest {

    // Use an unusual port so we don't clash with any other service
    private static final int TEST_PORT = 19876;

    private static TelnetServer server;
    private static Thread       serverThread;

    // -------------------------------------------------------------------------
    // Server setup / teardown
    // -------------------------------------------------------------------------

    @BeforeAll
    static void setupServer() throws InterruptedException {
        server = new TelnetServer(TEST_PORT);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                // Expected when stop() closes the server socket
            }
        }, "TestTelnetServer");
        serverThread.setDaemon(true);
        serverThread.start();
        // Give the server a moment to open the listening socket
        Thread.sleep(600);
    }

    @AfterAll
    static void teardownServer() throws IOException {
        if (server != null) {
            server.stop();
        }
    }

    // =========================================================================
    // Protocol constant tests (RFC 854)
    // =========================================================================

    @Test
    @Order(1)
    void testCommandIACValue() {
        assertEquals(255, TelnetCommand.IAC,
                "IAC must be 0xFF (255) per RFC 854");
    }

    @Test
    @Order(2)
    void testNegotiationCommandValues() {
        assertEquals(254, TelnetCommand.DONT);
        assertEquals(253, TelnetCommand.DO);
        assertEquals(252, TelnetCommand.WONT);
        assertEquals(251, TelnetCommand.WILL);
    }

    @Test
    @Order(3)
    void testSubnegotiationCommandValues() {
        assertEquals(250, TelnetCommand.SB);
        assertEquals(240, TelnetCommand.SE);
    }

    @Test
    @Order(4)
    void testMiscCommandValues() {
        assertEquals(249, TelnetCommand.GA);
        assertEquals(241, TelnetCommand.NOP);
        assertEquals(244, TelnetCommand.IP);
        assertEquals(246, TelnetCommand.AYT);
    }

    @Test
    @Order(5)
    void testOptionEcho() {
        assertEquals(1, TelnetOption.ECHO,
                "ECHO option must be 1 per RFC 857");
    }

    @Test
    @Order(6)
    void testOptionSuppressGA() {
        assertEquals(3, TelnetOption.SUPPRESS_GA,
                "SUPPRESS_GA must be 3 per RFC 858");
    }

    @Test
    @Order(7)
    void testOptionCodes() {
        assertEquals(5,  TelnetOption.STATUS);
        assertEquals(6,  TelnetOption.TIMING_MARK);
        assertEquals(24, TelnetOption.TERMINAL_TYPE);
        assertEquals(31, TelnetOption.WINDOW_SIZE);
        assertEquals(32, TelnetOption.TERMINAL_SPEED);
        assertEquals(34, TelnetOption.LINEMODE);
    }

    // =========================================================================
    // Name-lookup tests
    // =========================================================================

    @Test
    @Order(8)
    void testCommandNames() {
        assertEquals("IAC",  TelnetCommand.getName(TelnetCommand.IAC));
        assertEquals("DO",   TelnetCommand.getName(TelnetCommand.DO));
        assertEquals("DONT", TelnetCommand.getName(TelnetCommand.DONT));
        assertEquals("WILL", TelnetCommand.getName(TelnetCommand.WILL));
        assertEquals("WONT", TelnetCommand.getName(TelnetCommand.WONT));
        assertEquals("SB",   TelnetCommand.getName(TelnetCommand.SB));
        assertEquals("SE",   TelnetCommand.getName(TelnetCommand.SE));
        assertEquals("GA",   TelnetCommand.getName(TelnetCommand.GA));
        assertEquals("NOP",  TelnetCommand.getName(TelnetCommand.NOP));
    }

    @Test
    @Order(9)
    void testUnknownCommandName() {
        String name = TelnetCommand.getName(99);
        assertTrue(name.startsWith("UNKNOWN"), "Unknown commands should return UNKNOWN(n)");
    }

    @Test
    @Order(10)
    void testOptionNames() {
        assertEquals("ECHO",          TelnetOption.getName(TelnetOption.ECHO));
        assertEquals("SUPPRESS_GA",   TelnetOption.getName(TelnetOption.SUPPRESS_GA));
        assertEquals("TERMINAL_TYPE", TelnetOption.getName(TelnetOption.TERMINAL_TYPE));
        assertEquals("WINDOW_SIZE",   TelnetOption.getName(TelnetOption.WINDOW_SIZE));
        assertEquals("LINEMODE",      TelnetOption.getName(TelnetOption.LINEMODE));
    }

    @Test
    @Order(11)
    void testUnknownOptionName() {
        String name = TelnetOption.getName(200);
        assertTrue(name.startsWith("UNKNOWN"), "Unknown options should return UNKNOWN(n)");
    }

    // =========================================================================
    // Server lifecycle tests
    // =========================================================================

    @Test
    @Order(12)
    void testServerIsRunning() {
        assertTrue(server.isRunning(), "Server should be running after start()");
    }

    @Test
    @Order(13)
    void testServerPort() {
        assertEquals(TEST_PORT, server.getPort());
    }

    // =========================================================================
    // TCP connectivity tests
    // =========================================================================

    @Test
    @Order(14)
    void testRawSocketCanConnect() throws IOException {
        try (Socket s = new Socket("localhost", TEST_PORT)) {
            assertTrue(s.isConnected(), "Raw TCP socket should connect successfully");
        }
    }

    @Test
    @Order(15)
    void testServerSendsIACOnConnect() throws IOException {
        try (Socket s = new Socket("localhost", TEST_PORT)) {
            s.setSoTimeout(3000);
            InputStream in = s.getInputStream();

            // Read first 3 bytes - they must be the initial IAC WILL ECHO negotiation
            byte[] buf = new byte[3];
            int read = in.read(buf);

            assertTrue(read > 0, "Server should send data immediately on connect");
            assertEquals((byte) TelnetCommand.IAC, buf[0],
                    "First byte must be IAC (255)");
            assertEquals((byte) TelnetCommand.WILL, buf[1],
                    "Second byte should be WILL (251)");
            assertEquals((byte) TelnetOption.ECHO, buf[2],
                    "Third byte should be ECHO (1) - server offers echo");
        }
    }

    @Test
    @Order(16)
    void testServerSendsWelcomeBanner() throws IOException, InterruptedException {
        try (Socket s = new Socket("localhost", TEST_PORT)) {
            s.setSoTimeout(3000);
            InputStream in = s.getInputStream();

            // Read enough bytes to pass the IAC negotiation and reach the banner
            StringBuilder text = new StringBuilder();
            long deadline = System.currentTimeMillis() + 2500;
            while (System.currentTimeMillis() < deadline) {
                int b = in.read();
                if (b == -1) break;
                if (b != TelnetCommand.IAC && b >= 32) {
                    text.append((char) b);
                }
                if (text.toString().contains("Telnet")) break;
            }
            assertTrue(text.toString().contains("Telnet"),
                    "Welcome banner should contain 'Telnet'. Got: " + text);
        }
    }

    // =========================================================================
    // TelnetClient object tests
    // =========================================================================

    @Test
    @Order(17)
    void testClientConstructor() {
        TelnetClient client = new TelnetClient("localhost", TEST_PORT);
        assertEquals("localhost", client.getHost());
        assertEquals(TEST_PORT,   client.getPort());
        assertFalse(client.isConnected(), "Client should not be connected before connect()");
    }

    @Test
    @Order(18)
    void testClientConnectAndDisconnect() throws IOException {
        TelnetClient client = new TelnetClient("localhost", TEST_PORT);
        assertFalse(client.isConnected());

        client.connect();
        assertTrue(client.isConnected(), "Client should be connected after connect()");

        client.disconnect();
        assertFalse(client.isConnected(), "Client should not be connected after disconnect()");
    }

    @Test
    @Order(19)
    void testClientSendLine() throws IOException, InterruptedException {
        TelnetClient client = new TelnetClient("localhost", TEST_PORT);
        client.connect();
        assertTrue(client.isConnected());

        // Send a line - should not throw
        Thread.sleep(300); // let IAC negotiation complete
        client.sendLine("testuser");

        client.disconnect();
    }

    // =========================================================================
    // Concurrency test
    // =========================================================================

    @Test
    @Order(20)
    void testMultipleSimultaneousClients() throws IOException, InterruptedException {
        final int NUM_CLIENTS = 5;
        TelnetClient[] clients = new TelnetClient[NUM_CLIENTS];

        // Connect all clients
        for (int i = 0; i < NUM_CLIENTS; i++) {
            clients[i] = new TelnetClient("localhost", TEST_PORT);
            clients[i].connect();
        }

        // Verify all connected
        for (int i = 0; i < NUM_CLIENTS; i++) {
            assertTrue(clients[i].isConnected(),
                    "Client " + i + " should be connected");
        }

        Thread.sleep(200);

        // Disconnect all clients
        for (TelnetClient client : clients) {
            client.disconnect();
        }

        // Verify all disconnected
        for (int i = 0; i < NUM_CLIENTS; i++) {
            assertFalse(clients[i].isConnected(),
                    "Client " + i + " should be disconnected");
        }
    }

    // =========================================================================
    // Protocol encoding tests
    // =========================================================================

    @Test
    @Order(21)
    void testIACByteIsHighBit() {
        // IAC (0xFF) must be 255 - i.e., all 8 bits set
        assertEquals(0xFF, TelnetCommand.IAC & 0xFF,
                "IAC byte must have all 8 bits set (0xFF = 255)");
    }

    @Test
    @Order(22)
    void testNegotiationCommandOrdering() {
        // RFC 854 byte values: DONT=254 > DO=253 > WONT=252 > WILL=251 > SE=240
        assertTrue(TelnetCommand.DONT > TelnetCommand.DO);
        assertTrue(TelnetCommand.DO   > TelnetCommand.WONT);
        assertTrue(TelnetCommand.WONT > TelnetCommand.WILL);
        assertTrue(TelnetCommand.WILL > TelnetCommand.SE);
    }

    @Test
    @Order(23)
    void testIACSequenceBuilding() {
        // Verify we can build a valid IAC DO ECHO triplet
        byte[] seq = {
            (byte) TelnetCommand.IAC,
            (byte) TelnetCommand.DO,
            (byte) TelnetOption.ECHO
        };
        assertEquals(3, seq.length);
        assertEquals((byte) 255, seq[0]); // IAC
        assertEquals((byte) 253, seq[1]); // DO
        assertEquals((byte)   1, seq[2]); // ECHO
    }

    @Test
    @Order(24)
    void testClientObjectNotNull() {
        TelnetClient client = new TelnetClient("192.168.1.1", 23);
        assertNotNull(client);
        assertEquals("192.168.1.1", client.getHost());
        assertEquals(23, client.getPort());
    }
}
