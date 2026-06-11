package com.friday.assistant.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Friday — Custom Command Entity
 *
 * Maps a user-defined trigger phrase to a specific action.
 * Users can create their own voice shortcuts like:
 *   "party time" -> Launch Spotify
 *   "goodnight" -> Toggle flashlight off
 *   "motivate me" -> Speak a preset message
 */
@Entity(tableName = "custom_commands")
public class CustomCommand {

    @NonNull
    @PrimaryKey
    private String triggerPhrase;

    /** Action type: LAUNCH_APP, TOGGLE_SETTING, TTS_REPLY, OPEN_URL */
    private String actionType;

    /** Action target: package name, setting name, or text to speak */
    private String actionTarget;

    /** Human-readable label for display in the UI */
    private String label;

    /** Timestamp when this command was created */
    private long createdAt;

    public CustomCommand() {}

    @Ignore
    public CustomCommand(@NonNull String triggerPhrase, String actionType, String actionTarget, String label) {
        this.triggerPhrase = triggerPhrase;
        this.actionType = actionType;
        this.actionTarget = actionTarget;
        this.label = label;
        this.createdAt = System.currentTimeMillis();
    }

    // ─── Getters / Setters ───────────────────────────────────────

    public String getTriggerPhrase() { return triggerPhrase; }
    public void setTriggerPhrase(String triggerPhrase) { this.triggerPhrase = triggerPhrase; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getActionTarget() { return actionTarget; }
    public void setActionTarget(String actionTarget) { this.actionTarget = actionTarget; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
