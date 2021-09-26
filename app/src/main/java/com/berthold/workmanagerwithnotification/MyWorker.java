package com.berthold.workmanagerwithnotification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class MyWorker extends androidx.work.Worker {

    private static final String CHANNEL_ID = "123";
    private static final int NOTIFICATION_ID = 999;
    private String date, lastDate;

    public MyWorker(Context c, WorkerParameters parameters) {
        super(c, parameters);
        Log.v("WORKER_STARTED", " Started");
        lastDate = "-";
        date = "-";

    }

    /**
     * This will be executed every 15 minutes once.
     * It will run, even when he app which started this is destroyed.
     *
     * The only way to stop it from a user perspective is to stopp the app
     * from the Android systems settings.
     *
     * @return
     */
    @NonNull
    @Override
    public Result doWork() {

        // If input data is needed, this is how to obtain it.....
        String townToCheckForCovidUpdates = getInputData().getString("townToCheckForCovidUpdates");
        Log.v("SAMPLE_DATA", townToCheckForCovidUpdates);

        // Result
        StringBuffer covidDataBuffer = new StringBuffer();
        String favLocURL = getURLForCurrentLocation(townToCheckForCovidUpdates);
        List<CovidSearchResultData> result = new ArrayList<>();

        //
        // Get covid data from the server...
        //
        try {

            URL _url = new URL(favLocURL);
            HttpURLConnection urlConnection = (HttpURLConnection) _url.openConnection();

            InputStreamReader isr = new InputStreamReader(urlConnection.getInputStream());
            BufferedReader br = new BufferedReader(isr);

            String line;

            line = br.readLine();
            covidDataBuffer.append(line);

            result = DecodeJsonResult.getResult(covidDataBuffer.toString());

            isr.close();

            Thread.sleep(150);

        } catch (Exception e) {
            Log.v("WORKER_ERROR", e + "");
            return Result.failure();
        }

        //
        // This sends the received data to all registered receivers.
        //
        String town, bez, region, casesPer10K;
        StringBuilder resultDescription = new StringBuilder();
        if (result.size() > 0) {
            date = result.get(0).getLastUpdate();
            town = result.get(0).getName();
            bez = result.get(0).getBez();
            region = result.get(0).getBundesland();
            casesPer10K = result.get(0).getCasesPer10K() + "";
            resultDescription.append("Insidenz:" + casesPer10K+","+town + "," + bez + "," + region );

        } else {
            Log.v("WORKER_ERROR", "Nothing found");
            resultDescription.append("-");
            return Result.failure();
        }

        Log.v("WORKER_RESULT", resultDescription.toString());

        Intent myIntent = new Intent();
        myIntent.putExtra("covidData", resultDescription.toString());
        myIntent.setAction("com.berthold.myapplication.CUSTOM_INTENT");
        getApplicationContext().sendBroadcast(myIntent);

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
        }

        // Create channel and set importance
        // You need to d this on Android 8.0 or higher.
        //
        // Do this, as soon as your app starts. It is also safe to do this
        // repeatedly because if your create an existing channel no
        // operation is executed.
        createNotificationChannel();

        // Create an explicit intent for an Activity in your app
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        // Notification content
        // Channel ID is needed on Android 8.0 or higher. It is ignored on lower versions
        Bitmap icon = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.covid);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                // Add a small icon hat is shown on the main screens top row and in the notification itself...
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setLargeIcon(icon)
                .setContentTitle("Corona Update")
                .setContentText(resultDescription)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        //
        lastDate=getLastDateCovidWasUpdated();
        if (date.equals(lastDate)) {
            // No Notification
            Log.v("WORKER_UPDATE", " No update, Date:" + date + "  Last:" + lastDate);
        } else {
            // Show the notification
            //
            // When this notification is already shown inside of the notification manager,
            // it's contents are updated.
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

            // notificationId is a unique int for each notification that you must define
            notificationManager.notify(NOTIFICATION_ID, builder.build());

            // Save update date
            saveLastdateUpdated(date);
            Log.v("WORKER_UPDATE", " New Value:" + date + "  Last:" + lastDate);
        }
        return Result.success();
    }

    /**
     * Returns an url that searches the network for covid info
     * regarding the users favourite location.
     *
     * @return
     */
    public String getURLForCurrentLocation(String town) {
        String url = (MessageFormat.format("https://public.opendatasoft.com/api/records/1.0/search/?dataset=covid-19-germany-landkreise&q={0}&facet=last_update&facet=name&facet=rs&facet=bez&facet=bl", town));
        return url;
    }

    /**
     * Create a notification channel.
     * <p>
     * Source: {@link:https://developer.android.com/training/notify-user/build-notification#java}
     */
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "myCannel";
            String description = "Covid data";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void saveLastdateUpdated (String d){
        SharedPreferences sharedPreferences= PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("lastUpdate", d);
        editor.commit();
    }

    private String getLastDateCovidWasUpdated (){
        SharedPreferences sharedPreferences= PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String lastDateCovidWasUpdated = sharedPreferences.getString("lastUpdate", "0.0.0");
        Log.v("WORKER_LASTUPDATE",lastDateCovidWasUpdated);
        return lastDateCovidWasUpdated;
    }
}
