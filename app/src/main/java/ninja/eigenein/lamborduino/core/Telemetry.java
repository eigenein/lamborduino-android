package ninja.eigenein.lamborduino.core;

public class Telemetry {

    /**
     * Ping time in milliseconds.
     */
    public final long ping;

    /**
     * Voltage in volts.
     */
    public final double vcc;

    public Telemetry(final long ping, final double vcc) {
        this.ping = ping;
        this.vcc = vcc;
    }
}
