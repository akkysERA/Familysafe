package com.familysafe;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.familysafe.Handler.SavedDevice;
import com.familysafe.core.DataClassifier;
import com.familysafe.core.DataRepository;
import com.familysafe.core.DeviceClassifier;
import com.familysafe.core.MongoUploader;
import com.familysafe.core.UniversalBLEParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FamilySafe_Main";
    private static final int PERMISSION_REQUEST_CODE = 101;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private String connectedDeviceAddress;
    private boolean isScanning = false;
    private final Handler handler = new Handler();

    private final List<BluetoothDevice> deviceList = new ArrayList<>();
    private final Set<String> deviceAddresses = new HashSet<>();
    private ArrayAdapter<String> deviceAdapter;
    private final List<String> deviceDisplayList = new ArrayList<>();

    private TextView statusTextView;
    private TextView logTextView;
    private View logTitleTextView;
    private View logScrollView;
    private Button scanButton;
    private ListView deviceListView;
    private View scanContainer;
    private Button disconnectButton;

    // Device Details UI
    private View deviceDetailsContainer;
    private TextView detailNameTextView;
    private TextView detailMacTextView;
    private TextView detailBatteryTextView;
    private TextView detailStepsTextView;
    private TextView detailHeartRateTextView;
    private TextView detailSpO2TextView;
    private TextView detailDataTextView;

    private SavedDevice activeHandler;
    private MongoUploader mongoUploader;

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                
                if (device != null) {
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        addLog("Device paired (Bonded): " + device.getAddress());
                        // Now that it's bonded, we can try discovering services again if needed
                        if (bluetoothGatt != null && device.equals(bluetoothGatt.getDevice())) {
                            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                bluetoothGatt.discoverServices();
                            }
                        }
                    } else if (bondState == BluetoothDevice.BOND_BONDING) {
                        addLog("Pairing in progress with: " + device.getAddress());
                    } else if (bondState == BluetoothDevice.BOND_NONE) {
                        addLog("Device not paired (Bond removed/failed): " + device.getAddress());
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        initializeBluetooth();
        checkPermissions();
        mongoUploader = new MongoUploader();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bondReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bondReceiver);
        if (mongoUploader != null) {
            mongoUploader.close();
        }
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
        }
    }

    private void initializeUI() {
        statusTextView = findViewById(R.id.statusTextView);
        logTextView = findViewById(R.id.logTextView);
        logTitleTextView = findViewById(R.id.logTitleTextView);
        logScrollView = findViewById(R.id.logScrollView);
        scanButton = findViewById(R.id.scanButton);
        deviceListView = findViewById(R.id.deviceListView);
        scanContainer = findViewById(R.id.scanContainer);
        disconnectButton = findViewById(R.id.disconnectButton);

        deviceDetailsContainer = findViewById(R.id.deviceDetailsContainer);
        detailNameTextView = findViewById(R.id.detailNameTextView);
        detailMacTextView = findViewById(R.id.detailMacTextView);
        detailBatteryTextView = findViewById(R.id.detailBatteryTextView);
        detailStepsTextView = findViewById(R.id.detailStepsTextView);
        detailHeartRateTextView = findViewById(R.id.detailHeartRateTextView);
        detailSpO2TextView = findViewById(R.id.detailSpO2TextView);
        detailDataTextView = findViewById(R.id.detailDataTextView);

        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceDisplayList);
        deviceListView.setAdapter(deviceAdapter);

        scanButton.setOnClickListener(v -> {
            if (!isScanning) {
                startScan();
            } else {
                stopScan();
            }
        });

        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = deviceList.get(position);
            connectToDevice(device);
        });

        disconnectButton.setOnClickListener(v -> {
            if (bluetoothGatt != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    bluetoothGatt.disconnect();
                }
            }
        });
    }

    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            addLog("Bluetooth is not enabled!");
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            addLog("All necessary permissions granted.");
        }
    }

    private void startScan() {
        if (bleScanner == null) {
            addLog("BLE Scanner not available.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addLog("Bluetooth Scan permission missing.");
            return;
        }

        deviceList.clear();
        deviceAddresses.clear();
        deviceDisplayList.clear();
        deviceAdapter.notifyDataSetChanged();
        
        isScanning = true;
        scanButton.setText("Stop Scan");
        addLog("Starting BLE Scan...");

        handler.postDelayed(this::stopScan, 10000);
        bleScanner.startScan(scanCallback);
    }

    private void stopScan() {
        if (isScanning && bleScanner != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                bleScanner.stopScan(scanCallback);
            }
            isScanning = false;
            runOnUiThread(() -> {
                scanButton.setText("Start Scan");
                addLog("Scan stopped.");
            });
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                synchronized (deviceList) {
                    if (!deviceAddresses.contains(device.getAddress())) {
                        deviceAddresses.add(device.getAddress());
                        deviceList.add(device);
                        String name = "Unknown";
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            name = device.getName();
                        }
                        String finalName = (name != null ? name : "Unknown Device");
                        deviceDisplayList.add(finalName + "\n" + device.getAddress());
                        runOnUiThread(() -> deviceAdapter.notifyDataSetChanged());
                        addLog("Found: " + finalName);
                    }
                }
            }
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        addLog("Preparing to connect to: " + device.getAddress());
        
        // CRITICAL: Stop scan before connecting to avoid Error 133
        if (isScanning) {
            stopScan();
            // Give the radio a small breather after stopping scan
            handler.postDelayed(() -> performConnect(device), 500);
        } else {
            performConnect(device);
        }
    }

    private void performConnect(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            connectedDeviceAddress = device.getAddress();

            // Update UI with initial device info
            runOnUiThread(() -> {
                scanContainer.setVisibility(View.GONE);
                deviceDetailsContainer.setVisibility(View.VISIBLE);
                String name = device.getName();
                detailNameTextView.setText("Device: " + (name != null ? name : "Unknown"));
                detailMacTextView.setText("MAC: " + device.getAddress());
                detailBatteryTextView.setText("Battery: --%");
                detailDataTextView.setText("Latest Data: Connecting...");
            });

            if (bluetoothGatt != null) {
                addLog("Closing existing connection...");
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
            
            addLog("Initiating GATT connection...");
            // transport is set to LE for better stability on modern Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                bluetoothGatt = device.connectGatt(this, false, gattCallback);
            }
        } else {
            addLog("Connect permission missing.");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> {
                    statusTextView.setText("Status: Connected");
                    statusTextView.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_connected));
                });
                addLog("Connected to GATT server.");
                
                // Check bond state before discovering services
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    int bondState = gatt.getDevice().getBondState();
                    if (bondState == BluetoothDevice.BOND_NONE || bondState == BluetoothDevice.BOND_BONDED) {
                        gatt.discoverServices();
                    } else if (bondState == BluetoothDevice.BOND_BONDING) {
                        addLog("Waiting for pairing to complete...");
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    statusTextView.setText("Status: Disconnected");
                    statusTextView.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_disconnected));
                    deviceDetailsContainer.setVisibility(View.GONE);
                    scanContainer.setVisibility(View.VISIBLE);
                });
                activeHandler = null;
                addLog("Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Services discovered.");
                runOnUiThread(() -> {
                    DeviceClassifier classifier = new DeviceClassifier(MainActivity.this);
                    DeviceClassifier.DeviceType type = classifier.classify(gatt);
                    addLog("Detected device type: " + type.name());
                    
                    activeHandler = createHandler(type, gatt);
                    if (activeHandler != null) {
                        activeHandler.setupDevice();
                    }
                });
            } else {
                addLog("Service discovery failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (activeHandler != null) {
                activeHandler.handleData(characteristic);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (activeHandler != null) {
                activeHandler.onOperationComplete();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (activeHandler != null) {
                activeHandler.onOperationComplete();
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    activeHandler.handleData(characteristic);
                }
            }
        }
    };

    private SavedDevice createHandler(DeviceClassifier.DeviceType type, BluetoothGatt gatt) {
        SavedDevice.HandlerCallback callback = new SavedDevice.HandlerCallback() {
            @Override
            public void onDataParsed(UniversalBLEParser.ParsedData data, byte[] rawData) {
                processIncomingData(data, rawData);
            }

            @Override
            public void onMessage(String message) {
                addLog(message);
            }
        };

        return new SavedDevice(this, gatt, type, callback);
    }

    private void processIncomingData(UniversalBLEParser.ParsedData data, byte[] rawData) {
        if (data == null) return;

        // Save to DataRepository. returns true only if value actually changed
        boolean hasChanged = DataRepository.getInstance().addData(connectedDeviceAddress, data);
        
        if (!hasChanged) return;

        runOnUiThread(() -> {
            String valueText = data.value + " " + data.unit;
            detailDataTextView.setText("Latest Data: [" + data.type + "] " + valueText);

            // Update specific sensor UI fields
            if (UniversalBLEParser.TYPE_HEART_RATE.equals(data.type)) {
                detailHeartRateTextView.setText(String.valueOf((int)data.value));
            } else if (UniversalBLEParser.TYPE_STEPS.equals(data.type)) {
                detailStepsTextView.setText(String.valueOf((int)data.value));
            } else if (UniversalBLEParser.TYPE_SPO2.equals(data.type)) {
                detailSpO2TextView.setText((int)data.value + "%");
            } else if (UniversalBLEParser.TYPE_BATTERY.equals(data.type)) {
                detailBatteryTextView.setText((int)data.value + "%");
            } else if (UniversalBLEParser.TYPE_WATCH_DATA.equals(data.type)) {
                // Parse the composite unit string "BPM | SpO2: 62%"
                detailHeartRateTextView.setText(String.valueOf((int)data.value));
                String spo2Part = data.unit.substring(data.unit.indexOf("|") + 2);
                detailSpO2TextView.setText(spo2Part.replace("SpO2: ", ""));
            }

            // Also upload to MongoDB - Only if value is significant and changed
            // Note: hasChanged check is already handled above by the return statement
            if (mongoUploader != null && data.value > 0) {
                mongoUploader.uploadData(connectedDeviceAddress, data);
            }
        });
    }

    private void addLog(String message) {
        Log.d(TAG, message);
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        runOnUiThread(() -> {
            String currentLog = logTextView.getText().toString();
            logTextView.setText("[" + timeStamp + "] " + message + "\n" + currentLog);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_show_log) {
            if (logScrollView.getVisibility() == View.VISIBLE) {
                logScrollView.setVisibility(View.GONE);
                logTitleTextView.setVisibility(View.GONE);
                item.setTitle("Show System Log");
            } else {
                logScrollView.setVisibility(View.VISIBLE);
                logTitleTextView.setVisibility(View.VISIBLE);
                item.setTitle("Hide System Log");
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                addLog("Permissions granted.");
            } else {
                Toast.makeText(this, "Permissions are required for BLE features", Toast.LENGTH_LONG).show();
            }
        }
    }
}
