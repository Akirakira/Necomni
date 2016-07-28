package com.example.mqttsample;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

//外部ライブラリ
import org.eclipse.paho.android.service.*;
import org.eclipse.paho.client.mqttv3.*;

import com.example.mqttsample.R.id;

public class MainActivity extends Activity implements OnClickListener {

	//MQTTクライアントのインスタス作成
	MqttAndroidClient mqttClient ;
	private String sendMsg = null;
	
	//BLEインスタンス作成
	BluetoothManager bleManager;
	private BluetoothAdapter bleAdapter;
	private BluetoothGatt bleGatt;
	private BluetoothDevice bleDev = null;
	private List<BluetoothGattCharacteristic> chara;
	public final static UUID UUID_BATTERY_SERVICE =
            UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
	public final static UUID UUID_ORI_SERVICE = UUID.fromString("e5fe9a7c-272c-11e6-b67b-9e71128cae77");
	private static final UUID UUID_BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
	private static final UUID  UUID_ORI_LEVEL = UUID.fromString("709873d2-272e-11e6-b67b-9e71128cae77");

	//GPS :BLE接続されたのち現在地情報を取得しサーバー送信情報へ不可する
	private final int REQUEST_PERMISSION = 10;
	private LocationManager locationManager;
	
	//Timer 一定時間処理用：BLE接続があったあとの10分間はBLEデバイスのAutoサーチをしない
	//手動でつなぎに行くことはできる
	Timer timer = new Timer();
	Handler timerHandler = new Handler();

	//Viewインスタンス作成
	private Button connectButton,createButton,scanDevButton,connectDevButton,disconnectButton,readButton;
	private TextView txA;
	private ListView listview;
	
	//その他変数
	private static final long SCAN_PERIOD = 10000;
	private boolean mScanning;
	private Handler mHandler;
	private String devUuid = null;
	private ArrayAdapter<String> devList;
	private List<String> allList = new ArrayList<String>();
	private char loopFlag = 0;
	private Typeface typeFace;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		bleManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		bleAdapter = bleManager.getAdapter();
		
		mHandler = new Handler();
		//BLE設定
		if(bleAdapter == null || !bleAdapter.isEnabled()){
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, 1);
		}
		
		//GPSパーミッション android6, API23以上でパーミッション確認
		if(Build.VERSION.SDK_INT >= 23){
			//すでに許可しているときはなにもしない
			if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
				Toast toast = Toast.makeText(this, "enable GPS parmission.", Toast.LENGTH_SHORT);
				toast.show();
				getLacation();
				}else{//許可がない時は許可をとる
				if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
					ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION);
				}else{
					Toast toast = Toast.makeText(this, "please parmission.", Toast.LENGTH_SHORT);
					toast.show();
					ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION);
				}
			}
		}else{
			//getLacation();
		}
		
		typeFace = Typeface.createFromAsset(getAssets(), "Kazesawa-Regular.ttf");
		txA = (TextView)findViewById(id.textViewA);
		listview = (ListView)findViewById(R.id.listViewDe);
		devList = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,allList);
		listview.setAdapter(devList);
		
		connectButton = (Button)findViewById(id.button1M);
		createButton = (Button)findViewById(id.buttonMC);
		scanDevButton = (Button)findViewById(id.buttonB);
		connectDevButton = (Button)findViewById(id.buttonG);
		disconnectButton = (Button)findViewById(id.buttonCl);
		readButton = (Button)findViewById(id.buttonR);
		
		txA.setText("プロセス表示");
		txA.setTypeface(typeFace);
		connectButton.setTypeface(typeFace);
		createButton.setTypeface(typeFace);
		scanDevButton.setTypeface(typeFace);
		connectButton.setTypeface(typeFace);
		disconnectButton.setTypeface(typeFace);
		readButton.setTypeface(typeFace);
		
		connectButton.setOnClickListener(this);
		createButton.setOnClickListener(this);
		scanDevButton.setOnClickListener(this);
		connectDevButton.setOnClickListener(this);
		disconnectButton.setOnClickListener(this);
		readButton.setOnClickListener(this);
		
	}
	
	//GPS get
	private void getLacation(){
		locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1000, 
				new LocationListener(){

					@Override
					public void onLocationChanged(Location location) {
						// TODO Auto-generated method stub
						String lat = String.valueOf(location.getLatitude());
						String lon = String.valueOf(location.getLongitude());
						BigDecimal latDeci = new BigDecimal(lat);
						BigDecimal lonDeci = new BigDecimal(lon);
						latDeci = latDeci.setScale(2, BigDecimal.ROUND_HALF_UP);
						lonDeci = lonDeci.setScale(2, BigDecimal.ROUND_HALF_UP);
						txA.setText("GPS : lat="+latDeci+",lon="+lonDeci);
						sendMsg += "GPS : lat = "+latDeci+", lon = "+lonDeci;
					}

					@Override
					public void onStatusChanged(String provider, int status, Bundle extras) {
						// TODO Auto-generated method stub
					}

					@Override
					public void onProviderEnabled(String provider) {
						// TODO Auto-generated method stub
					}

					@Override
					public void onProviderDisabled(String provider) {
						// TODO Auto-generated method stub
					}
		});
	}
	
	//BLEデバイススキャン
	private void scanLeDevice(final boolean enable){
		Log.i("scanLeDevice : ",  "scanning");
		if(enable){
			mHandler.postDelayed(new Runnable(){
				@Override
				public void run() {
					// TODO Auto-generated method stub
					mScanning = false;
					bleAdapter.stopLeScan(lescanCallback);
				}
			}, SCAN_PERIOD);
			mScanning = true;
			bleAdapter.startLeScan(lescanCallback);
		}else{
			mScanning = false;
			bleAdapter.stopLeScan(lescanCallback);
		}
	}
	
	//BLE scan コールバック
	private BluetoothAdapter.LeScanCallback lescanCallback = new BluetoothAdapter.LeScanCallback(){
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
			// TODO Auto-generated method stub
			runOnUiThread(new Runnable(){
				@Override
				public void run() {
					// TODO Auto-generated method stub
					String devName = device.getName();
					String message = "name = " + device.getName();
					message += ", uuid = " + device.getUuids();
					message += ", boundstate = " + device.getBondState();
					message += ", address = " + device.getAddress();
					message += ", type = " + device.getType();
					devList.add(device.getName());
					Log.i("scan device :", message);
					
					if(device.getName().equals("Necom_Mike")){
						Log.i("scan device !!!! :", device.getName());
						bleDev = device;
						devUuid = device.getAddress();
						txA.setText("猫がいます！");
						//intervalDeviceConnect();
					}
					//gatt();
				}
			});
		}
	};
	
	
	//検索後のデバイスとつなぎに行く作業
	private final BluetoothGattCallback gattCallback = new BluetoothGattCallback(){

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			// TODO Auto-generated method stub
			super.onConnectionStateChange(gatt, status, newState);
			if(newState == BluetoothProfile.STATE_CONNECTED){//接続した
				Log.i("GATT! : ", "Connected to GATT server : " + bleDev.getName());
				Log.i("GATT! SERVICE itirannn : ", "" + bleGatt.discoverServices());
				
			}else if(newState == BluetoothProfile.STATE_DISCONNECTED){//接続していない
				Log.i("GATT! : ", "Disconnected from GATT server : " + bleDev.getName());
			}
		}

		//読み込み通知：非同期
		//読み込み通知を得るには読み込み通知要求をしなければいけない
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			// TODO Auto-generated method stub
			super.onCharacteristicRead(gatt, characteristic, status);
			Log.i("GATT : ", "Read!!");
			if(status == BluetoothGatt.GATT_SUCCESS){
				Log.i("CHARA! : ", "SUCCESS..." + characteristic);
				if(UUID_BATTERY_LEVEL.equals(characteristic.getUuid())){
					Log.i("SERVICE! : ", "Battery LEVEL..." + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
					sendMsg += " BL=" + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
					//txA.setText("Battery : " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
				}
				if(UUID_BATTERY_SERVICE.equals(characteristic.getUuid())){
					Log.i("SERVICE! : ", "Battery SERVICE..." + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
				}
				if(UUID_ORI_LEVEL.equals(characteristic.getUuid())){
					Log.i("SERVICE! : ", "original LEVEL..." + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
					sendMsg += " ORI=" + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				}
				if(UUID_ORI_SERVICE.equals(characteristic.getUuid())){
					Log.i("SERVICE! : ", "original SERVICE..." + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
				}else{
					Log.i("SERVICE? : ", " ??? Service..." + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
				}
			}
		}

		//書き込み通知：非同期
		//書き込み通知を得るには書き込み要求をしなければならない
		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			// TODO Auto-generated method stub
			super.onCharacteristicWrite(gatt, characteristic, status);
			Log.i("GATT : ", "Write!!");
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			// TODO Auto-generated method stub
			super.onCharacteristicChanged(gatt, characteristic);
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			// TODO Auto-generated method stub
			super.onServicesDiscovered(gatt, status);
			if(status == BluetoothGatt.GATT_SUCCESS){
				List<BluetoothGattService> gattList = gatt.getServices();
				for(BluetoothGattService gatts : gattList){
					UUID uuid = gatts.getUuid();
					List<BluetoothGattCharacteristic> characts = gatts.getCharacteristics();
					chara = gatts.getCharacteristics();
					for(BluetoothGattCharacteristic charact : characts){
						gatt.setCharacteristicNotification(charact, true);
						Log.i("BluetoothGattChara : ", "" + charact.getProperties());
					}
					Log.i("BluetoothGattService : ", String.valueOf(gatts.getInstanceId()));
				}
			}
		}
		
	};
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		try{
			mqttClient.disconnect();
			mqttClient.unregisterResources();
		}catch(Exception e){
			Log.i("MTTT DISconnect : ",  "失敗");
		}
		
		Log.i("PAUSE!!! : ",  "now! onPause!");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
		
	}

	void gatt(){
		bleGatt = bleDev.connectGatt(this, false, gattCallback);
		txA.setText("connectDevice...");
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if(v == connectButton){
			Log.d("TAGTAG : ","connectButton!!PUSH!!");
			try{
				sendMsg = "Hello World! ";
				mqttClient.publish("NyanNyan", sendMsg.getBytes(), 2, false);//QoS:2 = one time!!
				txA.setText("pub MQTT SUCCESS!");
				sendMsg = null;
				//mqttClient.disconnect();
			}catch(Exception e){
				e.printStackTrace();
				txA.setText("pub MQTT Failed!");
			}
		}else if(v == createButton){
			Log.d("TAGTAG : ","createButton!!PUSH!!");
			mqttClient = new MqttAndroidClient(this, "tcp://beam.soracom.io:1883","test");
			//mqttClient = new MqttAndroidClient(this, "mqtt://beam.soracom.io:1883","test");
			try{
				mqttClient.connect();
				txA.setText("Connect SUCCESS!");
			}catch(Exception e){
				e.printStackTrace();
				txA.setText("Connect Failed!");
			}
			getLacation();
		}else if(v == scanDevButton){
			devList.clear();
			scanLeDevice(true);
			txA.setText("scanDevice...");
		}else if(v == connectDevButton){
			if(bleDev != null){
				bleGatt = bleDev.connectGatt(this, false, gattCallback);
				txA.setText("connectDevice...");
				/**/
			}
		}else if(v == disconnectButton){
			bleGatt.close();
			devList.clear();
			txA.setText("disconnectDevice!");
		}else if(v == readButton){
			for(BluetoothGattCharacteristic charact : chara){
				bleGatt.readCharacteristic(charact);
			}
		}
	}
}
