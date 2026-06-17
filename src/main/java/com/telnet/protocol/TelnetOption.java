package com.telnet.protocol;

/**
 * Telnet option codes as defined in various RFCs.
 *
 * Options are negotiated using WILL/WONT/DO/DONT commands.
 * Example: server sends IAC WILL ECHO to indicate it will echo characters.
 *          client responds with IAC DO ECHO to accept.
 */
public final class TelnetOption {

    private TelnetOption() { /* Utility class - no instantiation */ }

    /** RFC 857  - Echo: server echoes characters back to client */
    public static final int ECHO           = 1;

    /** RFC 858  - Suppress Go Ahead: suppress the GA signal (almost always negotiated) */
    public static final int SUPPRESS_GA    = 3;

    /** RFC 859  - Status: allows querying the status of Telnet options */
    public static final int STATUS         = 5;

    /** RFC 860  - Timing Mark: used for synchronization */
    public static final int TIMING_MARK    = 6;

    /** RFC 1091 - Terminal Type: negotiate the terminal emulation type */
    public static final int TERMINAL_TYPE  = 24;

    /** RFC 1073 - Negotiate About Window Size (NAWS) */
    public static final int WINDOW_SIZE    = 31;

    /** RFC 1079 - Terminal Speed: negotiate terminal baud rate */
    public static final int TERMINAL_SPEED = 32;

    /** RFC 1372 - Remote Flow Control */
    public static final int REMOTE_FLOW    = 33;

    /** RFC 1184 - Linemode: allow client to process lines locally */
    public static final int LINEMODE       = 34;

    /** RFC 1572 - New Environment: send environment variables */
    public static final int NEW_ENVIRON    = 39;

    /**
     * Returns the human-readable name of a Telnet option byte.
     *
     * @param option the option byte value
     * @return the name of the option
     */
    public static String getName(int option) {
        switch (option) {
            case ECHO:           return "ECHO";
            case SUPPRESS_GA:    return "SUPPRESS_GA";
            case STATUS:         return "STATUS";
            case TIMING_MARK:    return "TIMING_MARK";
            case TERMINAL_TYPE:  return "TERMINAL_TYPE";
            case WINDOW_SIZE:    return "WINDOW_SIZE";
            case TERMINAL_SPEED: return "TERMINAL_SPEED";
            case REMOTE_FLOW:    return "REMOTE_FLOW";
            case LINEMODE:       return "LINEMODE";
            case NEW_ENVIRON:    return "NEW_ENVIRON";
            default:             return "UNKNOWN(" + option + ")";
        }
    }
}
