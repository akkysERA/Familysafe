package com.familysafe.core;

import android.Manifest;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeviceClassifier {
    private static final String TAG = "DeviceClassifier";

    // Standard SIG Service UUIDs
    public static final UUID SERVICE_HEART_RATE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    public static final UUID SERVICE_BATTERY = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID SERVICE_HEALTH_THERMOMETER = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
    public static final UUID SERVICE_STEP_COUNTER = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb");

    public enum DeviceType {
        GENERIC_BLE,
        HONOR_DEVICE,
        FITBIT_DEVICE,
        GARMIN_DEVICE,
        SENS2_WATCH,
        UNKNOWN
    }

    public static class DeviceCapabilities {
        public boolean hasHeartRate = false;
        public boolean hasBatteryLevel = false;
        public boolean hasThermometer = false;
        public boolean hasStepCounter = false;
        public List<String> customServices = new ArrayList<>();

        @Override
        public String toString() {
            return "Capabilities: [HR=" + hasHeartRate + 
                   ", Battery=" + hasBatteryLevel + 
                   ", Temp=" + hasThermometer + 
                   ", Steps=" + hasStepCounter + "]";
        }
    }

    private final Context context;

    public DeviceClassifier(Context context) {
        this.context = context;
    }

    public DeviceType classify(BluetoothGatt gatt) {
        String deviceName = null;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            deviceName = gatt.getDevice().getName();
        }

        List<BluetoothGattService> services = gatt.getServices();
        Log.d(TAG, "Classifying device: " + (deviceName != null ? deviceName : "Unknown Name"));

        String macAddress = gatt.getDevice().getAddress();
        // if (Sens2Parser.TARGET_MAC.equalsIgnoreCase(macAddress)) {
        //     return DeviceType.SENS2_WATCH;
        // }

        if (isHonorDevice(deviceName, services)) {
            return DeviceType.HONOR_DEVICE;
        } else if (isFitbitDevice(deviceName)) {
            return DeviceType.FITBIT_DEVICE;
        } else if (isGarminDevice(deviceName)) {
            return DeviceType.GARMIN_DEVICE;
        } else if (isGenericDevice(services)) {
            return DeviceType.GENERIC_BLE;
        }

        return DeviceType.UNKNOWN;
    }

    public DeviceCapabilities getCapabilities(BluetoothGatt gatt) {
        DeviceCapabilities caps = new DeviceCapabilities();
        List<BluetoothGattService> services = gatt.getServices();

        for (BluetoothGattService service : services) {
            UUID uuid = service.getUuid();
            if (uuid.equals(SERVICE_HEART_RATE)) caps.hasHeartRate = true;
            else if (uuid.equals(SERVICE_BATTERY)) caps.hasBatteryLevel = true;
            else if (uuid.equals(SERVICE_HEALTH_THERMOMETER)) caps.hasThermometer = true;
            else if (uuid.equals(SERVICE_STEP_COUNTER)) caps.hasStepCounter = true;
            else {
                caps.customServices.add(uuid.toString());
            }
        }
        return caps;
    }

    private boolean isHonorDevice(String name, List<BluetoothGattService> services) {
        if (name != null && (name.contains("Honor") || name.contains("HUAWEI") || name.contains("Band") || name.contains("Magic"))) {
            return true;
        }
        for (BluetoothGattService service : services) {
            String uuid = service.getUuid().toString().toLowerCase();
            if (uuid.contains("0000fee7") || uuid.contains("0000fe95")) {
                return true;
            }
        }
        return false;
    }

    private boolean isFitbitDevice(String name) {
        return name != null && name.toLowerCase().contains("fitbit");
    }

    private boolean isGarminDevice(String name) {
        return name != null && name.toLowerCase().contains("garmin");
    }

    private boolean isGenericDevice(List<BluetoothGattService> services) {
        return services != null && !services.isEmpty();
    }
}
