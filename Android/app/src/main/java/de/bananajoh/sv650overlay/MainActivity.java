package de.bananajoh.sv650overlay;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.IBinder;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.Toast;

import de.bananajoh.sv650overlay.R;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;


public class MainActivity extends AppCompatActivity {
    private static class DataInfoEntry {
        public String label;
        public String unit;
        public boolean show;

        public DataInfoEntry(String label, String unit, boolean show) {
            this.label = label;
            this.unit = unit;
            this.show = show;
        }
    }

    private static final int CODE_REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int CODE_REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int CODE_REQUEST_ENABLE_BLUETOOTH = 3;
    private static final int CODE_REQUEST_PERMISSION_DRAW_OVER_APPS = 10002;
    private static final int CODE_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 4;
    private static final DataInfoEntry[] DATA_INFO = {
            new DataInfoEntry("6", "", false),      new DataInfoEntry("7", "", false),
            new DataInfoEntry("8", "", false),      new DataInfoEntry("9", "", false),
            new DataInfoEntry("10", "", false),     new DataInfoEntry("11", "", false),
            new DataInfoEntry("12", "", false),     new DataInfoEntry("13", "", false),
            new DataInfoEntry("14", "", false),     new DataInfoEntry("15", "", false),
            new DataInfoEntry("16", "", false),     new DataInfoEntry("17", "", false),
            new DataInfoEntry("18", "", false),     new DataInfoEntry("19", "", false),
            new DataInfoEntry("20", "", false),     new DataInfoEntry("21", "", false),
            new DataInfoEntry("22", "", false),     new DataInfoEntry("23", "", false),
            new DataInfoEntry("24", "", false),     new DataInfoEntry("RPM", "", true),
            new DataInfoEntry("26", "", false),     new DataInfoEntry("TPS", " %", true),
            new DataInfoEntry("IAP1", "", true),    new DataInfoEntry("ECT", " °C", true),
            new DataInfoEntry("IAT", " °C", true),  new DataInfoEntry("31", "", false),
            new DataInfoEntry("BATT", " V", true),  new DataInfoEntry("HO2", "", true),
            new DataInfoEntry("GPS", "", true),     new DataInfoEntry("IAP2", "", true),
            new DataInfoEntry("IDLE", "", true),    new DataInfoEntry("ISCV", "", true),
            new DataInfoEntry("38", "", false),     new DataInfoEntry("FUEL1a", "", true),
            new DataInfoEntry("FUEL1b", "", true),  new DataInfoEntry("FUEL2a", "", true),
            new DataInfoEntry("FUEL2b", "", true),  new DataInfoEntry("43", "", false),
            new DataInfoEntry("44", "", false),     new DataInfoEntry("45", "", false),
            new DataInfoEntry("46", "", false),     new DataInfoEntry("47", "", false),
            new DataInfoEntry("48", "", false),     new DataInfoEntry("IGN1", "", true),
            new DataInfoEntry("IGN2", "", true),    new DataInfoEntry("51", "", false),
            new DataInfoEntry("52", "", false),     new DataInfoEntry("53", "", false),
            new DataInfoEntry("STV", "", true),     new DataInfoEntry("55", "", false),
            new DataInfoEntry("56", "", false),     new DataInfoEntry("57", "", false),
            new DataInfoEntry("58", "", false),     new DataInfoEntry("PAIR", "", true),
            new DataInfoEntry("C/M/TO", "", true),  new DataInfoEntry("N", "", true),
            new DataInfoEntry("62", "", false),     new DataInfoEntry("63", "", false),
            new DataInfoEntry("SUM", "", false)
    };
    private BluetoothAdapter bluetoothAdapter = null;
    private Intent overlayService = null;
    private OverlayService overlayServiceBinding = null;
    private ArrayAdapter<Spanned> gridArrayAdapter;
    private SharedPreferences sharedPreferences = null;
    private Menu menu_main = null;
    private BufferedWriter logFileBuffer = null;


    // Write sensor data log file //
    public void appendLog(String text) {
        if(logFileBuffer == null) {
            return;
        }
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd,HHmmssSSS,").format(new Date());
            logFileBuffer.append(timestamp + text);
            logFileBuffer.newLine();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }


    // Listen for device message broadcasts from overlay service //
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // No extra intent action check as there is only one filter registered
            String data = intent.getStringExtra("data");
            String values[] = data.split(",", DATA_INFO.length);
            gridArrayAdapter.clear();
            for(int i = 0; i < values.length; i++) {
                if(DATA_INFO[i].show) {
                    int value = 0;
                    try {
                        value = Integer.parseInt(values[i]);
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    boolean addValueToGrid = true;
                    switch (i + 6) {        // Correct offset to real data frame index
                        case 25:            // RPM
                            value = (value - 1) * 69 / 10 * 10;
                            if(value < 0) {
                                value = 0;
                            }
                            break;
                        case 27:            // TPS
                            value = (value - 56) * 6 / 10;
                            break;
                        case 29:            // ECT
                        case 30:            // IAT
                            value = value - 40;
                            break;
                        case 32:            // BATT
                            float fvalue = value * 8 / 100.0f;
                            gridArrayAdapter.add(Html.fromHtml("<b>" + DATA_INFO[i].label + "</b><br>" + fvalue + DATA_INFO[i].unit));
                            addValueToGrid = false;
                            break;
                    }
                    if (addValueToGrid) {
                        gridArrayAdapter.add(Html.fromHtml("<b>" + DATA_INFO[i].label + "</b><br>" + value + DATA_INFO[i].unit));
                    }
                }
            }
            appendLog(data);
        }
    };


    // Binding to the local service for intercom
    private ServiceConnection overlayServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            overlayServiceBinding = ((OverlayService.LocalBinder)service).getService();
            bluetoothReconnectOrDeviceList();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            overlayServiceBinding = null;
        }
    };


    // Check permission to draw over other apps //
    private boolean overlayPermissionGranted() {
        if(!Settings.canDrawOverlays(this)) {
            Intent permissionIntent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(permissionIntent, CODE_REQUEST_PERMISSION_DRAW_OVER_APPS);
            Toast.makeText(MainActivity.this, R.string.overlay_request_permission, Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        return true;
    }


    // Check if Bluetooth adapter is available and activated //
    private boolean bluetoothReady() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_LONG).show();
            finish();
            return false;
        } else if(!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, CODE_REQUEST_ENABLE_BLUETOOTH);
            return false;
        } else if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, R.string.bluetooth_fine_location_rationale, Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, CODE_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
            return false;
        }
        return true;
    }


    // Check fulfillment of necessary requirements and if so, start overlay service //
    private void startOverlayServiceIfRequirementsFulfilled() {
        if(!overlayPermissionGranted()) {
            return;
        }
        if(!bluetoothReady()) {
            return;
        }
        startService(overlayService);
        if(!bindService(overlayService, overlayServiceConnection, Context.BIND_AUTO_CREATE)) {
            Toast.makeText(this, R.string.overlay_service_binding_failed, Toast.LENGTH_LONG).show();
            return;
        }
    }


    // List paired and discovered Bluetooth devices //
    public void showBluetoothDeviceList() {
        Intent deviceListIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(deviceListIntent, CODE_REQUEST_CONNECT_DEVICE_SECURE);Toast.makeText(this, R.string.bluetooth_select_device, Toast.LENGTH_LONG).show();
    }


    // Try to connect to last connected device if it is still paired //
    private void bluetoothReconnectOrDeviceList() {
        if(overlayServiceBinding.isBluetoothConnected()) {
            return;
        }
        String restoredDeviceAddress = sharedPreferences.getString("deviceAddress", null);
        boolean restoredDeviceSecure = sharedPreferences.getBoolean("deviceSecure", true);

        if(restoredDeviceAddress != null) {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            for(BluetoothDevice device : pairedDevices) {
                if(device.getAddress().equals(restoredDeviceAddress)) {
                    overlayServiceBinding.connectBluetooth(restoredDeviceAddress, restoredDeviceSecure);
                    return;
                }
            }
            // Device not paired anymore, remove
            sharedPreferences.edit().remove("deviceAddress").remove("deviceSecure").apply();
        }
        // No saved device available to connect to, show device list
        showBluetoothDeviceList();
    }


    // ActivityResult callback //
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case CODE_REQUEST_ENABLE_BLUETOOTH:          // When the request to enable Bluetooth returns
                if(resultCode == Activity.RESULT_OK) {   // Bluetooth is now enabled
                    startOverlayServiceIfRequirementsFulfilled();
                } else {                                 // User did not enable Bluetooth or an error occurred
                    Toast.makeText(this, R.string.bluetooth_not_activated, Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            case CODE_REQUEST_CONNECT_DEVICE_SECURE:   // When DeviceListActivity returns with a device to connect
                if(resultCode == Activity.RESULT_OK) {
                    Bundle extras = data.getExtras();
                    if(extras != null) {
                        String deviceAddress = extras.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                        overlayServiceBinding.connectBluetooth(deviceAddress, true);
                        sharedPreferences.edit().putString("deviceAddress", deviceAddress).putBoolean("deviceSecure", true).apply();
                    }
                }
                break;
            case CODE_REQUEST_CONNECT_DEVICE_INSECURE: // When DeviceListActivity returns with a device to connect
                if(resultCode == Activity.RESULT_OK) {
                    Bundle extras = data.getExtras();
                    if(extras != null) {
                        String deviceAddress = extras.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                        overlayServiceBinding.connectBluetooth(deviceAddress, false);
                        sharedPreferences.edit().putString("deviceAddress", deviceAddress).putBoolean("deviceSecure", false).apply();
                    }
                }
                break;
        }
    }


    // Permission request callback //
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode) {
            case CODE_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startOverlayServiceIfRequirementsFulfilled();
                } else {
                    Toast.makeText(this, R.string.bluetooth_fine_location_not_granted, Toast.LENGTH_LONG).show();
                    finish();
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }


    // Create callback //
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        gridArrayAdapter = new ArrayAdapter<Spanned>(this, R.layout.grid_view_entry, R.id.grid_view_entry_text);
        GridView logListView = findViewById(R.id.main_grid_view);
        logListView.setAdapter(gridArrayAdapter);

        sharedPreferences = this.getSharedPreferences("de.bananajoh.sv650overlay.preferences", Context.MODE_PRIVATE);

        overlayService = new Intent(this, OverlayService.class);
        startOverlayServiceIfRequirementsFulfilled();
    }


    // Destroy callback //
    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    // Resume callback //
    @Override
    public void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(String.valueOf(R.string.bluetooth_message_intent_action)));
    }


    // Pause callback //
    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);

        super.onPause();
    }


    // Options menu creation callback //
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu_main = menu;
        return true;
    }


    // Options menu item selected callback //
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.action_bluetooth_device) {
            overlayServiceBinding.disconnectBluetooth(false);
            showBluetoothDeviceList();
            return true;
        } else if(id == R.id.action_data_logging) {
            if(logFileBuffer == null) {
                String filetimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File logFile = new File(this.getExternalFilesDir(null).getAbsolutePath(), "sensordata_" + filetimestamp + ".log");
                if(!logFile.exists()) {
                    try {
                        logFile.createNewFile();
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    logFileBuffer = new BufferedWriter(new FileWriter(logFile, true));
                    String logheader = "Date,Time";
                    for(DataInfoEntry dataInfoEntry : DATA_INFO) {
                        logheader += "," + dataInfoEntry.label;
                    }
                    logFileBuffer.append(logheader);
                    logFileBuffer.newLine();
                } catch(IOException e) {
                    e.printStackTrace();
                }
                menu_main.findItem(R.id.action_data_logging).setTitle(R.string.action_data_logging_stop);
                menu_main.findItem(R.id.action_data_logging).setIcon(android.R.drawable.checkbox_off_background);
            } else {
                try {
                    logFileBuffer.newLine();
                    logFileBuffer.flush();
                    logFileBuffer.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
                logFileBuffer = null;
                menu_main.findItem(R.id.action_data_logging).setTitle(R.string.action_data_logging_start);
                menu_main.findItem(R.id.action_data_logging).setIcon(android.R.drawable.ic_notification_overlay);
            }
            return true;
        } else if(id == R.id.action_close) {
            unbindService(overlayServiceConnection);
            stopService(overlayService);
            overlayService = null;
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}