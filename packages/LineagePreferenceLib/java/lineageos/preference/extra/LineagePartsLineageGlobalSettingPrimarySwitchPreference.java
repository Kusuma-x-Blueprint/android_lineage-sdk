/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference.extra;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;

import lineageos.providers.LineageSettings;

public class LineagePartsLineageGlobalSettingPrimarySwitchPreference
        extends LineagePartsPrimarySwitchPreference {

    public LineagePartsLineageGlobalSettingPrimarySwitchPreference(Context context,
            AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LineagePartsLineageGlobalSettingPrimarySwitchPreference(Context context,
            AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LineagePartsLineageGlobalSettingPrimarySwitchPreference(Context context,
            AttributeSet attrs) {
        super(context, attrs);
    }

    public LineagePartsLineageGlobalSettingPrimarySwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected Uri getSettingUri(String key) {
        return key != null ? LineageSettings.Global.getUriFor(key) : null;
    }
}
