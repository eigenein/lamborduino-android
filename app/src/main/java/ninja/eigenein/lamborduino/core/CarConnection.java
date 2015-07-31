package ninja.eigenein.lamborduino.core;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CarConnection {

    private static final String LOG_TAG = CarConnection.class.getSimpleName();

    private static final int[] BYTE_ORDER_MARK = {0xFF, 0xFE};
    private static final byte[] COMMAND_NOOP = {0x01};

    private SocketChangedListener listener;
    private BluetoothSocket socket;

    public void setListener(final SocketChangedListener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return socket != null;
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

    public synchronized Telemetry noop() {
        return sendCommand(COMMAND_NOOP);
    }

    private synchronized Telemetry sendCommand(final byte[]... buffers) {
        if (socket == null) {
            return null;
        }
        try {
            for (final byte[] buffer : buffers) {
                socket.getOutputStream().write(buffer);
            }
            return receiveTelemetry();
        } catch (final IOException e) {
            Log.e(LOG_TAG, "Failed to send command.", e);
            setSocket(null);
            return null;
        }
    }

    private synchronized Telemetry receiveTelemetry() throws IOException {
        final ByteOrder byteOrder = receiveByteOrderMark();
        final double vcc = receiveInt(byteOrder) / 1000.0;
        return new Telemetry(vcc);
    }

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

    private synchronized int receiveInt(final ByteOrder byteOrder) throws IOException {
        final byte[] buffer = new byte[4];
        if (socket.getInputStream().read(buffer) != buffer.length) {
            throw new IOException("failed to read integer");
        }
        return ByteBuffer.wrap(buffer).order(byteOrder).getInt();
    }

    public interface SocketChangedListener {

        void onSocketChanged(final CarConnection connection);
    }
}
