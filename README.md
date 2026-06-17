# Java Telnet Wireless Protocol

A clean, well-structured implementation of the **Telnet protocol (RFC 854)** in Java.  
Runs over **any TCP/IP network** — wired Ethernet, **WiFi**, mobile hotspot, VPN, or localhost.

---

## Project Structure

```
telnet-wireless-java/
├── pom.xml                          ← Maven build file
├── README.md
├── .gitignore
└── src/
    ├── main/java/com/telnet/
    │   ├── Main.java                ← Entry point (server or client mode)
    │   ├── protocol/
    │   │   ├── TelnetCommand.java   ← RFC 854 IAC command constants
    │   │   └── TelnetOption.java    ← Telnet option codes (RFC 857-1572)
    │   ├── server/
    │   │   ├── TelnetServer.java    ← Multi-threaded TCP listener
    │   │   └── TelnetClientHandler.java ← Per-client session handler
    │   └── client/
    │       └── TelnetClient.java    ← Interactive Telnet client
    └── test/java/com/telnet/
        └── TelnetProtocolTest.java  ← JUnit 5 test suite (24 tests)
```

---

## Prerequisites

| Tool | Minimum Version | Download |
|------|----------------|----------|
| Java JDK | 8+ | https://adoptium.net |
| Maven | 3.8+ | https://maven.apache.org |

Verify your setup:
```bash
java  -version   # should print 11 or higher
mvn   -version   # should print 3.8 or higher
```

---

## Build

```bash
# Navigate to the project root
cd telnet-wireless-java

# Compile and package into a fat JAR (includes all dependencies)
mvn clean package -DskipTests

# The executable JAR is created at:
#   target/telnet-wireless.jar
```

---

## Run the Server

```bash
# Default port 2323
java -jar target/telnet-wireless.jar server

# Custom port
java -jar target/telnet-wireless.jar server 5000
```

Expected output:
```
Starting Telnet server on port 2323 ...
To connect from this machine : telnet localhost 2323
To connect over WiFi/network : telnet <YOUR_IP> 2323
Press Ctrl+C to stop.
```

### Find your WiFi IP (so other devices can connect)

**Windows:**
```cmd
ipconfig
# Look for "IPv4 Address" under your WiFi adapter
# Example: 192.168.1.42
```

**macOS / Linux:**
```bash
ifconfig | grep "inet "
# or
ip addr show
```

---

## Run the Client

Open a **second terminal** while the server is still running:

```bash
# Connect to localhost
java -jar target/telnet-wireless.jar client

# Connect to a remote host over WiFi
java -jar target/telnet-wireless.jar client 192.168.1.42 2323

# Connect to any standard Telnet server (port 23)
java -jar target/telnet-wireless.jar client some.server.com 23
```

### Demo session
```
login: alice
password: (anything - demo mode)

Welcome, alice! (demo mode - any password accepted)
Type 'help' for available commands.

[alice@telnet]$ help
Available commands:
  help      - Show this help
  status    - Show connection status
  date      - Show current date/time
  whoami    - Show current user
  network   - Show network information
  quit      - Disconnect from server

[alice@telnet]$ network
Network Information:
  Local  address : 0.0.0.0
  Local  port    : 2323
  Remote address : 192.168.1.55
  Remote port    : 54321
  Note: Telnet runs over TCP/IP and works on any
        network including WiFi and mobile hotspots.

[alice@telnet]$ quit
Goodbye, alice!
```

---

## Connect with Third-Party Telnet Clients

You can also connect using any standard Telnet client instead of the built-in Java client:

### Windows (Enable Telnet client first)
```
# In PowerShell (admin) — one-time setup:
Enable-WindowsOptionalFeature -Online -FeatureName TelnetClient

# Then connect:
telnet localhost 2323
telnet 192.168.1.42 2323
```

### PuTTY (Recommended for Windows)
1. Download PuTTY from https://www.putty.org
2. Set **Connection type** → `Telnet`
3. Set **Host Name** → `localhost` (or your WiFi IP)
4. Set **Port** → `2323`
5. Click **Open**

### macOS / Linux
```bash
telnet localhost 2323
# or
nc localhost 2323
```

---

## Run the Tests

```bash
# Run all 24 JUnit 5 tests
mvn test

# Run a specific test class
mvn test -Dtest=TelnetProtocolTest

# Run a specific test method
mvn test -Dtest=TelnetProtocolTest#testServerSendsIACOnConnect
```

Expected output:
```
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### What the tests cover

| # | Test | What it verifies |
|---|------|-----------------|
| 1 | `testCommandIACValue` | IAC = 255 (0xFF) per RFC 854 |
| 2 | `testNegotiationCommandValues` | DONT/DO/WONT/WILL byte values |
| 3-4 | subnegotiation / misc commands | SB, SE, GA, NOP, IP, AYT |
| 5-7 | option codes | ECHO=1, SUPPRESS_GA=3, WINDOW_SIZE=31, etc. |
| 8-11 | name lookups | `getName()` helpers + unknown codes |
| 12-13 | server lifecycle | `isRunning()`, `getPort()` |
| 14 | raw TCP connect | socket connects to server port |
| 15 | IAC on connect | first byte received is 0xFF |
| 16 | welcome banner | server sends "Telnet" string |
| 17 | client constructor | host/port accessors, initial state |
| 18 | connect/disconnect | `isConnected()` lifecycle |
| 19 | `sendLine` | no exception when sending data |
| 20 | concurrency | 5 clients connect simultaneously |
| 21-24 | protocol encoding | byte ordering, IAC triplet building |

---

## How Wireless Works

Telnet transmits data over **standard TCP/IP sockets**, which means it works transparently over:

| Network type | How to use |
|---|---|
| **WiFi (same network)** | Use the server machine's WiFi IP (e.g. `192.168.1.x`) |
| **Mobile hotspot** | Connect both devices to the same hotspot |
| **LAN / Ethernet** | Same as WiFi — use the local IP |
| **Internet** | Port-forward 2323 on your router, use public IP |
| **VPN** | Use the VPN-assigned IP of the server |
| **localhost** | Default — same machine only |

> **Note:** Port 23 (standard Telnet) requires admin/root privileges.  
> This project defaults to port **2323** so no elevated permissions are needed.

---

## Upload to GitHub

```bash
cd telnet-wireless-java

# Initialize git
git init
git add .
git commit -m "Initial commit: Java Telnet RFC 854 implementation"

# Create a repo on GitHub, then:
git remote add origin https://github.com/<your-username>/telnet-wireless-java.git
git branch -M main
git push -u origin main
```

---

## Protocol Reference

| RFC | Topic |
|-----|-------|
| [RFC 854](https://www.rfc-editor.org/rfc/rfc854) | Telnet Protocol Specification |
| [RFC 855](https://www.rfc-editor.org/rfc/rfc855) | Telnet Option Specifications |
| [RFC 857](https://www.rfc-editor.org/rfc/rfc857) | Telnet Echo Option |
| [RFC 858](https://www.rfc-editor.org/rfc/rfc858) | Telnet Suppress Go Ahead Option |
| [RFC 1073](https://www.rfc-editor.org/rfc/rfc1073) | Telnet Window Size Option (NAWS) |
| [RFC 1091](https://www.rfc-editor.org/rfc/rfc1091) | Telnet Terminal Type Option |

---

## License

MIT License — free to use, modify, and distribute.
