package yasuaki.kyoto.com.sunshinewatchface;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;

public class WatchFaceConfigActivity extends WearableActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_face_config);
    }
}
