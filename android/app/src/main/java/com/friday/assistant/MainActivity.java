package com.friday.assistant;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.friday.core.plugin.FridayCorePlugin;
import com.friday.core.plugin.FridaySpeechPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FridayCorePlugin.class);
        registerPlugin(FridaySpeechPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
