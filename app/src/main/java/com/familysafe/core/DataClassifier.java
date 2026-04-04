package com.familysafe.core;

import java.util.UUID;

public class DataClassifier {

    public static UniversalBLEParser.ParsedData classifyAndParse(DeviceClassifier.DeviceType type, UUID uuid, byte[] data) {
        if (data == null || data.length == 0) return null;

        // Routing is now simplified as UniversalBLEParser handles common proprietary formats
        return UniversalBLEParser.parse(uuid, data);
    }
}
