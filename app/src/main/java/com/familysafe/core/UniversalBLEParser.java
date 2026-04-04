package com.familysafe.core;

import java.util.UUID;

public class UniversalBLEParser {
    
    // Standard Characteristic UUIDs
    public static final UUID CHAR_HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public static final UUID CHAR_BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID CHAR_TEMPERATURE_MEASUREMENT = UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb");
    public static final UUID CHAR_STEP_COUNT_TOTAL = UUID.fromString("00002a13-0000-1000-8000-00805f9b34fb");

    // Custom Sensor Types for SavedDevice
    public static final String TYPE_HEART_RATE = "Heart Rate";
    public static final String TYPE_BATTERY = "Battery";
    public static final String TYPE_TEMPERATURE = "Temperature";
    public static final String TYPE_STEPS = "Steps";
    public static final String TYPE_SPO2 = "SpO2";
    public static final String TYPE_WATCH_DATA = "Watch Data";

    // Common/Custom UUIDs for these sensors
    public static final UUID CHAR_SPO2_MEASUREMENT = UUID.fromString("00002a5f-0000-1000-8000-00805f9b34fb");

    public static class ParsedData {
        public String type;
        public double value;
        public String unit;

        public ParsedData(String type, double value, String unit) {
            this.type = type;
            this.value = value;
            this.unit = unit;
        }

        @Override
        public String toString() {
            return type + ": " + value + " " + unit;
        }
    }

    public static ParsedData parse(UUID uuid, byte[] data) {
        if (data == null || data.length == 0) return null;

        // --- SECTION 1: Standard SIG Characteristics ---
        if (uuid.equals(CHAR_HEART_RATE_MEASUREMENT)) {
            return parseHeartRate(data);
        } else if (uuid.equals(CHAR_STEP_COUNT_TOTAL)) {
            return parseSteps(data);
        } else if (uuid.equals(CHAR_SPO2_MEASUREMENT)) {
            return parseSpO2(data);
        } else if (uuid.equals(CHAR_BATTERY_LEVEL)) {
            return parseBatteryLevel(data);
        } else if (uuid.equals(CHAR_TEMPERATURE_MEASUREMENT)) {
            return parseTemperature(data);
        }

        // --- SECTION 2: Specific Device Parsing Logic (Honor, Sens2, etc.) ---
// Format: FE EA | TYPE | LEN | SENSOR_ID | VALUE
        if (data.length >= 6 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xEA) {

            int sensorType = data[4] & 0xFF;
            int value = data[5] & 0xFF;

            // 🔴 Heart Rate
            if (sensorType == 0x6D) {
                return new ParsedData(TYPE_HEART_RATE, value, "BPM");
            }

            // 🔵 SpO2
            else if (sensorType == 0x6B) {
                return new ParsedData(TYPE_SPO2, value, "%");
            }

            // 🟡 Unknown but valid packet
            else {
                return new ParsedData(TYPE_WATCH_DATA, value, "Unknown Sensor (0x" + Integer.toHexString(sensorType) + ")");
            }
        }

        // --- SECTION 3: Unknown / Generic Characteristics ---
        // If we reach here, the characteristic is not immediately recognized or decoded
        return null;
    }

    private static ParsedData parseHeartRate(byte[] data) {
        if (data.length < 2) return null;
        // As per protocol: [flag, value], BPM is data[1]
        int heartRate = data[1] & 0xFF;
        return new ParsedData(TYPE_HEART_RATE, heartRate, "BPM");
    }

    private static ParsedData parseSteps(byte[] data) {
        // Multi-byte little-endian integer (b0 + (b1<<8) + (b2<<16) + (b3<<24))
        long steps = 0;
        int length = Math.min(data.length, 4);
        for (int i = 0; i < length; i++) {
            steps |= ((long) (data[i] & 0xFF) << (i * 8));
        }
        return new ParsedData(TYPE_STEPS, steps, "steps");
    }

    private static ParsedData parseSpO2(byte[] data) {
        if (data.length < 1) return null;
        // Single byte representing percentage
        int spo2 = data[0] & 0xFF;
        return new ParsedData(TYPE_SPO2, spo2, "%");
    }

    private static ParsedData parseBatteryLevel(byte[] data) {
        int level = data[0] & 0xFF;
        return new ParsedData("Battery", level, "%");
    }

    private static ParsedData parseTemperature(byte[] data) {
        // Temperature Measurement is more complex (Float format), simplified here
        if (data.length >= 5) {
            // Byte 0: Flags, Bytes 1-4: IEEE-11073 32-bit float
            int mantissa = ((data[1] & 0xFF) | ((data[2] & 0xFF) << 8) | ((data[3] & 0xFF) << 16));
            byte exponent = data[4];
            double value = mantissa * Math.pow(10, exponent);
            return new ParsedData("Temperature", value, "°C");
        }
        return null;
    }
}
