package com.familysafe.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PatternAnalyzer {
    
    // Tracks the frequency of changes for specific UUIDs
    private final Map<UUID, Integer> changeFrequency = new HashMap<>();
    private final Map<UUID, byte[]> lastKnownValues = new HashMap<>();

    public void analyze(UUID uuid, byte[] data) {
        if (data == null) return;

        // Count how many times this characteristic has sent data
        int count = changeFrequency.getOrDefault(uuid, 0) + 1;
        changeFrequency.put(uuid, count);

        // Detect if the value is changing
        byte[] lastValue = lastKnownValues.get(uuid);
        if (lastValue != null && !isEqual(lastValue, data)) {
            // This is a dynamic characteristic, likely a sensor
            handleDynamicCharacteristic(uuid, data);
        }
        
        lastKnownValues.put(uuid, data.clone());
    }

    private void handleDynamicCharacteristic(UUID uuid, byte[] data) {
        // Pattern Recognition Logic:
        // 1. Check for specific byte lengths (e.g., Heart Rate is often 2 bytes)
        // 2. Check for incrementing values (likely step counts)
        // 3. Check for specific headers (0x5A, 0xAA, etc.)
        
        if (data.length == 1) {
            // Likely a battery percentage or status flag
        } else if (data.length == 2) {
            // Potential Heart Rate (8-bit) or Environment sensor
        } else if (data.length >= 4) {
            // Potential Step Counter or multi-sensor packet
        }
    }

    private boolean isEqual(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    public Map<UUID, Integer> getActivityReport() {
        return new HashMap<>(changeFrequency);
    }
}
