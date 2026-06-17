package com.telnet.protocol;

/**
 * Telnet protocol command constants as defined in RFC 854.
 *
 * The Telnet protocol uses a special escape byte (IAC = 255) to introduce
 * command sequences. Any occurrence of 0xFF in the data stream must be
 * escaped as IAC IAC to distinguish it from a command.
 *
 * Standard port: 23 (this project defaults to 2323 to avoid root requirement)
 */
public final class TelnetCommand {

    private TelnetCommand() { /* Utility class - no instantiation */ }

    /** Interpret As Command (0xFF) - marks the start of a Telnet command */
    public static final int IAC  = 255;

    /** DONT (0xFE) - request the other party not to perform an option */
    public static final int DONT = 254;

    /** DO (0xFD) - request the other party to perform an option */
    public static final int DO   = 253;

    /** WONT (0xFC) - refuse to perform an option */
    public static final int WONT = 252;

    /** WILL (0xFB) - offer/agree to perform an option */
    public static final int WILL = 251;

    /** SB (0xFA) - Subnegotiation Begin */
    public static final int SB   = 250;

    /** GA (0xF9) - Go Ahead */
    public static final int GA   = 249;

    /** EL (0xF8) - Erase Line */
    public static final int EL   = 248;

    /** EC (0xF7) - Erase Character */
    public static final int EC   = 247;

    /** AYT (0xF6) - Are You There */
    public static final int AYT  = 246;

    /** AO (0xF5) - Abort Output */
    public static final int AO   = 245;

    /** IP (0xF4) - Interrupt Process */
    public static final int IP   = 244;

    /** BRK (0xF3) - Break */
    public static final int BRK  = 243;

    /** DM (0xF2) - Data Mark */
    public static final int DM   = 242;

    /** NOP (0xF1) - No Operation */
    public static final int NOP  = 241;

    /** SE (0xF0) - Subnegotiation End */
    public static final int SE   = 240;

    /**
     * Returns the human-readable name of a Telnet command byte.
     *
     * @param command the command byte value
     * @return the name of the command
     */
    public static String getName(int command) {
        switch (command) {
            case IAC:  return "IAC";
            case DONT: return "DONT";
            case DO:   return "DO";
            case WONT: return "WONT";
            case WILL: return "WILL";
            case SB:   return "SB";
            case GA:   return "GA";
            case EL:   return "EL";
            case EC:   return "EC";
            case AYT:  return "AYT";
            case AO:   return "AO";
            case IP:   return "IP";
            case BRK:  return "BRK";
            case DM:   return "DM";
            case NOP:  return "NOP";
            case SE:   return "SE";
            default:   return "UNKNOWN(" + command + ")";
        }
    }
}
