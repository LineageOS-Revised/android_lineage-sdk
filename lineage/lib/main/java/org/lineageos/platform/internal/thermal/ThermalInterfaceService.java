/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.thermal;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;

import lineageos.app.LineageContextConstants;
import lineageos.thermal.IThermalInterface;

import org.lineageos.platform.internal.LineageSystemService;

public class ThermalInterfaceService extends LineageSystemService {
    private static final String TAG = "LineageThermal";

    private ThermalController mController;
    private boolean mPublished;

    public ThermalInterfaceService(Context context) {
        super(context);
    }

    @Override
    public String getFeatureDeclaration() {
        return "org.lineageos.thermal";
    }

    @Override
    public boolean isCoreService() {
        return false;
    }

    @Override
    public void onStart() {
        try {
            mController = new ThermalController(getContext());
            publishIfSupported();
        } catch (Throwable e) {
            Log.e(TAG, "Failed to start thermal service", e);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase != PHASE_BOOT_COMPLETED || mController == null) {
            return;
        }
        try {
            publishIfSupported();
            mController.applyPersisted();
        } catch (Throwable e) {
            Log.e(TAG, "Failed to apply thermal limits", e);
        }
    }

    private void publishIfSupported() {
        if (mPublished || !mController.isSupported()) {
            return;
        }
        publishBinderService(LineageContextConstants.LINEAGE_THERMAL_INTERFACE, mService);
        mPublished = true;
    }

    private final IBinder mService = new IThermalInterface.Stub() {
        @Override
        public boolean isSupported() {
            return mController != null && mController.isSupported();
        }

        @Override
        public int getCpuTemperature() {
            return mController != null ? mController.getCpuTemperature() : 0;
        }

        @Override
        public int getCpuLimit() {
            return mController != null ? mController.getCpuLimit() : 0;
        }

        @Override
        public boolean setCpuLimit(int millidegC) {
            return mController != null && mController.setCpuLimit(millidegC);
        }

        @Override
        public boolean clearCpuLimit() {
            return mController != null && mController.setCpuLimit(0);
        }

        @Override
        public int getBatteryTemperature() {
            return mController != null ? mController.getBatteryTemperature() : 0;
        }

        @Override
        public int getBatteryLimit() {
            return mController != null ? mController.getBatteryLimit() : 0;
        }

        @Override
        public boolean setBatteryLimit(int millidegC) {
            return mController != null && mController.setBatteryLimit(millidegC);
        }

        @Override
        public boolean clearBatteryLimit() {
            return mController != null && mController.setBatteryLimit(0);
        }
    };
}
