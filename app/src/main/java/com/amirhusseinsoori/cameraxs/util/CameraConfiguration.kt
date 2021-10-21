/*
 * Copyright (c) 2021 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.multipicker.camera.util

import androidx.viewbinding.BuildConfig


public const val LIB = BuildConfig.LIBRARY_PACKAGE_NAME

object CameraConfiguration {

    /** Result key for the captured image URI */
    const val IMAGE_URI = "$LIB.IMAGE_URI"

    /** User configuration key for viewfinder overlay resource ID */
    const val VIEW_FINDER_OVERLAY = "$LIB.VIEW_FINDER_OVERLAY"

    /** User configuration key for default camera lens facing */
    const val CAMERA_LENS_FACING = "$LIB.CAMERA_LENS_FACING"

    /** User configuration key for camera switching behavior */
    const val FULL_SCREEN_ENABLED = "$LIB.FULL_SCREEN_ENABLED"

    /** User configuration key for camera switching behavior */
    const val CAMERA_SWITCH_DISABLED = "$LIB.CAMERA_SWITCH_DISABLED"
}
