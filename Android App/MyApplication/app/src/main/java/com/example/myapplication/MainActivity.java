package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.myapplication.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import kotlin.text.Charsets;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final static int ENABLE_BLUETOOTH_REQUEST_CODE = 1;
    private final static int LOCATION_PERMISSION_REQUEST_CODE = 2;
    private final static int BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE = 3;
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    private Boolean inUse = false;
    private String currentEquipmentName;
    BluetoothLeScanner btScanner;
    Handler nHandler = new Handler();

    private NfcAdapter nfcAdapter;
    private ArrayList<ScanResult> scanResults;
    IntentFilter tagDetected;
    Button refreshButton;
    Tag myTag;
    TextView scanText;
    static NdefMessage ndefMessage;
    private Equipment firstEquipment;
    private Boolean isScanning = false;

    private BluetoothAdapter bluetoothAdapter;

    private BluetoothLeScanner bleScanner;
    private BluetoothManager bluetoothManager;
    private ScanSettings scanSettings;
    private boolean scanning;
    private ScanFilter filter;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public Map<String, Equipment> connectedDevices = new HashMap<String, Equipment>();

    // Stops scanning after 5 seconds.
    private Handler mHandler = new Handler();
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private String equipmentUuid = "BDFC9792-8234-405E-AE02-35EF3274B299";
    private final String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private final String posistionInQueueUUID = "00000001-0000-1000-8000-00805f9b34fb";
    private final String notifyUUID = "00000003-0000-1000-8000-00805f9b34fb";
    private final String availabilityUUID = "00000002-0000-1000-8000-00805f9b34fb";
    int deviceIndex = 0;
    ArrayList<BluetoothDevice> devicesDiscovered = new ArrayList<BluetoothDevice>();
    TextView deviceList;
    Boolean btScanning = false;
    TextView positionTV;
    BluetoothGatt bluetoothGatt;
    TableLayout connectedTable;

    private String message = "my ble connection id";
    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // nfc set up
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "NO NFC Capabilities",
                    Toast.LENGTH_SHORT).show();
            finish();
        }


        tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);


        //view setup
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
        scanText = findViewById(R.id.text_home);
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        refreshButton = findViewById(R.id.refresh_button_home);
        refreshButton.setOnClickListener(refreshDashboardOnclick);
        filter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(equipmentUuid)).build();
        scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        positionTV = findViewById(R.id.equipmentQueuePosition);
        connectedTable = findViewById(R.id.ConnectedTable);
    }


    @SuppressLint("NewApi")
    private ScanCallback scanCallBack = new ScanCallback() {
        @SuppressLint("NewApi")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String name = result.getDevice().getName() == null? result.getDevice().getName() : "Unnamed";
            String address = result.getDevice().getAddress();
            Log.i("“ScanCallback", "Found BLE device! Name: " + name +", address: " +address);
            stopScan();
            result.getDevice().getName();
            result.getDevice().connectGatt(getApplicationContext(), false, gattCallback);
        }
    };

    @SuppressLint("NewApi")
    private void subscribeToNotifications(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt){
        UUID cccUuid = UUID.fromString(CCC_DESCRIPTOR_UUID);
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor cccDescriptor = characteristic.getDescriptor(cccUuid);
        cccDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(cccDescriptor);
    }

    @SuppressLint("NewApi")
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("NewApi")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                String deviceName = gatt.getDevice().getName();
                //connectedDevices.remove(deviceName);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        scanText.setText("disconected from deivce: " + deviceName);
                        connectedTable.removeView(connectedDevices.get(deviceName).getTableRow());
                        connectedDevices.remove(deviceName);
                    }
                });
                if(deviceName.equals(currentEquipmentName)){
                    inUse = false;
                }
            }

            String deviceAddress = gatt.getDevice().getAddress();
            if (status == BluetoothGatt.GATT_SUCCESS) {

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedGatt = gatt;
                    String deviceName = gatt.getDevice().getName();
                    Equipment eq = new Equipment(gatt,MainActivity.this);
                    eq.setName(deviceName);
                    TableRow tr = eq.buildEquipmentView();
                    eq.setName(deviceName);
                    connectedDevices.put(deviceName, eq);
                    mHandler.post(() -> scanText.setText("Connected to device: "+ deviceName));
                    Log.v("test","Connected to " + deviceAddress );
                    // recommended on UI thread https://punchthrough.com/android-ble-guide/
                    gatt.discoverServices();
                    mHandler.post(() -> connectedTable.addView(tr));



                }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.v("test","Disconnected from " + deviceAddress);

                    gatt.close();
                } else {
                gatt.close();
            }
        }

        @SuppressLint("NewApi")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status){
            if (status == 129 /*GATT_INTERNAL_ERROR*/) {
                // it should be a rare case, this article recommends to disconnect:
                // https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
                Log.e("error","ERROR: status=129 (GATT_INTERNAL_ERROR), disconnecting");
                gatt.disconnect();
                return;
            }
            Equipment eq = connectedDevices.get(gatt.getDevice().getName());
            service = gatt.getService(UUID.fromString(equipmentUuid));
            position = service.getCharacteristic(UUID.fromString(posistionInQueueUUID));



            availability = service.getCharacteristic(UUID.fromString(availabilityUUID));
            notifyChar = service.getCharacteristic(UUID.fromString(notifyUUID));
            eq.setPositionInQueue(position);
            eq.setTimeLeft(availability);
            eq.setNotification(notifyChar);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    subscribeToNotifications(notifyChar, gatt);
                }
            });
            subscribeToNotifications(position, gatt);

        }


        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
            //gatt.readCharacteristic(position);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);
            Equipment eq = connectedDevices.get(gatt.getDevice().getName());
            if (characteristic.getUuid().equals(UUID.fromString(notifyUUID) )){
                int seconds = (int) value[0];

                gatt.readCharacteristic(eq.getPositionInQueue());
                eq.setTime(Integer.toString(seconds));
                //nHandler.post(() -> eq.updateEquipmentView());

            }
            if (characteristic.getUuid().equals(UUID.fromString(availabilityUUID) )){
                gatt.readCharacteristic(eq.getTimeLeft());
                mHandler.post(() ->scanText.setText("Position on device " + gatt.getDevice().getName() + " changed"));
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);
            if (characteristic.getUuid().equals(UUID.fromString(posistionInQueueUUID) )){
                int strValue = (int) characteristic.getValue()[0];
                if(strValue == 0){
                    inUse = true;
                    currentEquipmentName = gatt.getDevice().getName();
                    mHandler.post(() -> disconnectFromOtherDevices());
                }
                Equipment eq = connectedDevices.get(gatt.getDevice().getName());
                eq.setPosition(Integer.toString(strValue));
                mHandler.post(()-> eq.updateEquipmentView());
            }
            if (characteristic.getUuid().equals(UUID.fromString(availabilityUUID) )){
                int strValue = (int) characteristic.getValue()[0];
                Equipment eq = connectedDevices.get(gatt.getDevice().getName());
                eq.setTime(Integer.toString(strValue));
                mHandler.post(()-> eq.updateEquipmentView());
            }
        }
    };
    public BluetoothGatt connectedGatt;
    public BluetoothGattService service;
    public BluetoothGattCharacteristic position;
    public BluetoothGattCharacteristic notifyChar;
    public BluetoothGattCharacteristic availability;

    private void disconnectFromOtherDevices(){
        for(String key: connectedDevices.keySet()) {
            if (!currentEquipmentName.equals(key)) {
                connectedDevices.get(key).getConnectedGatt().disconnect();
            }
        }
    }
    private View.OnClickListener refreshDashboardOnclick = v -> {
        try {
            refreshDashboard();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    };

    @SuppressLint("NewApi")
    private void refreshDashboard() throws InterruptedException {
        for(Map.Entry<String, Equipment> entry : connectedDevices.entrySet()) {
            Equipment eq = entry.getValue();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    eq.getConnectedGatt().readCharacteristic(eq.getPositionInQueue());
                }
            });
                    eq.getConnectedGatt().readCharacteristic(eq.getTimeLeft());

        }
    }

    private void toggleBleScan(){
        if(!inUse){
            if(isScanning){
                stopScan();
            }else{
                startScan();
            }
        }else{
            scanText.setText("Currently using a equipment!");
        }

    }




    @SuppressLint("NewApi")
    private void startScan() {
        filter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(equipmentUuid)).build();
        ArrayList<ScanFilter> list = new ArrayList<>(Collections.singleton(filter));
        bleScanner.startScan(list, scanSettings, scanCallBack);

        isScanning = true;
    }


    private void requestBlePermissions(){
        final String[] wantedPermissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wantedPermissions = new String[]{
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
            };
        }else{
            wantedPermissions = new String[]{};
        }

        if(wantedPermissions.length == 0 || hasRequiredRuntimePermissions()){
            return;
        }else{
            runOnUiThread(new Runnable() {
                @SuppressLint("NewApi")
                @Override
                public void run() {
                    requestPermissions(wantedPermissions, BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE);
                }
            });
        }

    }

    @SuppressLint("NewApi")
    private void stopScan() {
        bleScanner.stopScan(scanCallBack);
        isScanning = false;
    }




    @Override
    protected void onResume() {
        super.onResume();
        assert nfcAdapter != null;
        // Enable NFC foreground dispatch to read tags
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE);
        IntentFilter[] intentFiltersArray = new IntentFilter[] { new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED) };
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        if (!bluetoothAdapter.isEnabled()) {
            promptEnableBluetooth();
        }
        if(!hasRequiredRuntimePermissions()){
            requestBlePermissions();
        }
    }

    private void promptEnableBluetooth(){
        if(!bluetoothAdapter.isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            bluetoothActivityResultLauncher.launch(enableBtIntent);
        }
    }


    ActivityResultLauncher<Intent> bluetoothActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        promptEnableBluetooth();
                    }
                }
    });


    @Override
    protected void onPause() {
        super.onPause();

        // Disable NFC foreground dispatch when the app is paused
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readFromIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
    }

    private void readFromIntent(Intent intent) {
        String action = intent.getAction();
        NdefMessage[] msgs;
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

            msgs = null;
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            }
            myTag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            detectTagData(myTag);
            buildTagViews(msgs);
            toggleBleScan();
        }
    }

        private void buildTagViews(NdefMessage[] msgs) {
            if (msgs == null || msgs.length == 0) return;

            String text = "";
//        String tagId = new String(msgs[0].getRecords()[0].getType());
            byte[] payload = msgs[0].getRecords()[0].getPayload();
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16"; // Get the Text Encoding
            int languageCodeLength = payload[0] & 0063; // Get the Language Code, e.g. "en"
            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");

            try {
                // Get the Text
                text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
            } catch (UnsupportedEncodingException e) {
                Log.e("UnsupportedEncoding", e.toString());
            }

                scanText.append("\ntext: " + text);
            payload = msgs[0].getRecords()[1].getPayload();
            textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16"; // Get the Text Encoding
            languageCodeLength = payload[0] & 0063; // Get the Language Code, e.g. "en"
            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");

            try {
                // Get the Text
                text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
            } catch (UnsupportedEncodingException e) {
                Log.e("UnsupportedEncoding", e.toString());
            }
            equipmentUuid = text.substring(0,36);
            scanText.append("\nuuid: " + text);

        }
    private String detectTagData(Tag tag) {
        StringBuilder sb = new StringBuilder();
        byte[] id = tag.getId();

        Log.v("test", sb.toString());
        scanText.setText("");
        return sb.toString();
    }

    private long toDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    private Boolean hasPermission(String permissionType){
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRequiredRuntimePermissions(){
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             return hasPermission(android.Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT);
        } else {
             return hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }
    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}

