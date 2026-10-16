package com.example.myfirstapplication;

import static android.system.Os.connect;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.location.LocationManager;
import androidx.core.location.LocationManagerCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    Button connectButton;
    Button disconnectButton;
    Button forwardButton;
    Button backwardButton;
    Button turnLeftForwardButton;
    Button turnRightForwardButton;
    TextView statusLabel;
    ArrayList<String> deviceNames = new ArrayList<>();
    ArrayAdapter<String> adapter;
    ListView deviceListLabel;

    BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter(); // Chip del bluetooth del teléfono
    private static final int REQUEST_DISCOVERY = 1002;
    private ActivityResultLauncher<Intent> enableBtLauncher;

    public static Boolean bluetoothActive = false;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();

    private ActivityResultLauncher<Intent> locationSettingsLauncher;
    private BluetoothSocket btSocket;
    private InputStream inputStream;
    private OutputStream outputStream;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        connectButton = (Button) findViewById(R.id.connectButton);
        disconnectButton = (Button) findViewById(R.id.disconnectButton);
        forwardButton = (Button) findViewById(R.id.forButton);
        backwardButton= (Button) findViewById(R.id.backButton);
        turnLeftForwardButton= (Button) findViewById(R.id.leftButton);
        turnRightForwardButton= (Button) findViewById(R.id.rightButton);

        statusLabel = (TextView) findViewById(R.id.textView);
        deviceListLabel = findViewById(R.id.deviceListLabel);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Por si el bluetooth está ya encendido en el Android al encender la app
        if (bluetooth != null && bluetooth.isEnabled()) {
            bluetoothActive = true;
        } else {
            bluetoothActive = false;
        }

        // Cuando el usuario responde y vuelve a la app lo vuelve a comprobar

        enableBtLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        bluetoothActive = true;
                        // El usuario aceptó encender el Bluetooth
                        this.statusLabel.setText("Bluetooth ENABLED");
                        Toast.makeText(this, "Bluetooth encendido por el usuario", Toast.LENGTH_SHORT).show();
                        startDiscovery();
                    } else {
                        bluetoothActive = false;
                        // El usuario canceló o rechazó encenderlo
                        this.statusLabel.setText("User CANCELLED");
                        Toast.makeText(this, "El usuario canceló la activación", Toast.LENGTH_SHORT).show();
                    }

                }
        );


        locationSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (isLocationEnabled()) {
                        startDiscovery();
                    } else {
                        statusLabel.setText("Ubicación desactivada");
                        Toast.makeText(this,
                                "Sin ubicación no se pueden buscar dispositivos",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        // Para el device list
        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                deviceNames
        );
        deviceListLabel.setAdapter(adapter);
        deviceListLabel.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = deviceList.get(position);
            connect(device);
        });
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return LocationManagerCompat.isLocationEnabled(locationManager);
    }

    public void onClickConnectButton(View view) {
        statusLabel.setText("Connect pressed");

        if (bluetooth == null) {
            bluetoothActive = false;
            Toast.makeText(this, "El dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        // Comprueba todos los permisos
        if (!hasDiscoveryPermission()) {
            requestDiscoveryPermission();
            return;
        }

        // Comprueba bluetooth
        if (!bluetooth.isEnabled()) {
            bluetoothActive = false;
            enableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        bluetoothActive = true;
        startDiscovery();
    }

    private void startDiscovery(){
        // Comprueba todos los permisos
        if (!hasDiscoveryPermission()) {
            requestDiscoveryPermission();
            return;
        }

        if (!isLocationEnabled()) {
            Toast.makeText(this,
                    "Debes activar la ubicación para buscar dispositivos Bluetooth",
                    Toast.LENGTH_LONG).show();

            // Abrir los ajustes para encender la ubicación
            Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            locationSettingsLauncher.launch(intent);
            return; // Vuelve al locationSettingsLauncher, y este luego vuelve a startDiscovery() si ya está conectada
        }

        deviceList.clear();
        deviceNames.clear();
        adapter.notifyDataSetChanged();

        statusLabel.setText("Dispositivos encontrados:");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryResult, filter);

        // Pide volver a comprobarlo porque si no da error
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {

            if (bluetooth.isDiscovering()) {
                bluetooth.cancelDiscovery();
            }

            boolean ok = bluetooth.startDiscovery();
            if (!ok) {
                Toast.makeText(this,
                        "Could not start discovery", Toast.LENGTH_SHORT).show();
            }

        }

    }

    private boolean hasDiscoveryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Versión >= 12
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else { // Versión más antigua: Permiso de ubicación
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestDiscoveryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_DISCOVERY);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_DISCOVERY);
        }
    }

    // Callback que el SO ejecuta cuando el usuario responde a la ventana flotante
    // que pide un permiso en tiempo de ejecución.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_DISCOVERY) {
            if (hasDiscoveryPermission()) {
                onClickConnectButton(null);
            } else {
                Toast.makeText(this,
                        "Se necesitan permisos para buscar dispositivos Bluetooth",
                        Toast.LENGTH_LONG).show();
                statusLabel.setText("Permisos denegados");
            }
        }
    }

    private final BroadcastReceiver discoveryResult = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); // == ACTION_FOUND si la antena descubre un nuevo dispositivo Bluetooth

            // Si se ha encontrado un dispositivo
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                // Guardamos el nombre del dispositivo descubierto
                String remoteDeviceName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);

                // Guardamos el objeto Java del dispositivo descubierto, para poder conectar
                BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // Leemos la intensidad de la radio con respecto a este dispositivo bluetooth
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                // Guardamos el dispositivo encontrado en la lista (si no es nulo)
                if (remoteDevice != null && !deviceList.contains(remoteDevice)) {
                    deviceList.add(remoteDevice);

                    String name = (remoteDeviceName != null) ? remoteDeviceName : "Sin nombre";

                    String mac = remoteDevice.getAddress();

                    deviceNames.add(name + ": " + mac);
                    adapter.notifyDataSetChanged();
                }

                // Mostramos el evento en el Log.
                Log.d("MyFirstApp", "Discovered " + remoteDeviceName);
                Log.d("MyFirstApp", "RSSI " + rssi + " dBm");
            }
        }
    };

//    protected void connect(BluetoothDevice device) {
//        try {
//            btSocket = device.createRfcommSocketToServiceRecord(
//                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
//            btSocket.connect();
//            Log.d("connect"
//                    ,
//                    "Client connected");
//            inputStream = btSocket.getInputStream();
//            outputStream = btSocket.getOutputStream();
//        }catch (Exception e) {
//            Log.e("ERROR: connect"
//                    ,
//                    ">>", e);
//        }
//    }
protected void connect(BluetoothDevice device) {
    new Thread(() -> {
        try {
            btSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

            btSocket.connect();

            Log.d("connect", "Client connected");

            inputStream = btSocket.getInputStream();
            outputStream = btSocket.getOutputStream();

            runOnUiThread(() -> {
                statusLabel.setText("Conectado");
            });

        } catch (Exception e) {
            Log.e("ERROR: connect", ">>", e);

            runOnUiThread(() -> {
                statusLabel.setText("Error de conexión");
            });
        }
    }).start();
}

}