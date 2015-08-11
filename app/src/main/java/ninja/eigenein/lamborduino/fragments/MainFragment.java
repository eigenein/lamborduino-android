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
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import ninja.eigenein.joypad.JoypadView;
import ninja.eigenein.lamborduino.R;
import ninja.eigenein.lamborduino.core.VehicleConnection;
import ninja.eigenein.lamborduino.core.Telemetry;

public class MainFragment extends Fragment implements JoypadView.Listener {

    private static final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final ThreadPoolExecutor moveExecutor = (ThreadPoolExecutor)Executors.newFixedThreadPool(1);
    private final VehicleConnection vehicleConnection = new VehicleConnection();

    private TextView textViewDevice;
    private TextView textViewBattery;
    private TextView textViewPing;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(
            final @NonNull LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState
    ) {
        final View view = inflater.inflate(R.layout.fragment_main, container, false);

        textViewDevice = (TextView)view.findViewById(R.id.text_view_device);
        textViewBattery = (TextView)view.findViewById(R.id.text_view_battery);
        textViewPing = (TextView)view.findViewById(R.id.text_view_ping);

        ((JoypadView)view.findViewById(R.id.joypad)).setListener(this);
        view.findViewById(R.id.fab_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                startConnecting();
            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public void onStart() {
        super.onStart();

        vehicleConnection.setListener(new VehicleConnection.SocketChangedListener() {
            @Override
            public void onSocketChanged(final VehicleConnection connection) {
                final Telemetry telemetry = vehicleConnection.noop();
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateTelemetryView(telemetry);
                    }
                });
            }
        });
    }

    @Override
    public void onStop() {
        vehicleConnection.setListener(null);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onUp() {
        moveExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Telemetry telemetry = vehicleConnection.stop();
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateTelemetryView(telemetry);
                    }
                });
            }
        });
    }

    @Override
    public void onMove(final float distance, final float dx, final float dy) {
        if (moveExecutor.getActiveCount() != 0) {
            // Other command is already being executed.
            return;
        }
        moveExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Telemetry telemetry = vehicleConnection.move(Math.abs(dy), dy < 0f);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateTelemetryView(telemetry);
                    }
                });
            }
        });
    }

    private synchronized void startConnecting() {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
            return;
        }

        final List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
        if (devices.size() == 0) {
            Toast.makeText(getActivity(), R.string.toast_no_paired_devices, Toast.LENGTH_LONG).show();
            return;
        }

        final ArrayAdapter<String> dialogAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.select_dialog_item);
        for (final BluetoothDevice device : devices) {
            dialogAdapter.add(device.getName());
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.title_choose_vehicle)
                .setCancelable(true)
                .setAdapter(dialogAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final @NonNull DialogInterface dialog, final int which) {
                        new ConnectAsyncTask().execute(devices.get(which));
                    }
                })
                .show();
    }

    private void updateTelemetryView(final Telemetry telemetry) {
        if (telemetry != null) {
            final BluetoothDevice device = vehicleConnection.getSocket().getRemoteDevice();
            textViewDevice.setText(getString(R.string.telemetry_device, device.getName(), device.getAddress()));
            textViewBattery.setText(getString(R.string.telemetry_voltage, telemetry.vcc));
            textViewPing.setText(getString(R.string.telemetry_ping, telemetry.ping));
        } else {
            textViewDevice.setText(R.string.telemetry_not_available);
            textViewBattery.setText(R.string.telemetry_not_available);
            textViewPing.setText(R.string.telemetry_not_available);
        }
    }

    /**
     * Connects to the specified {@see BluetoothDevice}.
     */
    private class ConnectAsyncTask extends AsyncTask<BluetoothDevice, String, Void> {

        private final static int ATTEMPT_COUNT = 3;
        private final String LOG_TAG = ConnectAsyncTask.class.getSimpleName();

        private ProgressDialog progressDialog;
        private Exception exception = null;

        @Override
        protected void onPreExecute() {

            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setCancelable(false);
            progressDialog.setIndeterminate(true);
            progressDialog.setTitle("Connecting");
            progressDialog.show();
        }

        @Override
        protected synchronized Void doInBackground(final @NonNull BluetoothDevice... devices) {
            vehicleConnection.setSocket(null);
            publishProgress(devices[0].getName());
            for (int i = 0; i < ATTEMPT_COUNT; i += 1) {
                Log.i(LOG_TAG, "attempt #" + i);
                try {
                    final BluetoothSocket socket = devices[0].createRfcommSocketToServiceRecord(SERIAL_UUID);
                    socket.connect();
                    vehicleConnection.setSocket(socket);
                } catch (final IOException e) {
                    exception = e;
                    continue;
                }
                exception = null;
                break;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(final String... values) {
            progressDialog.setMessage(getString(R.string.dialog_connecting, values[0]));
        }

        @Override
        protected void onPostExecute(final @NonNull Void result) {
            progressDialog.dismiss();
            progressDialog = null;

            if (exception == null) {
                Toast.makeText(getActivity(), R.string.toast_connected, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}
