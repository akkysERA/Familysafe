package com.familysafe.Handler;

import android.Manifest;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.familysafe.core.DeviceClassifier.DeviceType;
import com.familysafe.core.UniversalBLEParser;
import java.util.UUID;

public abstract class DeviceHandler {
    protected final String TAG;
    protected Context context;
    protected BluetoothGatt gatt;
    protected DeviceType deviceType;
    protected HandlerCallback callback;

    public interface HandlerCallback {
        void onDataParsed(UniversalBLEParser.ParsedData data, byte[] rawData);
        void onMessage(String message);
    }

    public DeviceHandler(Context context, BluetoothGatt gatt, DeviceType deviceType, HandlerCallback callback) {
        this.context = context;
        this.gatt = gatt;
        this.deviceType = deviceType;
        this.callback = callback;
        this.TAG = "Handler_" + deviceType.name();
    }

    public abstract void setupDevice();
    public abstract void handleData(BluetoothGattCharacteristic characteristic);

    protected boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    protected void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (gatt == null || characteristic == null || !hasConnectPermission()) return;
        try {
            gatt.readCharacteristic(characteristic);
        } catch (SecurityException e) {
            log("SecurityException: Cannot read characteristic");
        }
    }

    protected void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (gatt == null || characteristic == null || !hasConnectPermission()) return;
        try {
            characteristic.setValue(data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                gatt.writeCharacteristic(characteristic);
            }
        } catch (SecurityException e) {
            log("SecurityException: Cannot write characteristic");
        }
    }

    protected void enableNotification(BluetoothGattCharacteristic characteristic) {
        if (gatt == null || characteristic == null || !hasConnectPermission()) return;
        try {
            gatt.setCharacteristicNotification(characteristic, true);
            UUID descriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUuid);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
                log("Enabled notification for: " + characteristic.getUuid());
            }
        } catch (SecurityException e) {
            log("SecurityException: Cannot enable notification");
        }
    }

    protected void log(String message) {
        Log.d(TAG, message);
        if (callback != null) callback.onMessage(message);
    }
}
