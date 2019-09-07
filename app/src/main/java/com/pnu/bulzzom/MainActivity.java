/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.pnu.bulzzom;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SlidingDrawer;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    public static final String TAG = "nRFUART";
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;

    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private ArrayAdapter<String> listAdapter;
    private Button btnConnectDisconnect;
    private LinearLayout layout;
    private Switch switchButton;
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_CONNECT_MSG");
                        btnConnectDisconnect.setText("연결끊기");
                        listAdapter.add("[" + currentDateTimeString + "] Connected to: " + mDevice.getName());
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        btnConnectDisconnect.setText("연결하기");
                        listAdapter.add("[" + currentDateTimeString + "] Disconnected to: " + mDevice.getName());
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        //setUiState();

                    }
                });
            }
            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();

                switchButton.setEnabled(true);
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            String text = new String(txValue, StandardCharsets.UTF_8);
                            Log.d(TAG, text);
                            if (text.contains("OK")) {
                                switchButton.setEnabled(true);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)) {
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }
        }
    };
    private TextView statusText;
    private SlidingDrawer slidingdrawer;
    private Button alarmbtn;
    private Button timerbtn;
    private Button slidingbtn;
    private boolean fromTimer;
    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
            switchButton.setEnabled(false);
        }
    };

    private Handler mHandler = new Handler() {
        @Override

        //Handler events that received from UART service
        public void handleMessage(Message msg) {

        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        btnConnectDisconnect = findViewById(R.id.btn_select);
        switchButton = findViewById(R.id.statusSwitch);
        statusText = findViewById(R.id.statusText);
        layout = findViewById(R.id.main_layout);
        slidingdrawer = findViewById(R.id.slidingDrawer);
        alarmbtn = findViewById(R.id.alarmbtn);
        timerbtn = findViewById(R.id.timerbtn);
        slidingbtn = findViewById(R.id.handle);
        fromTimer = false;

        service_init();

        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    if (btnConnectDisconnect.getText().equals("연결하기")) {
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices

                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice != null) {
                            mService.disconnect();
                        }
                    }
                }
            }
        });

        // Handle Send button
        switchButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    statusText.setText(R.string.switch_off);
                    statusText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.whitegrey));
                    layout.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.night));
                } else {
                    statusText.setText(R.string.switch_on);
                    statusText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.black));
                    layout.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.day));
                }

                if (!fromTimer) {
                    if (isChecked) {
                        // Off
                        if (switchButton.isEnabled()) {
                            try {
                                while (!mService.writeRXCharacteristic("SA20".getBytes(StandardCharsets.UTF_8)))
                                    Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        // On
                        if (switchButton.isEnabled()) {
                            try {
                                while (!mService.writeRXCharacteristic("SA160".getBytes(StandardCharsets.UTF_8)))
                                    Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    fromTimer = false;
                }

                switchButton.setEnabled(false);
            }
        });

        SharedPreferences cache = getSharedPreferences("cache", Activity.MODE_PRIVATE);
        String savedStatus = cache.getString("status", "false");

        switchButton.setEnabled(false);
        if (savedStatus.equals("true")) {
            switchButton.setChecked(true);
        } else {
            switchButton.setChecked(false);
        }

        alarmbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar calender = Calendar.getInstance();

                TimePickerDialog tpd = new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        long now = System.currentTimeMillis();
                        Date date = new Date(now);
                        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");
                        String getTime = sdf.format(date);
                        String[] splitTime = getTime.split(":");
                        Integer nowHour = Integer.parseInt(splitTime[0]);
                        Integer nowMinute = Integer.parseInt(splitTime[1]);
                        Integer nowSecond = Integer.parseInt(splitTime[2]);

                        Integer totalTime = 0;

                        if (nowHour > hourOfDay ||
                                (nowHour == hourOfDay && nowMinute > minute)) {
                            totalTime = 24 * 3600 - (nowHour * 3600 + minute * 60 + nowSecond - (hourOfDay * 3600 + minute * 60));
                        } else {
                            totalTime = (hourOfDay * 3600 + minute * 60) - (nowHour * 3600 + minute * 60 + nowSecond);
                        }

                        try {
                            byte[] value = null;

                            if (switchButton.isChecked()) {
                                value = ("TAN" + totalTime.toString()).getBytes(StandardCharsets.UTF_8);
                                switchButton.setChecked(false);
                            } else {
                                value = ("TAF" + totalTime.toString()).getBytes(StandardCharsets.UTF_8);
                                switchButton.setChecked(true);
                            }

                            while (!mService.writeRXCharacteristic(value))
                                Thread.sleep(1000);
                            switchButton.setEnabled(false);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "시간을 숫자로만 입력해주세요", Toast.LENGTH_LONG).show();
                        }
                    }
                }, 22, 0, true);

                tpd.show();

                Button posBtn = tpd.getButton(tpd.BUTTON_POSITIVE);
                posBtn.setWidth(400);
                Button negBtn = tpd.getButton(tpd.BUTTON_NEGATIVE);
                negBtn.setWidth(400);
            }
        });

        timerbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();

                LayoutInflater inflater = getLayoutInflater();

                View view = inflater.inflate(R.layout.alertdialog_title, null);
                alertDialog.setCustomTitle(view);

                view = inflater.inflate(R.layout.timer_layout, null);
                alertDialog.setView(view);

                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "설정", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        EditText et_hour = alertDialog.findViewById(R.id.timer_hour);
                        EditText et_minute = alertDialog.findViewById(R.id.timer_minute);
                        EditText et_second = alertDialog.findViewById(R.id.timer_second);

                        String hour, minute, second;
                        if (et_hour.getText() != null) {
                            hour = et_hour.getText().toString();
                        } else {
                            hour = "0";
                        }

                        if (et_minute.getText() != null) {
                            minute = et_minute.getText().toString();
                        } else {
                            minute = "0";
                        }

                        if (et_second.getText() != null) {
                            second = et_second.getText().toString();
                        } else {
                            second = "0";
                        }

                        try {
                            byte[] value = null;
                            Integer parsedHour = Integer.parseInt(hour);
                            Integer parsedMinute = Integer.parseInt(minute);
                            Integer parsedSecond = Integer.parseInt(second);

                            Integer totalTime = parsedHour * 3600 + parsedMinute * 60 + parsedSecond;

                            if (switchButton.isChecked()) {
                                value = ("TAN" + totalTime.toString()).getBytes(StandardCharsets.UTF_8);
                                fromTimer = true;
                                switchButton.setChecked(false);
                            } else {
                                value = ("TAF" + totalTime.toString()).getBytes(StandardCharsets.UTF_8);
                                fromTimer = true;
                                switchButton.setChecked(true);
                            }

                            while (!mService.writeRXCharacteristic(value))
                                Thread.sleep(1000);
                            switchButton.setEnabled(false);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "시간을 숫자로만 입력해주세요", Toast.LENGTH_LONG).show();
                        }
                    }
                });
                alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "취소", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                });

                alertDialog.show();

                Button posBtn = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                posBtn.setWidth(400);
                Button negBtn = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                negBtn.setWidth(400);
            }
        });

        slidingdrawer.setOnDrawerOpenListener(new SlidingDrawer.OnDrawerOpenListener() {
            @Override
            public void onDrawerOpened() {
                slidingbtn.setRotation(180);
            }
        });

        slidingdrawer.setOnDrawerCloseListener(new SlidingDrawer.OnDrawerCloseListener() {
            public void onDrawerClosed() {
                slidingbtn.setRotation(0);
            }
        });
    }

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        } else {
            Log.d(TAG, "ACCESS_FINE_LOCATION permisson");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        } else {
            Log.d(TAG, "ACCESS_COARSE_LOCATION permisson");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService = null;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        SharedPreferences cache = getSharedPreferences("cache", Activity.MODE_PRIVATE);

        SharedPreferences.Editor editor = cache.edit();

        if (switchButton.isChecked())
            editor.putString("status", "true");
        else
            editor.putString("status", "false");

        editor.commit();
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        } else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }
}
