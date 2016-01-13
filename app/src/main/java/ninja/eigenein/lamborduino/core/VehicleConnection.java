package ninja.eigenein.lamborduino.core;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VehicleConnection {

    private static final String LOG_TAG = VehicleConnection.class.getSimpleName();

    private static final int[] BYTE_ORDER_MARK = {0xFF, 0xFE};

    private static final byte[] COMMAND_NOOP = {0x01};
    private static final byte[] COMMAND_STOP = {0x02};
    private static final byte[] COMMAND_MOVE = {0x03};

    private SocketChangedListener listener;
    private BluetoothSocket socket;

    public void setListener(final SocketChangedListener listener) {
        this.listener = listener;
    }

    public BluetoothSocket getSocket() {
        return socket;
    }

    public synchronized void setSocket(final BluetoothSocket socket) {
        try {
            if (this.socket != null) {
                this.socket.close();
            }
        } catch (final IOException e) {
            // Do nothing.
        } finally {
            this.socket = null;
        }

        this.socket = socket;

        final SocketChangedListener listener = this.listener;
        if (listener != null) {
            listener.onSocketChanged(this);
        }
    }

    /**
     * Sends empty command.
     */
    public synchronized Telemetry noop() {
        return sendCommand(COMMAND_NOOP);
    }

    /**
     * Sends full stop.
     */
    public synchronized Telemetry stop() {
        return sendCommand(COMMAND_STOP);
    }

    /**
     * Sends move command.
     */
    public synchronized Telemetry move(
            final float leftWheelSpeed,
            final boolean leftWheelInverse,
            final float rightWheelSpeed,
            final boolean rightWheelInverse
    ) {
        final float normalizedLeftWheelSpeed = Math.max(0f, Math.min(1f, leftWheelSpeed));
        final float normalizedRightWheelSpeed = Math.max(0f, Math.min(1f, rightWheelSpeed));

        return sendCommand(COMMAND_MOVE, new byte[] {
                (byte)Math.round(normalizedLeftWheelSpeed * 255f),
                (byte)(leftWheelInverse ? 0x01 : 0x00),
                (byte)Math.round(normalizedRightWheelSpeed * 255f),
                (byte)(rightWheelInverse ? 0x01 : 0x00),
        });
    }

    /**
     * Sends the command and receives telemetry in response.
     */
    private synchronized Telemetry sendCommand(final byte[]... buffers) {
        if (socket == null) {
            return null;
        }

        final long startTimeMillis = System.currentTimeMillis();
        try {
            for (final byte[] buffer : buffers) {
                socket.getOutputStream().write(buffer);
            }
            return receiveTelemetry(startTimeMillis);
        } catch (final IOException e) {
            Log.e(LOG_TAG, "Failed to send command.", e);
            setSocket(null);
            return null;
        }
    }

    /**
     * Receives telemetry from the connected device.
     */
    private synchronized Telemetry receiveTelemetry(final long startTimeMillis) throws IOException {
        final ByteOrder byteOrder = receiveByteOrderMark();
        final double vcc = receiveInt(byteOrder) / 1000.0;
        return new Telemetry(System.currentTimeMillis() - startTimeMillis, vcc);
    }

    /**
     * Receives byte order mark and gets {@see ByteOrder}.
     */
    private synchronized ByteOrder receiveByteOrderMark() throws IOException {
        final int byte1 = socket.getInputStream().read();
        final int byte2 = socket.getInputStream().read();
        if ((byte1 == BYTE_ORDER_MARK[1]) && (byte2 == BYTE_ORDER_MARK[0])) {
            return ByteOrder.BIG_ENDIAN;
        }
        if ((byte1 == BYTE_ORDER_MARK[0]) && (byte2 == BYTE_ORDER_MARK[1])) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        throw new IOException(String.format("invalid byte order mark: %02x %02x", byte1, byte2));
    }

    /**
     * Receives {@see int} that is Arduino's long.
     */
    private synchronized int receiveInt(final ByteOrder byteOrder) throws IOException {
        final byte[] buffer = new byte[Integer.SIZE / 8];
        receiveBytes(buffer);
        return ByteBuffer.wrap(buffer).order(byteOrder).getInt();
    }

    /**
     * Performs blocking read.
     */
    private synchronized void receiveBytes(final byte[] buffer) throws IOException {
        final InputStream inputStream = socket.getInputStream();
        for (int offset = 0; offset < buffer.length; offset += 1) {
            if (inputStream.read(buffer, offset, 1) == 0) {
                throw new IOException("failed to read byte #" + offset);
            }
        }
    }

    public interface SocketChangedListener {

        void onSocketChanged(final VehicleConnection connection);
    }
}
