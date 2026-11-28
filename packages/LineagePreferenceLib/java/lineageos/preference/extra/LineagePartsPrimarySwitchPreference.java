/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference.extra;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.PrimarySwitchPreference;

import lineageos.preference.ConstraintsHelper;
import lineageos.preference.PartInfo;
import lineageos.preference.PartsList;
import lineageos.preference.R;
import lineageos.preference.RemotePreference;
import lineageos.preference.RemotePreferenceManager;
import lineageos.preference.SettingsHelper;

/**
 * A {@link PrimarySwitchPreference} that links to a LineageParts screen via {@link PartsList}
 * while keeping its inline switch and dynamic summary synced with a remote
 * {@link lineageos.preference.RemotePreferenceUpdater}, or directly with a Settings/LineageSettings
 * key when subclassed with {@link #getSettingUri(String)}.
 */
public class LineagePartsPrimarySwitchPreference extends PrimarySwitchPreference
        implements RemotePreferenceManager.OnRemoteUpdateListener,
        SettingsHelper.OnSettingsChangeListener {

    private final Context mContext;
    private final ConstraintsHelper mConstraints;
    private final PartInfo mPart;

    private final Uri mSettingUri;
    private final boolean mDefaultValue;

    private Uri mWatchUri;
    private boolean mAttached;

    public LineagePartsPrimarySwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        mConstraints = new ConstraintsHelper(context, attrs, this);

        mPart = PartsList.get(context).getPartInfo(getKey());
        if (mPart == null) {
            throw new RuntimeException("Part not found: " + getKey());
        }

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.lineage_LineagePartsPrimarySwitchPreference);
        String settingKey = a.getString(
                R.styleable.lineage_LineagePartsPrimarySwitchPreference_settingKey);
        a.recycle();

        TypedArray defAttrs = context.obtainStyledAttributes(
                attrs, new int[]{android.R.attr.defaultValue});
        mDefaultValue = defAttrs.getBoolean(0, false);
        defAttrs.recycle();

        mSettingUri = getSettingUri(!TextUtils.isEmpty(settingKey) ? settingKey : getKey());
        mWatchUri = mSettingUri;

        setPreferenceDataStore(new DataStore());
        updatePreference();
        setIntent(mPart.getIntentForActivity());
    }

    public LineagePartsPrimarySwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public LineagePartsPrimarySwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, ConstraintsHelper.getAttr(context,
                androidx.preference.R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public LineagePartsPrimarySwitchPreference(Context context) {
        this(context, null);
    }

    /**
     * Subclasses override this to bind the switch directly to a Settings/LineageSettings URI
     * identified by {@code key}. Returning {@code null} delegates switch state to the remote
     * {@link lineageos.preference.RemotePreferenceUpdater}.
     */
    protected Uri getSettingUri(String key) {
        return null;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mConstraints.onAttached();
        mAttached = true;
        if (isAvailable()) {
            if (mSettingUri != null) {
                setChecked(SettingsHelper.get(mContext).getBoolean(mSettingUri, mDefaultValue));
            }
            RemotePreferenceManager.get(mContext).attach(getKey(), this);
            if (mWatchUri != null) {
                SettingsHelper.get(mContext).startWatching(this, mWatchUri);
            }
        }
    }

    @Override
    public void onDetached() {
        super.onDetached();
        mAttached = false;
        RemotePreferenceManager.get(mContext).detach(getKey(), this);
        if (mWatchUri != null) {
            SettingsHelper.get(mContext).stopWatching(this);
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mConstraints.onBindViewHolder(holder);
    }

    public void setAvailable(boolean available) {
        mConstraints.setAvailable(available);
    }

    public boolean isAvailable() {
        return mConstraints.isAvailable();
    }

    @Override
    public Intent getReceiverIntent() {
        return RemotePreference.resolveReceiverIntent(mContext, getIntent(), getKey());
    }

    @Override
    public void onRemoteUpdated(Bundle bundle) {
        if (bundle.containsKey(PartsList.EXTRA_PART)) {
            PartInfo update = bundle.getParcelable(PartsList.EXTRA_PART);
            if (update != null) {
                mPart.updateFrom(update);
                updatePreference();
            }
        }
        final boolean remoteToggle = mSettingUri == null;
        if (remoteToggle && bundle.containsKey(RemotePreference.EXTRA_WATCH_URI)) {
            String watchUriStr = bundle.getString(RemotePreference.EXTRA_WATCH_URI);
            Uri watchUri = !TextUtils.isEmpty(watchUriStr) ? Uri.parse(watchUriStr) : null;
            updateWatchUri(watchUri);
        }
        if (isAvailable()) {
            if (remoteToggle && bundle.containsKey(RemotePreference.EXTRA_CHECKED)) {
                setChecked(bundle.getBoolean(RemotePreference.EXTRA_CHECKED));
            }
            if (bundle.containsKey(RemotePreference.EXTRA_SWITCH_ENABLED)) {
                setSwitchEnabled(bundle.getBoolean(RemotePreference.EXTRA_SWITCH_ENABLED, true));
            }
        }
    }

    @Override
    public void onSettingsChanged(Uri settingsUri) {
        if (settingsUri == null) {
            return;
        }
        if (mSettingUri != null) {
            setChecked(SettingsHelper.get(mContext).getBoolean(mSettingUri, mDefaultValue));
        }
        RemotePreferenceManager.get(mContext).refresh(getKey());
    }

    private void updateWatchUri(Uri newUri) {
        if (newUri == null || newUri.equals(mWatchUri)) {
            return;
        }
        if (mAttached && isAvailable()) {
            if (mWatchUri != null) {
                SettingsHelper.get(mContext).stopWatching(this);
            }
            SettingsHelper.get(mContext).startWatching(this, newUri);
        }
        mWatchUri = newUri;
    }

    private void updatePreference() {
        if (isAvailable() != mPart.isAvailable()) {
            setAvailable(mPart.isAvailable());
        }
        if (isAvailable()) {
            setTitle(mPart.getTitle());
            setSummary(mPart.getSummary());
        }
    }

    private class DataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(String key, boolean value) {
            if (mSettingUri == null) {
                RemotePreferenceManager.get(mContext).requestSetChecked(getKey(), value);
            } else {
                SettingsHelper.get(mContext).putBoolean(mSettingUri, value);
                RemotePreferenceManager.get(mContext).refresh(getKey());
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            if (mSettingUri != null) {
                return SettingsHelper.get(mContext).getBoolean(mSettingUri, defaultValue);
            }
            return Boolean.TRUE.equals(getCheckedState());
        }
    }
}
