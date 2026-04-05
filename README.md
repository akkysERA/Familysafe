FamilySafe: Real-Time Family Monitoring via BLE

FamilySafe is a Bluetooth Low Energy (BLE) family monitoring platform for urban environments. It uses a universal parsing engine to integrate smartwatch and smartphone sensor data, enabling real-time health tracking and centralized monitoring.

Key Features:

*   Live Vitals Sync: Tracks real-time heart rate, SpO₂, and steps from BLE smartwatches using standard (0x2A37) and custom (FEE1/FEE3) protocols.
*   Universal BLE Parser: Dynamically decodes raw byte streams from multiple devices using UUID-based classification and custom protocol mapping.
*   Proprietary Device Support: Supports devices like HONOR Band using tagged packet decoding (Sensor ID + Value).
*   Emergency Alerts: Provides SOS triggers, abnormal vitals detection, and disconnection alerts.
*   Family Network: Centralized architecture for multi-device, multi-user family tracking.

Tech Stack:

*   Frontend: Android (Java, Android Studio, BLE APIs)
*   Backend: Node.js, Express.js, MongoDB Atlas
*   Core Technologies: Bluetooth Low Energy (BLE), GATT Protocol, Byte-Level Data Parsing, Reverse Engineering

System Flow:

BLE Scan → Device Connection (GATT) → Service Discovery → Notification Subscription → Raw Data Capture → UniversalBLEParser → Decoded Health Metrics → Backend Sync

Technical Highlights:

*   GATT-based BLE communication with real-time notification handling.
*   Custom decoder for proprietary packets (FEE3: Tagged packets, FEE1/FEA1: Packed multi-sensor frames).
*   Byte-level parsing using bitwise operations.
*   Hybrid architecture supporting standard and non-standard devices.

App Link: https://github.com/akkysERA/Familysafe.git
