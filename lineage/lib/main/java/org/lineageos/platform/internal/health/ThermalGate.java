/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.health;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import lineageos.providers.LineageSettings;

public final class ThermalGate {
    private static boolean sBlocked;

    public static int getBatteryTemperature(Context context) {
        Intent intent = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_EXPORTED);
        return intent != null ?
                intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) * 100 : 0;
    }

    public static boolean isChargingBlocked(Context context) {
        int limit = LineageSettings.System.getInt(context.getContentResolver(),
                LineageSettings.System.THERMAL_BATTERY_LIMIT, 0);
        if (limit <= 0) {
            sBlocked = false;
            return false;
        }
        int temperature = getBatteryTemperature(context);
        if (sBlocked) {
            sBlocked = temperature > limit - 2000;
        } else {
            sBlocked = temperature >= limit;
        }
        return sBlocked;
    }
}
