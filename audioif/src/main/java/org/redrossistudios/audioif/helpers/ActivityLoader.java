package org.redrossistudios.audioif.helpers;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

public class ActivityLoader {
    public static void load(AppCompatActivity origin ,Class<?> activityToLoad){
        Intent intent = new Intent(origin, activityToLoad);
        origin.startActivity(intent);
    }
}
