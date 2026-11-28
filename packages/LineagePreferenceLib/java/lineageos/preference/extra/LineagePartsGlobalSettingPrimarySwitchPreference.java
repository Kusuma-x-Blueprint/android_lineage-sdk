/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference.extra;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.util.AttributeSet;

public class LineagePartsGlobalSettingPrimarySwitchPreference
        extends LineagePartsPrimarySwitchPreference {

    public LineagePartsGlobalSettingPrimarySwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LineagePartsGlobalSettingPrimarySwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LineagePartsGlobalSettingPrimarySwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LineagePartsGlobalSettingPrimarySwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected Uri getSettingUri(String key) {
        return key != null ? Settings.Global.getUriFor(key) : null;
    }
}
