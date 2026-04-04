package com.familysafe.Handler;

import android.Manifest;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.familysafe.core.DataClassifier;
import com.familysafe.core.DataRepository;
import com.familysafe.core.UniversalBLEParser;
import com.familysafe.core.DeviceClassifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * SavedDevice: A universal class to manage and access most market watches.
 * Includes custom decoding for "Sens 2" and similar reverse-engineered protocols.
 */
public class SavedDevice {
    protected final String TAG;
    protected Context context;
    protected BluetoothGatt gatt;
    protected DeviceClassifier.DeviceType deviceType;
    protected HandlerCallback callback;

    // Operation Queue to prevent GATT collisions
    private final Queue<Runnable> operationQueue = new LinkedList<>();
    private boolean isOperationPending = false;

    public interface HandlerCallback {
        void onDataParsed(UniversalBLEParser.ParsedData data, byte[] rawData);
        void onMessage(String message);
    }

    public SavedDevice(Context context, BluetoothGatt gatt, DeviceClassifier.DeviceType deviceType, HandlerCallback callback) {
        this.context = context;
        this.gatt = gatt;
        this.deviceType = deviceType;
        this.callback = callback;
        this.TAG = "SavedDevice_" + deviceType.name();
    }

    public void setupDevice() {
        if (gatt == null) return;
        
        log("Analyzing device patterns for: " + deviceType.name());
        List<BluetoothGattService> services = gatt.getServices();
        
        for (BluetoothGattService service : services) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                UUID uuid = characteristic.getUuid();
                int props = characteristic.getProperties();
                
                // 1. Check Standard SIG patterns
                if (uuid.equals(UniversalBLEParser.CHAR_HEART_RATE_MEASUREMENT) ||
                    uuid.equals(UniversalBLEParser.CHAR_BATTERY_LEVEL) ||
                    uuid.equals(UniversalBLEParser.CHAR_SPO2_MEASUREMENT) ||
                    uuid.equals(UniversalBLEParser.CHAR_TEMPERATURE_MEASUREMENT) ||
                    uuid.equals(UniversalBLEParser.CHAR_STEP_COUNT_TOTAL)) {
                    
                    if (uuid.equals(UniversalBLEParser.CHAR_BATTERY_LEVEL)) {
                        readCharacteristic(characteristic);
                    }
                    
                    if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        enableNotification(characteristic);
                    }
                }
                
                // 2. Generic Notify (For Honor/Proprietary devices using FE EA packets)
                else if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    if (uuid.toString().startsWith("0000ff") || uuid.toString().startsWith("0000fe") || 
                        deviceType == DeviceClassifier.DeviceType.HONOR_DEVICE) {
                        log("Enabling generic notification for potential data: " + uuid);
                        enableNotification(characteristic);
                    }
                }
            }
        }
    }

    private synchronized void enqueueOperation(Runnable operation) {
        operationQueue.add(operation);
        if (!isOperationPending) {
            executeNextOperation();
        }
    }

    private synchronized void executeNextOperation() {
        Runnable operation = operationQueue.poll();
        if (operation != null) {
            isOperationPending = true;
            operation.run();
        }
    }

    public synchronized void onOperationComplete() {
        isOperationPending = false;
        // Small delay to allow GATT stack to breathe
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::executeNextOperation, 100);
    }

    public void handleData(BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        if (data == null || data.length == 0) return;

        UUID uuid = characteristic.getUuid();
        String address = gatt.getDevice().getAddress();

        // 1. Store Raw Data for Pattern Analysis
        DataRepository.getInstance().addRawData(address, uuid, data);

        // 2. Classify and Parse
        UniversalBLEParser.ParsedData parsedData = DataClassifier.classifyAndParse(deviceType, uuid, data);

        if (callback != null && parsedData != null) {
            callback.onDataParsed(parsedData, data);
        }
    }

    protected boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void enableNotification(BluetoothGattCharacteristic characteristic) {
        if (gatt == null || characteristic == null || !hasConnectPermission()) return;
        
        enqueueOperation(() -> {
            try {
                if (!gatt.setCharacteristicNotification(characteristic, true)) {
                    log("Failed to set notification locally for " + characteristic.getUuid());
                    onOperationComplete();
                    return;
                }

                UUID descriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUuid);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    if (!gatt.writeDescriptor(descriptor)) {
                        log("Failed to write descriptor for " + characteristic.getUuid());
                        onOperationComplete();
                    }
                } else {
                    log("CCCD Descriptor not found for " + characteristic.getUuid());
                    onOperationComplete();
                }
            } catch (SecurityException e) {
                log("Permission Error: Cannot enable notification");
                onOperationComplete();
            }
        });
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (gatt == null || characteristic == null || !hasConnectPermission()) return;
        
        enqueueOperation(() -> {
            try {
                if (!gatt.readCharacteristic(characteristic)) {
                    log("Failed to initiate read for " + characteristic.getUuid());
                    onOperationComplete();
                }
            } catch (SecurityException e) {
                log("Permission Error: Cannot read characteristic");
                onOperationComplete();
            }
        });
    }

    protected void log(String message) {
        Log.d(TAG, message);
        if (callback != null) callback.onMessage(message);
    }
}
