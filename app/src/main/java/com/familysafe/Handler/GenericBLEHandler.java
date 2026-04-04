package com.familysafe.Handler;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import com.familysafe.core.DeviceClassifier;
import com.familysafe.core.UniversalBLEParser;
import java.util.List;
import java.util.UUID;

public class GenericBLEHandler extends DeviceHandler {

    public GenericBLEHandler(Context context, BluetoothGatt gatt, DeviceClassifier.DeviceType deviceType, HandlerCallback callback) {
        super(context, gatt, deviceType, callback);
    }

    @Override
    public void setupDevice() {
        if (gatt == null) return;
        
        List<BluetoothGattService> services = gatt.getServices();
        for (BluetoothGattService service : services) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                UUID uuid = characteristic.getUuid();
                
                if (uuid.equals(UniversalBLEParser.CHAR_HEART_RATE_MEASUREMENT) ||
                    uuid.equals(UniversalBLEParser.CHAR_BATTERY_LEVEL) ||
                    uuid.equals(UniversalBLEParser.CHAR_TEMPERATURE_MEASUREMENT) ||
                    uuid.equals(UniversalBLEParser.CHAR_STEP_COUNT_TOTAL)) {
                    
                    enableNotification(characteristic);
                }
            }
        }
    }

    @Override
    public void handleData(BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        if (data == null || data.length == 0) return;

        UniversalBLEParser.ParsedData parsedData = UniversalBLEParser.parse(characteristic.getUuid(), data);
        if (callback != null) {
            callback.onDataParsed(parsedData, data);
        }
    }
}
