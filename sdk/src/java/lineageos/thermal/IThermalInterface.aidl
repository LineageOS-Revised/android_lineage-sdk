/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.thermal;

/** @hide */
interface IThermalInterface {
    boolean isSupported();
    int getCpuTemperature();
    int getCpuLimit();
    boolean setCpuLimit(int millidegC);
    boolean clearCpuLimit();
    int getBatteryTemperature();
    int getBatteryLimit();
    boolean setBatteryLimit(int millidegC);
    boolean clearBatteryLimit();
}
