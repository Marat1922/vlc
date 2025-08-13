package org.videolan.vlc.viewmodels.browser

import android.content.Context
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.CoroutineContextProvider

class UsbBrowserModel(
    context: Context,
    url: String? = null,
    mocked: ArrayList<MediaLibraryItem>? = null,
    coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()
) : BrowserModel(
    context,
    url,
    TYPE_NETWORK,
    false,
    mocked = mocked,
    coroutineContextProvider = coroutineContextProvider
) {
}