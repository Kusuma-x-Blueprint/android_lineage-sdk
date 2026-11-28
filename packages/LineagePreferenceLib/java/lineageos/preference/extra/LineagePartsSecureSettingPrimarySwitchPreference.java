/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference.extra;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.util.AttributeSet;

public class LineagePartsSecureSettingPrimarySwitchPreference
        extends LineagePartsPrimarySwitchPreference {

    public LineagePartsSecureSettingPrimarySwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LineagePartsSecureSettingPrimarySwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LineagePartsSecureSettingPrimarySwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LineagePartsSecureSettingPrimarySwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected Uri getSettingUri(String key) {
        return key != null ? Settings.Secure.getUriFor(key) : null;
    }
}
