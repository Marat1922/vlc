/*****************************************************************************
 * UsbBrowserModel.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.viewmodels.browser

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import org.videolan.tools.CoroutineContextProvider
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.providers.UsbFileProvider

class UsbBrowserModel(
    context: Context,
    url: String?,
    coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()
) : BrowserModel(
    context,
    url,
    TYPE_USB,
    false,
    PickerType.SUBTITLE, // Добавляем обязательный параметр
    null, // mocked
    coroutineContextProvider // coroutineContextProvider
) {

    override val provider: UsbFileProvider = UsbFileProvider(context, dataset, url, showDummyCategory = false, sort = sort, desc = desc)

    class Factory(val context: Context, val url: String?) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return UsbBrowserModel(context.applicationContext, url) as T
        }
    }
}