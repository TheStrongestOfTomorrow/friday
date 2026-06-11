package com.friday.assistant;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.friday.assistant.data.AppDatabase;
import com.friday.assistant.data.CustomCommand;

import java.util.List;

/**
 * Friday — Custom Commands Activity
 *
 * Allows users to create, view, and delete custom voice commands.
 * Users can map spoken phrases to specific actions:
 *   - LAUNCH_APP: Open any installed application
 *   - TOGGLE_SETTING: Toggle flashlight, Wi-Fi, etc.
 *   - TTS_REPLY: Speak a custom text response
 *   - OPEN_URL: Open a web link
 */
public class CustomCommandsActivity extends AppCompatActivity {

    private AppDatabase db;
    private LinearLayout commandsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = AppDatabase.getInstance(this);

        // Build UI programmatically (dark theme)
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_primary));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageButton btnBack = new ImageButton(this);
        btnBack.setImageResource(R.drawable.ic_arrow_back);
        btnBack.setBackground(null);
        btnBack.setColorFilter(ContextCompat.getColor(this, R.color.text_primary));
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);

        TextView title = new TextView(this);
        title.setText("  Custom Commands");
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);
        root.addView(header);

        // Description
        TextView desc = new TextView(this);
        desc.setText("Create voice shortcuts. When you say the trigger phrase, Friday runs the action.");
        desc.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        desc.setTextSize(14);
        desc.setPadding(0, dp(16), 0, dp(16));
        root.addView(desc);

        // ─── Add New Command Section ─────────────────────────────

        TextView addLabel = new TextView(this);
        addLabel.setText("Add New Command");
        addLabel.setTextColor(ContextCompat.getColor(this, R.color.brand_purple));
        addLabel.setTextSize(16);
        addLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        addLabel.setPadding(0, dp(8), 0, dp(8));
        root.addView(addLabel);

        // Trigger phrase input
        TextView triggerLabel = new TextView(this);
        triggerLabel.setText("When I say:");
        triggerLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        triggerLabel.setTextSize(13);
        root.addView(triggerLabel);

        EditText etTrigger = new EditText(this);
        etTrigger.setHint("e.g. party time");
        etTrigger.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        etTrigger.setHintTextColor(ContextCompat.getColor(this, R.color.text_muted));
        etTrigger.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_input));
        etTrigger.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(etTrigger, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Action type selector
        TextView actionLabel = new TextView(this);
        actionLabel.setText("Action:");
        actionLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        actionLabel.setTextSize(13);
        LinearLayout.LayoutParams alLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alLp.topMargin = dp(12);
        root.addView(actionLabel, alLp);

        String[] actionTypes = {"Open App", "Speak Response", "Toggle Flashlight", "Open Link"};
        Spinner spinnerAction = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, actionTypes);
        spinnerAction.setAdapter(adapter);
        root.addView(spinnerAction);

        // Action target input
        TextView targetLabel = new TextView(this);
        targetLabel.setText("Target (app name, text, or URL):");
        targetLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        targetLabel.setTextSize(13);
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlLp.topMargin = dp(8);
        root.addView(targetLabel, tlLp);

        EditText etTarget = new EditText(this);
        etTarget.setHint("e.g. Spotify, Hello!, https://example.com");
        etTarget.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        etTarget.setHintTextColor(ContextCompat.getColor(this, R.color.text_muted));
        etTarget.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_input));
        etTarget.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(etTarget, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Add button
        androidx.appcompat.widget.AppCompatButton btnAdd = new androidx.appcompat.widget.AppCompatButton(this);
        btnAdd.setText("Add Command");
        btnAdd.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        btnAdd.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_button_primary));
        btnAdd.setPadding(dp(32), dp(12), dp(32), dp(12));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = dp(16);
        addLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        root.addView(btnAdd, addLp);

        btnAdd.setOnClickListener(v -> {
            String trigger = etTrigger.getText().toString().trim().toLowerCase();
            String target = etTarget.getText().toString().trim();
            int actionPos = spinnerAction.getSelectedItemPosition();

            if (trigger.isEmpty()) {
                Toast.makeText(this, "Please enter a trigger phrase", Toast.LENGTH_SHORT).show();
                return;
            }

            String actionType;
            String actionTarget;

            switch (actionPos) {
                case 0: // Open App
                    actionType = "LAUNCH_APP";
                    actionTarget = target.isEmpty() ? trigger : target;
                    break;
                case 1: // Speak Response
                    actionType = "TTS_REPLY";
                    actionTarget = target.isEmpty() ? "You said " + trigger : target;
                    break;
                case 2: // Toggle Flashlight
                    actionType = "TOGGLE_SETTING";
                    actionTarget = "FLASHLIGHT_ON";
                    break;
                case 3: // Open Link
                    actionType = "OPEN_URL";
                    actionTarget = target.startsWith("http") ? target : "https://" + target;
                    break;
                default:
                    actionType = "TTS_REPLY";
                    actionTarget = target;
            }

            CustomCommand cmd = new CustomCommand(trigger, actionType, actionTarget,
                    actionTypes[actionPos]);

            new Thread(() -> {
                db.customCommandDao().insert(cmd);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Command added: \"" + trigger + "\"", Toast.LENGTH_SHORT).show();
                    etTrigger.setText("");
                    etTarget.setText("");
                    refreshCommandsList(commandsList);
                });
            }).start();
        });

        // ─── Existing Commands ───────────────────────────────────

        TextView existingLabel = new TextView(this);
        existingLabel.setText("Your Commands");
        existingLabel.setTextColor(ContextCompat.getColor(this, R.color.brand_purple));
        existingLabel.setTextSize(16);
        existingLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams elLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elLp.topMargin = dp(24);
        root.addView(existingLabel, elLp);

        commandsList = new LinearLayout(this);
        commandsList.setOrientation(LinearLayout.VERTICAL);
        root.addView(commandsList);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCommandsList(commandsList);
    }

    private void refreshCommandsList(LinearLayout container) {
        new Thread(() -> {
            List<CustomCommand> commands = db.customCommandDao().getAllCommands();
            runOnUiThread(() -> {
                container.removeAllViews();

                if (commands.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("No custom commands yet. Add one above!");
                    empty.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                    empty.setTextSize(14);
                    empty.setPadding(0, dp(16), 0, dp(16));
                    container.addView(empty);
                    return;
                }

                for (CustomCommand cmd : commands) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(8), 0, dp(8));

                    LinearLayout textCol = new LinearLayout(this);
                    textCol.setOrientation(LinearLayout.VERTICAL);
                    textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    TextView tvTrigger = new TextView(this);
                    tvTrigger.setText("\"" + cmd.getTriggerPhrase() + "\"");
                    tvTrigger.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    tvTrigger.setTextSize(15);
                    textCol.addView(tvTrigger);

                    TextView tvAction = new TextView(this);
                    tvAction.setText(cmd.getLabel() + " → " + cmd.getActionTarget());
                    tvAction.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                    tvAction.setTextSize(12);
                    textCol.addView(tvAction);

                    row.addView(textCol);

                    // Delete button
                    androidx.appcompat.widget.AppCompatButton btnDelete = new androidx.appcompat.widget.AppCompatButton(this);
                    btnDelete.setText("Delete");
                    btnDelete.setTextColor(ContextCompat.getColor(this, R.color.danger_red));
                    btnDelete.setBackground(null);
                    btnDelete.setTextSize(12);
                    btnDelete.setOnClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete Command?")
                                .setMessage("Remove the command \"" + cmd.getTriggerPhrase() + "\"?")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    new Thread(() -> {
                                        db.customCommandDao().delete(cmd);
                                        runOnUiThread(() -> refreshCommandsList(container));
                                    }).start();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    });
                    row.addView(btnDelete);

                    container.addView(row);

                    // Divider
                    View divider = new View(this);
                    divider.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_card));
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    container.addView(divider);
                }
            });
        }).start();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
