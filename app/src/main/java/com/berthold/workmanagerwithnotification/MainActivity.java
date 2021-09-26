package com.berthold.workmanagerwithnotification;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.concurrent.TimeUnit;

/**
 * Check for covid data and notify user.
 *
 * Checks a dedicated location in germany for covid data and notifies the user
 * every time the data is updated.
 *
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI
        TextView covidDataDisplay=findViewById(R.id.covidDate);
        Button stop=findViewById(R.id.stop);

        //
        // This registers the broadcast receiver and receives
        // any broadcast send from our service....
        //
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String covidData=intent.getStringExtra("covidData");
                Log.v("MAIN_RECEIVED:", covidData+"--");
               covidDataDisplay.setText(covidData);
            }
        };

        registerReceiver(r, new IntentFilter("com.berthold.myapplication.CUSTOM_INTENT"));
        //
        // Defines which constraints must be met in order to do the work.
        // E.g. : One could define that the worker is executed if the device is charging or an active internet connection is available....
        //
        //Constraints constraints = new Constraints.Builder().setRequiresCharging(false).build();
        Constraints constraints=new Constraints.Builder().setRequiresCharging(false).build();

        //
        // Do work periodically
        //
        // Minimum time interval is 15 minutes!!
        //
        PeriodicWorkRequest myPeriodic = new PeriodicWorkRequest.Builder(MyWorker.class, 1, TimeUnit.MINUTES).addTag("MYWORK").setConstraints(constraints).setInputData(new Data.Builder().putString("townToCheckForCovidUpdates","Karlsruhe").build()).build();
        WorkManager.getInstance(this).enqueue(myPeriodic);


        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WorkManager.getInstance(getApplicationContext()).cancelAllWorkByTag("MYWORK");
            }
        });
    }
}