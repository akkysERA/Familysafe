package com.familysafe.core;

import android.util.Log;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MongoUploader {
    private static final String TAG = "MongoUploader";
    
    // Replace with your actual MongoDB Atlas Connection String
    // USE STANDARD CONNECTION STRING (mongodb://) instead of SRV (mongodb+srv://) for Android
    private static final String CONNECTION_STRING = "mongodb://<username>:<password>@cluster-shard-00-00.mongodb.net:27017,cluster-shard-00-01.mongodb.net:27017,cluster-shard-00-02.mongodb.net:27017/FamilySafeDB?ssl=true&replicaSet=atlas-xxxxxx-shard-0&authSource=admin&retryWrites=true&w=majority";
    private static final String DATABASE_NAME = "FamilySafeDB";
    private static final String COLLECTION_NAME = "DeviceData";

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public MongoUploader() {
        // Lazy initialization in background thread to avoid Main Thread crash
    }

    private synchronized boolean ensureInitialized() {
        if (mongoClient != null) return true;
        try {
            Log.d(TAG, "Initializing MongoDB client...");
            this.mongoClient = MongoClients.create(CONNECTION_STRING);
            this.database = mongoClient.getDatabase(DATABASE_NAME);
            this.collection = database.getCollection(COLLECTION_NAME);
            Log.d(TAG, "MongoDB client initialized successfully.");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize MongoDB. Note: SRV connection strings (mongodb+srv://) require JNDI which is missing on Android. Consider using a standard connection string (mongodb://).", t);
            return false;
        }
    }

    public void uploadData(String deviceAddress, UniversalBLEParser.ParsedData data) {
        executorService.execute(() -> {
            if (!ensureInitialized() || collection == null) return;
            try {
                Document doc = new Document("device_address", deviceAddress)
                        .append("type", data.type)
                        .append("value", data.value)
                        .append("unit", data.unit)
                        .append("timestamp", new Date());

                collection.insertOne(doc);
                Log.d(TAG, "Uploaded to MongoDB: " + data.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error uploading to MongoDB", e);
            }
        });
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
