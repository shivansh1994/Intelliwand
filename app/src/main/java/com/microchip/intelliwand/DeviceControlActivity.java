
package com.microchip.intelliwand;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.microchip.intelliwand.adapter.ReportDatabase;


import java.util.UUID;

/**
 * This Activity receives a Bluetooth device address provides the user interface to connect, display data, and display GATT services
 * and characteristics supported by the device. The Activity communicates with {@code BluetoothLeService}, which in turn
 * interacts with the Bluetooth LE API.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@SuppressWarnings("deprecation")
public class DeviceControlActivity extends FragmentActivity implements ActionBar.TabListener,View.OnClickListener{
    ReportDatabase myDb;
    public static String msgdata;
    private ActionBar actionBar;
    public static String spmin,spmax,cp,sw,sen,p1r,p2r,csa,str;
    private final static String TAG = DeviceControlActivity.class.getSimpleName();      //Get name of activity to tag debug and warning messages
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";                      //Name passed by intent that lanched this activity
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";                //MAC address passed by intent that lanched this activity
    private static final String MLDP_PRIVATE_SERVICE = "00035b03-58e6-07dd-021a-08123a000300"; //Private service for Microchip MLDP
    private static final String MLDP_DATA_PRIVATE_CHAR = "00035b03-58e6-07dd-021a-08123a000301"; //Characteristic for MLDP Data, properties - notify, write
    private static final String MLDP_CONTROL_PRIVATE_CHAR = "00035b03-58e6-07dd-021a-08123a0003ff"; //Characteristic for MLDP Control, properties - read, write
    private static final String CHARACTERISTIC_NOTIFICATION_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";	//Special UUID for descriptor needed to enable notifications

                                                                                //BluetoothAdapter controls the Bluetooth radio in the phone
    private BluetoothGatt mBluetoothGatt;                                               //BluetoothGatt controls the Bluetooth communication link
    private BluetoothGattCharacteristic mDataMDLP;
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    //Handler used to send die roll after a time delay
    private EditText message;
    private TextView mConnectionState;                                      //TextViews to show connection state and die roll number on the display
    private TextView mDevice_name;
    private TextView timestamp;
    private TextView tv2;
    File myFile,Output;
    FileOutputStream fOut;
    private Button button;
    private Button report;
    private Button email;
    private String mDeviceAddress;                                         //Strings for the Bluetooth device name and MAC address
    private String incomingMessage;//String to hold the incoming message from the MLDP characteristic
    private boolean mConnected = false;
    private String path=Environment.getExternalStorageDirectory().getAbsolutePath() + "/ITester/";
    private String connection;
    SQLiteDatabase sqldb;
    Cursor c;
    Handler mHandler;
    ProgressBar pbHeaderProgress;

    // ----------------------------------------------------------------------------------------------------------------
    // Activity launched
    // Invoked by Intent in onListItemClick method in DeviceScanActivity
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.die_screen);                                            //Show the screen with the die number and button
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        final Intent intent = getIntent();                                              //Get the Intent that launched this activity
        String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);                  //Get the BLE device address from the Intent
        mHandler = new Handler();                                                       //Create Handler to delay sending first roll after new connection
        ((TextView) findViewById(R.id.deviceAddress)).setText(mDeviceAddress);          //Display device address on the screen
        mConnectionState = (TextView) findViewById(R.id.connectionState);               //TextView that will display the connection state
        mDevice_name=(TextView)findViewById(R.id.devicename);
        message=(EditText) findViewById(R.id.message);
        pbHeaderProgress = (ProgressBar) findViewById(R.id.pbHeaderProgress);
        pbHeaderProgress.setVisibility(View.GONE);

        message.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                // TODO Auto-generated method stub
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                // TODO Auto-generated method stub
            }
            @Override
            public void afterTextChanged(Editable s) {
                int i=s.length();
                if(i==2 || i==5){
                    message.setText(s+"/");
                    message.setSelection(message.getText().length());
                }
                if(i==10)
                {message.setError(null);}
                if(i>10 || i<10){
               //     Toast.makeText(DeviceControlActivity.this,"Enter Correct PCBA format",Toast.LENGTH_SHORT).show();
                    Animation shake = AnimationUtils.loadAnimation(DeviceControlActivity.this, R.anim.shake);
                    message.startAnimation(shake);
                    message.setError("Input right format");
                }
            }
        });

        timestamp=(TextView)findViewById(R.id.timestamp);
        String date = (DateFormat.format("dd-MM-yyyy_hh:mm:ss", new java.util.Date()).toString());
        timestamp.setText(date);
         //My Database class
        myDb =new ReportDatabase(this);
        mHandler = new Handler();
        button=(Button)findViewById(R.id.button1);
        report=(Button)findViewById(R.id.button2);
        email=(Button)findViewById(R.id.button3);
        myDb =new ReportDatabase(this);
        incomingMessage = "";
        button.setVisibility(View.GONE);
        report.setVisibility(View.GONE);
        email.setVisibility(View.GONE);
        button.setOnClickListener((View.OnClickListener) this);
        report.setOnClickListener((View.OnClickListener) this);
        email.setOnClickListener((View.OnClickListener) this);
        message.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(message.getWindowToken(), 0);
                }
                return false;
            }
        });
        //Create new string to hold incoming message data
        this.getActionBar().setTitle(mDeviceName);                                      //Set the title of the ActionBar to the name of the BLE device
        this.getActionBar().setDisplayHomeAsUpEnabled(true);                            //Make home icon clickable with < symbol on the left
        mDevice_name.setText(mDeviceName);
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE); //Get the BluetoothManager
        mBluetoothAdapter = bluetoothManager.getAdapter();                              //Get a reference to the BluetoothAdapter (radio)
        if (mBluetoothAdapter == null) {                                                //Check if we got the BluetoothAdapter
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show(); //Message that Bluetooth is not supported
            finish();                                                                   //End the activity
        }

    }


    void write (String d){
        Log.d("Sending :",d);
        mDataMDLP.setValue(d);
        writeCharacteristic(mDataMDLP);

    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed
    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
            }
           scanLeDevice(true);
        }


        if (mBluetoothAdapter == null || mDeviceAddress == null) {                      //Check that we still have a Bluetooth adappter and device address
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");     //Warn that something went wrong
            finish();                                                                   //End the Activity
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDeviceAddress); //Get the Bluetooth device by referencing its address
        if (device == null) {                                                           //Check whether a device was returned
            Log.w(TAG, "Device not found.  Unable to connect.");                        //Warn that something went wrong
            finish();                                                                   //End the Activity
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);                //Directly connect to the device so autoConnect is false
        Log.d(TAG, "Trying to create a new connection.");

    }
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);

                    }
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }


    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("onLeScan", device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(false);// will stop after first device detection
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("onServicesDiscovered", services.toString());
            gatt.readCharacteristic(services.get(1).getCharacteristics().get
                    (0));
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
            gatt.disconnect();
        }
    };


    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    @Override
    protected void onPause() {
        super.onPause();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity is ending
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mBluetoothGatt != null) {                                            //If there is a valid GATT connection
            mBluetoothGatt.disconnect();                                        // then disconnect
        }
        mBluetoothGatt.close();                                                         //Close the connection
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Options menu is different depending on whether connected or not
    // Show Connect option if not connected or show Disconnect option if we are connected
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);                          //Show the Options menu
        if (mConnected) {                                                               //See if connected
            menu.findItem(R.id.menu_connect).setVisible(false);                         // then dont show disconnect option
            menu.findItem(R.id.menu_disconnect).setVisible(true);                       // and do show connect option
        }
        else {                                                                          //If not connected
            menu.findItem(R.id.menu_connect).setVisible(true);                          // then show connect option
            menu.findItem(R.id.menu_disconnect).setVisible(false);                      // and don't show disconnect option
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Menu item selected
    // Connect or disconnect to BLE device
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {                                                     //Get which menu item was selected
            case R.id.menu_connect:                                                     //Option to Connect chosen
                if(mBluetoothGatt != null) {                                            //If there is a valid GATT connection
                    mBluetoothGatt.connect();                                           // then connect

                    updateDieState();
                }
                return true;
            case R.id.menu_disconnect:                                                  //Option to Disconnect chosen
                if(mBluetoothGatt != null) {                                            //If there is a valid GATT connection
                    mBluetoothGatt.disconnect();                                        // then disconnect

                }
                return true;
            case android.R.id.home:                                                     //Option to go back was chosen
                onBackPressed();                                                        //Execute functionality of back button
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Update text with connection state
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);                                   //Update text to say "Connected" or "Disconnected"

            }
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Update text roll of die and send over Bluetooth
    private void updateDieState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mConnected) {

                }

            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){

            case R.id.button1: {
                boolean success = true;
                if (incomingMessage.length() == 613 && incomingMessage.contains("SELF TEST PASSED") && incomingMessage.contains("SYSTEM STATUS\tIC40\t\tSERIAL\t99999999")) {
                    Toast.makeText(DeviceControlActivity.this, "DEVICE PASSED", Toast.LENGTH_LONG).show();

                    try {
                        myFile = new File(path);
                        if (!myFile.exists()) {
                            success = myFile.mkdir();
                        }
                        if (success) {
                            // On success create file
                            connection = "Connected Successfully";
                            Output = new File(myFile, timestamp.getText().toString() + ".txt");
                            Output.createNewFile();
                            fOut = new FileOutputStream(Output, true);
                            fOut.write("\n\r".getBytes());
                            fOut.write("DEVICE NAME -     ".getBytes());
                            fOut.write(mDevice_name.getText().toString().getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("DEVICE ADDRESS -     ".getBytes());
                            fOut.write(mDeviceAddress.toString().getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("PCBA DETAILS -     ".getBytes());
                            fOut.write(message.getText().toString().getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("TIMESTAMP -     ".getBytes());
                            fOut.write(timestamp.getText().toString().getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("MESSAGE -     ".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write(incomingMessage.getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("RESULT -   DEVICE TEST PASSED!!".getBytes());
                            fOut.close();
                        }
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    //DATABASE INSERTION
                    incomingMessage = "PASSED";
                    boolean isInserted = myDb.insertData(timestamp.getText().toString(), message.getText().toString(), mDeviceAddress.toString(), connection.toString(), incomingMessage.toString(), "SUCCESSFULLY PASSED".toString());
                    if (isInserted == true) {
                        Toast toast = Toast.makeText(DeviceControlActivity.this, "DATA SAVED IN REPORT", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.TOP, 0, 0);
                        toast.show();
                    } else {
                        Toast toast = Toast.makeText(DeviceControlActivity.this, "DATA NOT SAVED IN REPORT", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.TOP, 0, 0);
                        toast.show();
                    }
                    button.setVisibility(View.GONE);
                    Toast toast = Toast.makeText(DeviceControlActivity.this, "PRESS BACK FOR ANOTHER TEST", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();

                } else {
                    Toast.makeText(DeviceControlActivity.this, "DEVICE FAILED", Toast.LENGTH_LONG).show();
                    try {
                        myFile = new File(path);

                        String TAG = DeviceControlActivity.class.getName();
                        if (!myFile.exists()) {
                            success = myFile.mkdir();
                        }
                        if (success) {
                            // On success create file
                            connection = "Connected  Unsuccessfully";

                            Output = new File(myFile, timestamp.getText().toString() + ".txt");
                            Output.createNewFile();
                            fOut = new FileOutputStream(Output, true);
                            fOut.write("\n\r".getBytes());
                            fOut.write("DEVICE NAME -     ".getBytes());
                            fOut.write(mDevice_name.getText().toString().getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("DEVICE ADDRESS -     ".getBytes());
                            fOut.write(mDeviceAddress.toString().getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("PCBA DETAILS -     ".getBytes());
                            fOut.write(message.getText().toString().getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("TIMESTAMP -     ".getBytes());
                            fOut.write(timestamp.getText().toString().getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("MESSAGE -     ".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write(incomingMessage.getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("\n\r".getBytes());
                            fOut.write("RESULT -   DEVICE TEST FAILED!!".getBytes());
                            fOut.close();
                        }
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    //DATABASE INSERTION
                    boolean isInserted = myDb.insertData(timestamp.getText().toString(), message.getText().toString(), mDeviceAddress.toString(), connection.toString(), incomingMessage.toString(), "FAILED!".toString());
                    if (isInserted == true) {
                        Toast toast = Toast.makeText(DeviceControlActivity.this, "DATA SAVED IN REPORT", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.TOP, 0, 0);
                        toast.show();
                    } else {
                        Toast toast = Toast.makeText(DeviceControlActivity.this, "DATA NOT SAVED IN REPORT", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.TOP, 0, 0);
                        toast.show();
                    }
                    button.setVisibility(View.GONE);
                    Toast toast = Toast.makeText(DeviceControlActivity.this, "PRESS BACK FOR ANOTHER TEST", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
                incomingMessage = "";
                sqldb = myDb.getReadableDatabase(); //My Database class
                c = null;
                try {
                    c = sqldb.rawQuery("select * from devices_info", null);
                    int rowcount = 0;
                    int colcount = 0;
                    File sdCardDir = Environment.getExternalStorageDirectory();
                    String filename = "MyReportIntelliwand.csv";
                    // the name of the file to export with
                    File saveFile = new File(sdCardDir, filename);
                    FileWriter fw = new FileWriter(saveFile);
                    BufferedWriter bw = new BufferedWriter(fw);
                    rowcount = c.getCount();
                    colcount = c.getColumnCount();
                    if (rowcount > 0) {
                        c.moveToFirst();

                        for (int i = 0; i < colcount; i++) {
                            if (i != colcount - 1) {

                                bw.write(c.getColumnName(i) + " , ");

                            } else {

                                bw.write(c.getColumnName(i));

                            }
                        }
                        bw.newLine();

                        for (int i = 0; i < rowcount; i++) {
                            c.moveToPosition(i);

                            for (int j = 0; j < colcount; j++) {
                                if (j != colcount - 1)
                                    bw.write(c.getString(j) + " , ");
                                else
                                    bw.write(c.getString(j));
                            }
                            bw.newLine();
                        }
                        bw.flush();
                        Toast.makeText(DeviceControlActivity.this, "SUCCESS", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception ex) {
                    if (sqldb.isOpen()) {
                        sqldb.close();
                        Toast.makeText(DeviceControlActivity.this, "NO SUCCESS", Toast.LENGTH_LONG).show();

                    }

                }

                incomingMessage = "";
                email.setVisibility(View.VISIBLE);
                break;
            }
            case R.id.button3: {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"manul.jain@pentair.com"});
                intent.putExtra(Intent.EXTRA_SUBJECT, "INTELLIWAND TEST REPORT");
                intent.putExtra(Intent.EXTRA_TEXT, "SEE ATTACHED FILE");
                File root = Environment.getExternalStorageDirectory();
                File file = new File(root,"MyReportIntelliwand.csv");
                if (!file.exists() || !file.canRead()) {
                    Toast.makeText(this, "Attachment Error", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                Uri uri = Uri.parse("file://" + file.getAbsolutePath());
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(Intent.createChooser(intent, "Send email with GMAIL APP"));
                Toast
                        .makeText(this, "SELECT GMAIL TO SEND REPORT", Toast.LENGTH_LONG)
                        .show();
            }
        }}


    private void findMldpGattService(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {                                                     //Verify that list of GATT services is valid
            Log.d(TAG, "findMldpGattService found no Services");
            return;
        }
        String uuid;                                                                    //String to compare received UUID with desired known UUIDs
        mDataMDLP = null;                                                               //Searching for a characteristic, start with null value

        for (BluetoothGattService gattService : gattServices) {                         //Test each service in the list of services
            uuid = gattService.getUuid().toString();                                    //Get the string version of the service's UUID
            if (uuid.equals(MLDP_PRIVATE_SERVICE)) {                                    //See if it matches the UUID of the MLDP service
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics(); //If so then get the service's list of characteristics
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) { //Test each characteristic in the list of characteristics
                    uuid = gattCharacteristic.getUuid().toString();                     //Get the string version of the characteristic's UUID
                    if (uuid.equals(MLDP_DATA_PRIVATE_CHAR)) {                          //See if it matches the UUID of the MLDP data characteristic
                        mDataMDLP = gattCharacteristic;                                 //If so then save the reference to the characteristic
                        Log.d(TAG, "Found MLDP data characteristics");
                    }
                    else if (uuid.equals(MLDP_CONTROL_PRIVATE_CHAR)) {                  //See if UUID matches the UUID of the MLDP control characteristic
                        BluetoothGattCharacteristic mControlMLDP = gattCharacteristic;
                        Log.d(TAG, "Found MLDP control characteristics");
                    }
                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_NOTIFICATION_CONFIG)); //Get the descripter that enables notification on the server
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                        mBluetoothGatt.writeDescriptor(descriptor);                     //Write the descriptor
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_INDICATE)) > 0) { //See if the characteristic has the Indicate property
                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification (and indication) in the BluetoothGatt
                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_NOTIFICATION_CONFIG)); //Get the descripter that enables indication on the server
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE); //Set the value of the descriptor to enable indication
                        mBluetoothGatt.writeDescriptor(descriptor);                     //Write the descriptor
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE)) > 0) { //See if the characteristic has the Write (acknowledged) property
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); //If so then set the write type (write with acknowledge) in the BluetoothGatt
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                    }
                }
                break;                                                                  //Found the MLDP service and are not looking for any other services
            }
        }
        if (mDataMDLP == null) {                                                        //See if the MLDP data characteristic was not found
            Toast.makeText(this, R.string.mldp_not_supported, Toast.LENGTH_SHORT).show(); //If so then show an error message
            Log.d(TAG, "findMldpGattService found no MLDP service");
            finish();                                                                   //and end the activity
        }
        mHandler.postDelayed(new Runnable() {                                           //Create delayed runnable that will send a roll of the die after a delay
            @Override
            public void run() {
                updateDieState();                                                       //Update the state of the die with a new roll and send over BLE
            }
        }, 500);                                                                        //Do it after 200ms delay to give the RN4020 time to configure the characteristic

    }

    // ----------------------------------------------------------------------------------------------------------------
    // Implements callback methods for GATT events that the app cares about.  For example: connection change and services discovered.
    // When onConnectionStateChange() is called with newState = STATE_CONNECTED then it calls mBluetoothGatt.discoverServices()
    // resulting in another callback to onServicesDiscovered()
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) { //Change in connection state
            if (newState == BluetoothProfile.STATE_CONNECTED) {                         //See if we are connected
                Log.i(TAG, "Connected to GATT server.");
                mConnected = true;                                                      //Record the new connection state
                updateConnectionState(R.string.connected);                              //Update the display to say "Connected"
                invalidateOptionsMenu();                                                //Force the Options menu to be regenerated to show the disconnect option
                mBluetoothGatt.discoverServices();                                      // Attempt to discover services after successful connection.
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {                 //See if we are not connected
                Log.i(TAG, "Disconnected from GATT server.");
                mConnected = false;                                                     //Record the new connection state
                updateConnectionState(R.string.disconnected);                           //Update the display to say "Disconnected"
                invalidateOptionsMenu();                                                //Force the Options menu to be regenerated to show the connect option
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {              //Service discovery complete
            if (status == BluetoothGatt.GATT_SUCCESS && mBluetoothGatt != null) {       //See if the service discovery was successful
                findMldpGattService(mBluetoothGatt.getServices());                      //Get the list of services and call method to look for MLDP service
            }
            else {                                                                      //Service discovery was not successful
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        //For information only. This application uses Indication to receive updated characteristic data, not Read
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { //A request to Read has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the read was successful
                String dataValue = characteristic.getStringValue(0);                        //Get the value of the characteristic
                incomingMessage = incomingMessage.concat(dataValue);

            }
        }

        //For information only. This application sends small packets infrequently and does not need to know what the previous write completed
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { //A request to Write has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the write was successful
                boolean writeComplete = true;
            }

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) { //Indication or notification was received
            String dataValue = characteristic.getStringValue(0);                        //Get the value of the characteristic
            incomingMessage = incomingMessage.concat(dataValue);
           if(incomingMessage.length()>=613){

               Activity act = DeviceControlActivity.this;
               act.runOnUiThread(new Runnable() {
                   @Override
                   public void run() {
                       if((message.getText().toString()).length()==10) {
                           pbHeaderProgress.setVisibility(View.GONE);
                           button.setVisibility(View.VISIBLE);
                       }
                       else {
                           pbHeaderProgress.setVisibility(View.VISIBLE);
                           Toast.makeText(DeviceControlActivity.this,"ENTER CORRECT FORMAT OF PCBA",Toast.LENGTH_LONG).show();

                   }}
               });

           }
        }
    };


    // ----------------------------------------------------------------------------------------------------------------
    // Request a read of a given BluetoothGattCharacteristic. The Read result is reported asynchronously through the
    // BluetoothGattCallback onCharacteristicRead callback method.
    // For information only. This application uses Indication to receive updated characteristic data, not Read

    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have access to a Bluetooth radio
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);                              //Request the BluetoothGatt to Read the characteristic
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Write to a given characteristic. The completion of the write is reported asynchronously through the
    // BluetoothGattCallback onCharacteristicWrire callback method.
    private void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have access to a Bluetooth radio
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        int test = characteristic.getProperties();                                      //Get the properties of the characteristic
        if ((test & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 && (test & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) { //Check that the property is writable 
            return;
        }

        if (mBluetoothGatt.writeCharacteristic(characteristic)) {                       //Request the BluetoothGatt to do the Write
            Log.d(TAG, "writeCharacteristic successful");                               //The request was accepted, this does not mean the write completed
            Log.d("Writing :",characteristic.toString());
        }
        else {
            Log.d(TAG, "writeCharacteristic failed");                                   //Write request was not accepted by the BluetoothGatt
        }
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        // on tab selected
        // show respected fragment view
//        viewPager.setCurrentItem(tab.getPosition());

    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {

    }

    @Override
    public void onBackPressed() {
        if(mBluetoothGatt != null) {                                            //If there is a valid GATT connection
            mBluetoothGatt.disconnect();                                        // then disconnect
        }
        mBluetoothGatt.close();                                                         //Close the connection
        Intent j=new Intent(getApplicationContext(), DeviceScanActivity.class);
        j.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(j);
        super.onBackPressed();
        this.finish();
    }

}
