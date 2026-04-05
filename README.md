🛡️ FamilySafe: Universal BLE-Based Real-Time Family Monitoring Ecosystem
Team XCODER | Hacksagon 2026 @ ABV-IIITM Gwalior
FamilySafe is a hybrid Bluetooth Low Energy (BLE) powered family monitoring platform designed for urban environments. It integrates smartwatch and smartphone sensor data using a universal parsing engine capable of handling both standard and proprietary BLE protocols, enabling real-time health tracking and centralized monitoring.
✨ Key Features
⌚ Live Vitals Sync: Real-time Heart Rate, SpO₂, and Steps tracking via BLE-enabled smartwatches using both standard (0x2A37) and reverse-engineered custom protocols (FEE1/FEE3).
🧠 Universal BLE Parser: Intelligent decoding engine that dynamically interprets raw byte streams from multiple devices using UUID-based classification and custom protocol mapping.
🔓 Proprietary Device Support: Reverse-engineered support for devices like HONOR Band using tagged packet decoding (Sensor ID + Value model).
🚨 Emergency Alerts: Logic-ready system for SOS triggers, abnormal vitals detection, and disconnection alerts.
👥 Family Network: Centralized monitoring architecture for multi-device, multi-user family tracking.
🛠️ Tech Stack
Frontend: Android (Java, Android Studio, BLE APIs)
Backend: Node.js, Express.js, MongoDB Atlas
Core Technologies: Bluetooth Low Energy (BLE), GATT Protocol, Byte-Level Data Parsing, Reverse Engineering (Code 34)
📡 Core System Flow
BLE Scan → Device Connection (GATT) → Service Discovery → Notification Subscription → Raw Data Capture → UniversalBLEParser → Decoded Health Metrics → Backend Sync
🔬 Technical Highlights
Implemented GATT-based BLE communication with real-time notification handling
Developed custom decoder for proprietary packets:
FEE3: Tagged packets (Sensor ID + Value)
FEE1/FEA1: Packed multi-sensor frames
Byte-level parsing using bitwise operations (&, <<, |)
Hybrid architecture supporting both standard and non-standard devices
Link of FamilySafe APP :- https://github.com/akkysERA/Familysafe.git
