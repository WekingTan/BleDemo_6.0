package com.example.a123.bledemo;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private Handler mHandler;
    private ListView mListView;
    private Context context;
    private boolean mScanning;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_CODE_LOCATION_SETTINGS = 2;
    private static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
    // 10秒后停止查找搜索.
    private static final long SCAN_PERIOD = 10000;
    //蓝牙适配器列表
    private ArrayList<BluetoothDevice> mLeDevices = new ArrayList<>();
    private final String TAG = "蓝牙";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle("BLE蓝牙设备目录");

        context = this;
        mHandler = new Handler();
        mListView = findViewById(R.id.listView_list_devices);

        //init();

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device == null) return;
                final Intent intent = new Intent(context, DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                if (mScanning) {
                    scanLeDevices(false);
                    mScanning = false;
                }
                startActivity(intent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_scan_stop, menu);
        if (mScanning) {
            menu.findItem(R.id.stop).setVisible(true);
            menu.findItem(R.id.scan).setVisible(false);
        } else {
            menu.findItem(R.id.stop).setVisible(false);
            menu.findItem(R.id.scan).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.scan:
                mLeDeviceListAdapter.clear();
                scanLeDevices(true);
                break;
            case R.id.stop:
                scanLeDevices(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        //todo 跟官方demo不同
        init();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        //todo 跟官方demo不同
        if (mBluetoothLeScanner == null) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {

            //扫描蓝牙设备
            mLeDeviceListAdapter.clear();
            mListView.setAdapter(mLeDeviceListAdapter);
            scanLeDevices(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //@Override
    //protected void onPause() {
    //    super.onPause();
    //    scanLeDevices(false);
    //    mLeDeviceListAdapter.clear();
    //}

    private void init() {

        // 检查当前手机是否支持 BLE 蓝牙,如果不支持则退出程序
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // 初始化 Bluetooth adapter, 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        //初始化 BluetoothLeScanner
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        //初始化 LeDeviceListAdapter
        mLeDeviceListAdapter = new LeDeviceListAdapter(context, mLeDevices);

        // 检查设备上是否支持蓝牙
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//如果 API level 是大于等于 23(Android 6.0) 时
            //判断是否具有权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //判断是否需要向用户解释为什么需要申请该权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(getApplicationContext(), "自Android 6.0开始需要打开位置权限才可以搜索到Ble设备", Toast.LENGTH_SHORT).show();
                }
                //请求权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_ACCESS_COARSE_LOCATION);
            }
        }

        Boolean mLocation = isLocationEnable(this);
        if (!mLocation) {
            setLocationService();
        }
    }

    //搜索函数，反馈是mLeScanCallback
    private void scanLeDevices(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLeScanner.stopScan(mScanCallback);
                    invalidateOptionsMenu();
                    Log.d("蓝牙扫描状态", "\n" + "————————————————————————" + "bluetooth has stopScanned" + "————————————————————————");
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothLeScanner.startScan(mScanCallback);
        } else {
            mScanning = false;
            mBluetoothLeScanner.stopScan(mScanCallback);
            Log.d("蓝牙扫描状态", "\n" + "————————————————————————" + "bluetooth has stopScanned" + "————————————————————————");
        }
        invalidateOptionsMenu();
    }

    // 搜索函数更新到主线程来更新UI界面
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            final StringBuilder builder = new StringBuilder();
            BluetoothDevice device = result.getDevice();
            Log.d(TAG, "*****************************************");
            Log.d(TAG, "Device name: " + device.getName());
            Log.d(TAG, "Device address: " + device.getAddress());
            Log.d(TAG, "Device service UUIDs: " + device.getUuids());

            // TODO: 2018/3/15  device.getName() != null 如果不加入则在扫描iphone蓝牙时会奔溃
            if (device.getName() != null && builder.toString().contains(device.getName())) {
            } else {
                builder.append(device.getName() + "_____________" + device.getAddress());
            }
            ScanRecord record = result.getScanRecord();
            Log.d(TAG, "Record advertise flags: 0x" + Integer.toHexString(record.getAdvertiseFlags()));
            Log.d(TAG, "Record Tx power level: " + record.getTxPowerLevel());
            Log.d(TAG, "Record device name: " + record.getDeviceName());
            Log.d(TAG, "Record service UUIDs: " + record.getServiceUuids());
            Log.d(TAG, "Record service data: " + record.getServiceData());
            Log.d(TAG, "*****************************************");

            mLeDeviceListAdapter.addDevice(device);
            mLeDeviceListAdapter.notifyDataSetChanged();

            //mTextView.setText("搜索结果，builder：" + "\n"+ builder.toString());
            Log.d(TAG, builder.toString());
        }
    };

    //判断定位
    public static boolean isLocationEnable(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (networkProvider || gpsProvider) return true;
        return false;
    }

    public void setLocationService() {
        Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        this.startActivityForResult(locationIntent, REQUEST_CODE_LOCATION_SETTINGS);
    }

}



