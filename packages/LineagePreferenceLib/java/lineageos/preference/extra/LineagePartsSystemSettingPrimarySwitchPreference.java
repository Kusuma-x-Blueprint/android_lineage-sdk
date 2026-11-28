/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference.extra;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.util.AttributeSet;

public class LineagePartsSystemSettingPrimarySwitchPreference
        extends LineagePartsPrimarySwitchPreference {

    public LineagePartsSystemSettingPrimarySwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LineagePartsSystemSettingPrimarySwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LineagePartsSystemSettingPrimarySwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LineagePartsSystemSettingPrimarySwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected Uri getSettingUri(String key) {
        return key != null ? Settings.System.getUriFor(key) : null;
    }
}
