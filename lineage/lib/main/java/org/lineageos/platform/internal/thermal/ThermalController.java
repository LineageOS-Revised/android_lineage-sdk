/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.thermal;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import lineageos.providers.LineageSettings;

import org.lineageos.platform.internal.health.ThermalGate;

import vendor.lineage.health.IChargingControl;
import vendor.lineage.thermal.IThermalControl;

public class ThermalController {
    private static final String TAG = "LineageThermal";

    private static final int CPU_MIN = 65000;
    private static final int CPU_MAX = 95000;
    private static final int BATTERY_MIN = 35000;
    private static final int BATTERY_MAX = 45000;
    private static final int HYSTERESIS = 2000;

    private final Context mContext;
    private final ContentResolver mResolver;

    private IThermalControl mThermalControl;
    private IChargingControl mChargingControl;
    private boolean mBatteryBlocked;
    private BroadcastReceiver mBatteryReceiver;

    public ThermalController(Context context) {
        mContext = context;
        mResolver = context.getContentResolver();
    }

    private IThermalControl getThermalControl() {
        if (mThermalControl == null) {
            mThermalControl = IThermalControl.Stub.asInterface(
                    ServiceManager.getService(IThermalControl.DESCRIPTOR + "/default"));
        }
        return mThermalControl;
    }

    private IChargingControl getChargingControl() {
        if (mChargingControl == null) {
            mChargingControl = IChargingControl.Stub.asInterface(
                    ServiceManager.getService(IChargingControl.DESCRIPTOR + "/default"));
        }
        return mChargingControl;
    }

    public boolean isSupported() {
        IThermalControl control = getThermalControl();
        if (control == null) {
            return false;
        }
        try {
            return control.isSupported();
        } catch (RemoteException e) {
            mThermalControl = null;
            return false;
        }
    }

    public int getCpuTemperature() {
        IThermalControl control = getThermalControl();
        try {
            return control != null ? control.getCpuTemperature() : 0;
        } catch (RemoteException e) {
            mThermalControl = null;
            return 0;
        }
    }

    public int getCpuLimit() {
        return LineageSettings.System.getInt(mResolver,
                LineageSettings.System.THERMAL_CPU_LIMIT, 0);
    }

    public boolean setCpuLimit(int millidegC) {
        if (millidegC != 0 && (millidegC < CPU_MIN || millidegC > CPU_MAX)) {
            return false;
        }
        LineageSettings.System.putInt(mResolver,
                LineageSettings.System.THERMAL_CPU_LIMIT, millidegC);
        applyCpuLimit(millidegC);
        return true;
    }

    public int getBatteryTemperature() {
        return ThermalGate.getBatteryTemperature(mContext);
    }

    public int getBatteryLimit() {
        return LineageSettings.System.getInt(mResolver,
                LineageSettings.System.THERMAL_BATTERY_LIMIT, 0);
    }

    public boolean setBatteryLimit(int millidegC) {
        if (millidegC != 0 && (millidegC < BATTERY_MIN || millidegC > BATTERY_MAX)) {
            return false;
        }
        LineageSettings.System.putInt(mResolver,
                LineageSettings.System.THERMAL_BATTERY_LIMIT, millidegC);
        Log.i(TAG, "Battery limit " + millidegC);
        if (millidegC > 0) {
            registerBatteryReceiver();
        } else {
            unregisterBatteryReceiver();
            unblockCharging();
        }
        return true;
    }

    public void applyPersisted() {
        applyCpuLimit(getCpuLimit());
        if (getBatteryLimit() > 0) {
            registerBatteryReceiver();
        }
    }

    private void applyCpuLimit(int millidegC) {
        IThermalControl control = getThermalControl();
        if (control == null) {
            Log.w(TAG, "No thermal HAL, cannot apply CPU limit");
            return;
        }
        try {
            if (millidegC > 0) {
                control.setCpuLimit(millidegC);
            } else {
                control.clearCpuLimit();
            }
            Log.i(TAG, "CPU limit " + millidegC);
        } catch (RemoteException e) {
            mThermalControl = null;
            Log.e(TAG, "Failed to apply CPU limit", e);
        }
    }

    private boolean isChargingControlEnabled() {
        return LineageSettings.System.getInt(mResolver,
                LineageSettings.System.CHARGING_CONTROL_ENABLED, 0) != 0;
    }

    private void blockCharging() {
        IChargingControl control = getChargingControl();
        if (control == null) {
            return;
        }
        try {
            if (!mBatteryBlocked || control.getChargingEnabled()) {
                control.setChargingEnabled(false);
                Log.i(TAG, "Charging blocked by battery limit");
            }
            mBatteryBlocked = true;
        } catch (RemoteException e) {
            mChargingControl = null;
            Log.e(TAG, "Failed to stop charging", e);
        }
    }

    private void unblockCharging() {
        IChargingControl control = getChargingControl();
        if (mBatteryBlocked && control != null && !isChargingControlEnabled()) {
            try {
                control.setChargingEnabled(true);
                Log.i(TAG, "Charging resumed");
            } catch (RemoteException e) {
                mChargingControl = null;
                Log.e(TAG, "Failed to resume charging", e);
            }
        }
        mBatteryBlocked = false;
    }

    private void handleBatteryTemperature(int millidegC) {
        int limit = getBatteryLimit();
        if (limit <= 0 || getChargingControl() == null) {
            mBatteryBlocked = false;
            return;
        }
        if (mBatteryBlocked) {
            if (millidegC <= limit - HYSTERESIS) {
                unblockCharging();
            } else {
                blockCharging();
            }
        } else if (millidegC >= limit) {
            blockCharging();
        }
    }

    private void registerBatteryReceiver() {
        if (mBatteryReceiver != null || getChargingControl() == null) {
            return;
        }
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleBatteryTemperature(
                        intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) * 100);
            }
        };
        mContext.registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        handleBatteryTemperature(getBatteryTemperature());
    }

    private void unregisterBatteryReceiver() {
        if (mBatteryReceiver == null) {
            return;
        }
        mContext.unregisterReceiver(mBatteryReceiver);
        mBatteryReceiver = null;
    }
}
