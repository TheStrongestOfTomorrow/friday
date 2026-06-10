package com.friday.assistant;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.friday.core.plugin.FridayCorePlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FridayCorePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
