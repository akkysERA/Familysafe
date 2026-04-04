package com.familysafe.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.UUID;

public class DataRepository {
    
    // In-memory storage (singleton)
    private static DataRepository instance;
    private final Map<String, List<UniversalBLEParser.ParsedData>> deviceDataMap = new HashMap<>();
    private final Map<String, UniversalBLEParser.ParsedData> lastDataMap = new HashMap<>();
    private final Map<String, List<RawDataPacket>> rawPacketMap = new HashMap<>();

    public static class RawDataPacket {
        public UUID uuid;
        public byte[] data;
        public long timestamp;

        public RawDataPacket(UUID uuid, byte[] data, long timestamp) {
            this.uuid = uuid;
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, Long> lastUpdateTimeMap = new HashMap<>();

    private DataRepository() {}

    public static synchronized DataRepository getInstance() {
        if (instance == null) {
            instance = new DataRepository();
        }
        return instance;
    }

    public synchronized void addRawData(String deviceAddress, UUID uuid, byte[] data) {
        if (!rawPacketMap.containsKey(deviceAddress)) {
            rawPacketMap.put(deviceAddress, new ArrayList<>());
        }
        List<RawDataPacket> rawList = rawPacketMap.get(deviceAddress);
        if (rawList != null) {
            rawList.add(new RawDataPacket(uuid, data, System.currentTimeMillis()));
            if (rawList.size() > 500) rawList.remove(0);
        }
    }

    public synchronized boolean addData(String deviceAddress, UniversalBLEParser.ParsedData data) {
        if (data == null) return false;
        
        // Key for identifying sensor type per device
        String sensorKey = deviceAddress + "_" + data.type;
        UniversalBLEParser.ParsedData lastData = lastDataMap.get(sensorKey);
        long lastUpdate = lastUpdateTimeMap.getOrDefault(sensorKey, 0L);
        long now = System.currentTimeMillis();
        
        // Proceed if value changed OR if it's been more than 2 seconds since last update
        boolean hasChanged = lastData == null || lastData.value != data.value || !lastData.unit.equals(data.unit);
        boolean isTimeout = (now - lastUpdate) > 2000;

        if (!hasChanged && !isTimeout) {
            return false;
        }

        // Store as last known value and time
        lastDataMap.put(sensorKey, data);
        lastUpdateTimeMap.put(sensorKey, now);

        // Save to history (parsed data)
        if (!deviceDataMap.containsKey(deviceAddress)) {
            deviceDataMap.put(deviceAddress, new ArrayList<>());
        }
        List<UniversalBLEParser.ParsedData> dataList = deviceDataMap.get(deviceAddress);
        if (dataList != null) {
            dataList.add(data);
            if (dataList.size() > 100) dataList.remove(0);
        }
        return true;
    }

    public synchronized List<UniversalBLEParser.ParsedData> getLatestData(String deviceAddress) {
        return deviceDataMap.getOrDefault(deviceAddress, Collections.emptyList());
    }

    public synchronized Map<String, List<UniversalBLEParser.ParsedData>> getAllData() {
        return new HashMap<>(deviceDataMap);
    }

    public synchronized void clear(String deviceAddress) {
        deviceDataMap.remove(deviceAddress);
    }
}
