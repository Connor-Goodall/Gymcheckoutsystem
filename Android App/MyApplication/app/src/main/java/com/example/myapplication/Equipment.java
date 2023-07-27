package com.example.myapplication;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.util.TypedValue;
import android.graphics.Color;

import java.util.UUID;

public class Equipment {
    public BluetoothGattCharacteristic getPositionInQueue() {
        return positionInQueue;
    }

    public void setPositionInQueue(BluetoothGattCharacteristic positionInQueue) {
        this.positionInQueue = positionInQueue;
    }

    private BluetoothGattCharacteristic positionInQueue;

    public BluetoothGattCharacteristic getNotification() {
        return notification;
    }

    public void setNotification(BluetoothGattCharacteristic notification) {
        this.notification = notification;
    }

    private BluetoothGattCharacteristic notification;

    public BluetoothGattCharacteristic getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(BluetoothGattCharacteristic timeLeft) {
        this.timeLeft = timeLeft;
    }

    private BluetoothGattCharacteristic timeLeft;
    private String name;
    private String id;
    private String position;
    private String time;
    TextView nameView;
    TextView positionView;
    TextView timeView;
    private TableRow tableRow;
    private BluetoothGatt connectedGatt;
    private final String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private final String posistionInQueueUUID = "00000001-0000-1000-8000-00805f9b34fb";
    private final String availabilityUUID = "00000002-0000-1000-8000-00805f9b34fb";
    Context mainContext;
    Equipment(BluetoothGatt gatt, Context context){
        mainContext = context;
        connectedGatt = gatt;
        time = "4";
        name = "example Name";
        position = "0";

    }
    TableRow buildEquipmentView(){
        tableRow = new TableRow(mainContext);
        tableRow.setLayoutParams(new TableRow.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT
        ));
        tableRow.setPadding(0,0,0,0);
        nameView = new TextView(mainContext);
        positionView = new TextView(mainContext);
        timeView = new TextView(mainContext);

        nameView.setGravity(Gravity.CENTER);
        positionView.setGravity(Gravity.CENTER);
        timeView.setGravity(Gravity.CENTER);
        nameView.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, .3f));
        positionView.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, .2f));
        timeView.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, .3f));

        nameView.setText(name);
        positionView.setText(position);
        timeView.setText(time);

        tableRow.addView(nameView);
        tableRow.addView(positionView);
        tableRow.addView(timeView);
        return tableRow;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void updateEquipmentView(){
        if(position.equals("0")){
            positionView.setText("In Use");
        }else{
            positionView.setText(position);
        }


        timeView.setText(time);
    }

    public void updateEquipmentValues(){

    }
    public TableRow getTableRow(){
        return tableRow;
    }
    public BluetoothGatt getConnectedGatt() {
        return connectedGatt;
    }

}
