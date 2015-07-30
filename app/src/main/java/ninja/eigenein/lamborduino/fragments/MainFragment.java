package ninja.eigenein.lamborduino.fragments;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ninja.eigenein.lamborduino.Commands;
import ninja.eigenein.lamborduino.R;

public class MainFragment extends Fragment {

    private static final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int MAGIC = 0x55;

    private TextView connectionTextView;
    private TextView vccTextView;

    private BluetoothSocket socket;
    private double vcc;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState
    ) {
        final View view = inflater.inflate(R.layout.fragment_main, container, false);
        connectionTextView = (TextView)view.findViewById(R.id.text_view_connection);
        vccTextView = (TextView)view.findViewById(R.id.text_view_vcc);
        vccTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (socket != null) {
                    updateTelemetry();
                }
            }
        });
        return view;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_connect:
                connect();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private synchronized void connect() {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
            return;
        }

        final List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
        if (devices.size() == 0) {
            Toast.makeText(getActivity(), "No paired devices.", Toast.LENGTH_LONG).show();
            return;
        }

        final ArrayAdapter<String> dialogAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.select_dialog_item);
        for (final BluetoothDevice device : devices) {
            dialogAdapter.add(device.getName());
        }
        new AlertDialog.Builder(getActivity())
                .setTitle("Choose paired device")
                .setCancelable(true)
                .setAdapter(dialogAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        new ConnectAsyncTask().execute(devices.get(which));
                    }
                })
                .show();
    }

    private synchronized void onConnected(final BluetoothSocket socket) {
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException e) {
                // Do nothing.
            }
        }

        this.socket = socket;
        connectionTextView.setText(socket.getRemoteDevice().getName());
        connectionTextView.setBackgroundResource(R.color.light_green_50);
        updateTelemetry();
    }

    private synchronized void onDisconnected(final Exception exception) {
        socket = null;
        connectionTextView.setText("Offline");
        connectionTextView.setBackgroundResource(R.color.red_50);
        Toast.makeText(getActivity(), exception.getMessage(), Toast.LENGTH_LONG).show();
    }

    private synchronized void updateTelemetry() {
        try {
            sendNoop();
            readTelemetry();
        } catch (final IOException e) {
            onDisconnected(e);
            return;
        }
        vccTextView.setText(Double.toString(vcc) + "V");
    }

    /**
     * Sends no operation.
     */
    private synchronized void sendNoop() throws IOException {
        socket.getOutputStream().write(Commands.NOOP);
    }

    /**
     * Reads telemetry from the connected device.
     */
    private synchronized void readTelemetry() throws IOException {
        if (socket == null) {
            throw new IOException("no socket");
        }
        if (socket.getInputStream().read() != MAGIC) {
            throw new IOException("invalid magic");
        }

        final byte[] vcc = new byte[4];
        if (socket.getInputStream().read(vcc) != vcc.length) {
            throw new IOException("failed to read VCC");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(vcc);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.vcc = buffer.getInt() / 1000.0;
    }

    private class ConnectAsyncTask extends AsyncTask<BluetoothDevice, String, BluetoothSocket> {

        private ProgressDialog progressDialog;
        private Exception exception;

        @Override
        protected void onPreExecute() {

            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setCancelable(false);
            progressDialog.setIndeterminate(true);
            progressDialog.setTitle("Connecting");
            progressDialog.show();
        }

        @Override
        protected synchronized BluetoothSocket doInBackground(final BluetoothDevice... devices) {
            publishProgress(devices[0].getName());
            try {
                final BluetoothSocket socket = devices[0].createRfcommSocketToServiceRecord(SERIAL_UUID);
                socket.connect();
                return socket;
            } catch (final IOException e) {
                exception = e;
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(final String... values) {
            progressDialog.setMessage(values[0]);
        }

        @Override
        protected void onPostExecute(final BluetoothSocket socket) {
            progressDialog.hide();
            progressDialog = null;

            if (socket != null) {
                onConnected(socket);
            } else {
                onDisconnected(exception);
            }
        }
    }
}
