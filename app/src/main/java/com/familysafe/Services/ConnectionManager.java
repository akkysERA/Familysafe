package com.familysafe.Services;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.familysafe.core.DataClassifier;
import com.familysafe.core.DataRepository;
import com.familysafe.core.DeviceClassifier;
import com.familysafe.core.MongoUploader;
import com.familysafe.core.PatternAnalyzer;
import com.familysafe.core.UniversalBLEParser;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    
    // Standard Client Characteristic Configuration Descriptor UUID
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final ConnectionListener listener;
    private BluetoothGatt bluetoothGatt;
    private final DeviceClassifier deviceClassifier;
    private final DataRepository dataRepository;
    private final MongoUploader mongoUploader;
    private final PatternAnalyzer patternAnalyzer;
    private DeviceClassifier.DeviceType currentDeviceType = DeviceClassifier.DeviceType.UNKNOWN;

    private final Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedBlockingQueue<>();
    private boolean isWritingDescriptor = false;

    public interface ConnectionListener {
        void onConnectionStateChange(String status);
        void onLogMessage(String message);
        void onDataReceived(UUID uuid, byte[] data);
        void onScanningStateChange(boolean isScanning);
        void onDeviceClassified(DeviceClassifier.DeviceType type, BluetoothGatt gatt);
    }

    public ConnectionManager(Context context, ConnectionListener listener) {
        this.context = context;
        this.listener = listener;
        this.deviceClassifier = new DeviceClassifier(context);
        this.dataRepository = DataRepository.getInstance();
        this.mongoUploader = new MongoUploader();
        this.patternAnalyzer = new PatternAnalyzer();
    }

    public void connectToDevice(BluetoothDevice device) {
        listener.onLogMessage("Connecting to: " + device.getAddress());
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        } else {
            listener.onLogMessage("Connect permission missing.");
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                bluetoothGatt.disconnect();
            }
        }
    }

    public void enableNotifications(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return;
        }

        // Enable local notifications
        bluetoothGatt.setCharacteristicNotification(characteristic, true);

        // Enable remote notifications by writing to the CCCD
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            byte[] value = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 
                ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE 
                : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            
            descriptor.setValue(value);
            enqueueDescriptorWrite(descriptor);
        }
    }

    private void enqueueDescriptorWrite(BluetoothGattDescriptor descriptor) {
        descriptorWriteQueue.add(descriptor);
        if (!isWritingDescriptor) {
            processNextDescriptorWrite();
        }
    }

    private synchronized void processNextDescriptorWrite() {
        BluetoothGattDescriptor descriptor = descriptorWriteQueue.poll();
        if (descriptor != null) {
            isWritingDescriptor = true;
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                bluetoothGatt.writeDescriptor(descriptor);
            }
        } else {
            isWritingDescriptor = false;
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onConnectionStateChange("Status: Connected");
                listener.onLogMessage("Connected to GATT server.");
                listener.onScanningStateChange(false);
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    gatt.discoverServices();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener.onConnectionStateChange("Status: Disconnected");
                listener.onLogMessage("Disconnected from GATT server.");
                close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onLogMessage("Services discovered. Auto-subscribing...");
                
                currentDeviceType = deviceClassifier.classify(gatt);
                DeviceClassifier.DeviceCapabilities caps = deviceClassifier.getCapabilities(gatt);
                listener.onLogMessage(caps.toString());

                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        int props = characteristic.getProperties();
                        if ((props & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                            enableNotifications(characteristic);
                        }
                    }
                }

                listener.onDeviceClassified(currentDeviceType, gatt);
            } else {
                listener.onLogMessage("Service discovery failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            UUID uuid = characteristic.getUuid();
            String address = gatt.getDevice().getAddress();

            // 1. Store Raw Data for Pattern Analysis (Requirement 2)
            dataRepository.addRawData(address, uuid, data);

            // 2. Pattern Analysis (Requirement 2)
            patternAnalyzer.analyze(uuid, data);

            // 3. Classify and Parse (Requirement 1)
            UniversalBLEParser.ParsedData parsedData = DataClassifier.classifyAndParse(currentDeviceType, uuid, data);

            if (parsedData != null) {
                // 4. Store Decoded Data (Requirement 2)
                boolean hasChanged = dataRepository.addData(address, parsedData);

                // 5. Upload to MongoDB (Requirement 3) - Only if data is valid and significant
                if (hasChanged && parsedData.value > 0) {
                    mongoUploader.uploadData(address, parsedData);
                }

                listener.onLogMessage("Decoded: " + parsedData.toString());
            } else {
                // If not decoded, it's still stored as raw for PatternAnalyzer/Store
                listener.onLogMessage("Unrecognized data from: " + uuid);
            }

            listener.onDataReceived(uuid, data);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful: " + descriptor.getUuid());
                listener.onLogMessage("Subscribed to: " + descriptor.getCharacteristic().getUuid());
            } else {
                Log.e(TAG, "Descriptor write failed with status: " + status);
            }
            processNextDescriptorWrite();
        }
    };

    private void close() {
        if (bluetoothGatt == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothGatt.close();
        }
        bluetoothGatt = null;
        descriptorWriteQueue.clear();
        isWritingDescriptor = false;
        mongoUploader.close();
    }
}
