/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.os;

import android.view.KeyEvent;

/**
 * Interface for intercepting and handling hardware keys at the framework level.
 */
public interface DeviceKeyHandler {
    KeyEvent handleKeyEvent(KeyEvent event);
}
