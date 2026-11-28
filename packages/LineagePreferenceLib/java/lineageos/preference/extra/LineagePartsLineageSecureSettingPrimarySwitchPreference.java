/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference.extra;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;

import lineageos.providers.LineageSettings;

public class LineagePartsLineageSecureSettingPrimarySwitchPreference
        extends LineagePartsPrimarySwitchPreference {

    public LineagePartsLineageSecureSettingPrimarySwitchPreference(Context context,
            AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LineagePartsLineageSecureSettingPrimarySwitchPreference(Context context,
            AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LineagePartsLineageSecureSettingPrimarySwitchPreference(Context context,
            AttributeSet attrs) {
        super(context, attrs);
    }

    public LineagePartsLineageSecureSettingPrimarySwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected Uri getSettingUri(String key) {
        return key != null ? LineageSettings.Secure.getUriFor(key) : null;
    }
}
