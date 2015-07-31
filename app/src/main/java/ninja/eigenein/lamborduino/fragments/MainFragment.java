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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ninja.eigenein.lamborduino.R;
import ninja.eigenein.lamborduino.core.CarConnection;
import ninja.eigenein.lamborduino.core.Telemetry;

public class MainFragment extends Fragment {

    private static final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private MenuItem connectMenuItem;
    private MenuItem updateVccMenuItem;

    private CarConnection carConnection = new CarConnection();

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
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        connectMenuItem = menu.findItem(R.id.menu_item_connect);
        updateVccMenuItem = menu.findItem(R.id.menu_item_update_vcc);
        updateView();
    }

    @Override
    public void onStart() {
        super.onStart();

        carConnection.setListener(new CarConnection.SocketChangedListener() {
            @Override
            public void onSocketChanged(final CarConnection connection) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateView();
                    }
                });
            }
        });
    }

    @Override
    public void onStop() {
        carConnection.setListener(null);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_item_connect:
                connect();
                return true;

            case R.id.menu_item_update_vcc:
                final Telemetry telemetry = carConnection.noop();
                if (telemetry != null) {
                    getActivity().setTitle(getString(R.string.title_vcc, telemetry.vcc));
                } else {
                    Toast.makeText(getActivity(), R.string.toast_not_connected, Toast.LENGTH_SHORT).show();
                }
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
            Toast.makeText(getActivity(), R.string.toast_no_paired_devices, Toast.LENGTH_LONG).show();
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
                    public void onClick(final @NonNull DialogInterface dialog, final int which) {
                        new ConnectAsyncTask().execute(devices.get(which));
                    }
                })
                .show();
    }

    private void updateView() {
        if (carConnection.isConnected()) {
            connectMenuItem.setIcon(R.drawable.ic_bluetooth_connected_white_24dp);
            updateVccMenuItem.setIcon(R.drawable.ic_battery_std_white_24dp);
            getActivity().setTitle(getString(R.string.title_vcc, carConnection.noop().vcc));
        } else {
            connectMenuItem.setIcon(R.drawable.ic_bluetooth_white_24dp);
            updateVccMenuItem.setIcon(R.drawable.ic_battery_unknown_white_24dp);
            getActivity().setTitle(R.string.app_name);
        }
    }

    private class ConnectAsyncTask extends AsyncTask<BluetoothDevice, String, Void> {

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
            publishProgress(devices[0].getName());
            try {
                final BluetoothSocket socket = devices[0].createRfcommSocketToServiceRecord(SERIAL_UUID);
                socket.connect();
                carConnection.setSocket(socket);
            } catch (final IOException e) {
                exception = e;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(final String... values) {
            progressDialog.setMessage(getString(R.string.dialog_connecting, values[0]));
        }

        @Override
        protected void onPostExecute(final @NonNull Void result) {
            progressDialog.hide();
            progressDialog = null;

            if (exception == null) {
                Toast.makeText(getActivity(), R.string.toast_connected, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}
