/*****************************************************************************
 * FileBrowserProvider.kt
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

package org.videolan.vlc.providers

import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.R
import org.videolan.vlc.VlcMigrationHelper
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate
import org.videolan.vlc.gui.helpers.hf.getDocumentFiles
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.FileUtils
import java.io.File

open class FileBrowserProvider(
        context: Context,
        dataset: LiveDataset<MediaLibraryItem>,
        url: String?, private val filePicker: Boolean = false,
        private val showDummyCategory: Boolean = true, sort:Int, desc:Boolean) : BrowserProvider(context, dataset,
        url, sort, desc), Observer<MutableList<UsbDevice>> {

    private var storagePosition = -1
    private var otgPosition = -1

    init {
        fetch()
    }

    private lateinit var storageObserver : Observer<Boolean>

    override suspend fun browseRootImpl() {
        loading.postValue(true)

        // 1. Только внутренняя память
        val internalMemoryPath = AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY
        val file = File(internalMemoryPath)

        // 2. Проверка доступа
        if (!file.exists() || !file.canRead()) {
            loading.postValue(false)
            dataset.value = mutableListOf()
            return
        }

        // 3. Создаем обёртку для папки
        val directory = MLServiceLocator.getAbstractMediaWrapper(AndroidUtil.PathToUri(internalMemoryPath)).apply {
            type = MediaWrapper.TYPE_DIR
            setDisplayTitle(context.getString(R.string.internal_memory))
        }

        // 4. Получаем содержимое папки через существующий механизм
        val contents = withContext(Dispatchers.IO) {
            val items = mutableListOf<MediaLibraryItem>()
            file.listFiles()?.forEach { file ->
                val uri = when {
                    file.isDirectory -> AndroidUtil.PathToUri(file.absolutePath)
                    file.isFile -> AndroidUtil.PathToUri(file.absolutePath)
                    else -> null
                }

                uri?.let {
                    items.add(MLServiceLocator.getAbstractMediaWrapper(it).apply {
                        type = if (file.isDirectory) {
                            MediaWrapper.TYPE_DIR
                        } else {
                            val mw = MLServiceLocator.getAbstractMediaWrapper(it)
                            mw.type
                        }
                    })
                }
            }
            items
        }

        // 5. Обновляем LiveData
        dataset.postValue(contents)
        loading.postValue(false)
    }

    override suspend fun requestBrowsing(url: String?, eventListener: MediaBrowser.EventListener, interact : Boolean) = withContext(coroutineContextProvider.IO) {
        initBrowser()
        mediabrowser?.let {
            it.changeEventListener(eventListener)
            if (url != null) it.browse(url.toUri(), getFlags(interact))
        }
    }

    override fun browse(url: String?) {
        when {
            url == "otg://" || url?.startsWith("content:") == true -> launch {
                loading.postValue(true)
                dataset.value = withContext(coroutineContextProvider.IO) {
                    @Suppress("UNCHECKED_CAST")
                    getDocumentFiles(context, url.toUri().path?.substringAfterLast(':') ?: "") as? MutableList<MediaLibraryItem> ?: mutableListOf()
                }
                loading.postValue(false)
            }
            url == "root" -> launch { browseRootImpl() }
            else -> super.browse(url)
        }
    }

    suspend fun browseByUrl(url: String): List<MediaWrapper> {
        return when {
            url == "otg://" || url.startsWith("content:") -> {
                withContext(coroutineContextProvider.IO) {
                    val files = getDocumentFiles(context, url.toUri().path?.substringAfterLast(':') ?: "")
                            as? MutableList<MediaLibraryItem> ?: mutableListOf()


                    files.filterIsInstance<MediaWrapper>().toList()
                }
            }
            else -> super.browseUrl(url).toList().map { it as MediaWrapper }
        }
    }

    override fun release() {
        if (url == null) {
            ExternalMonitor.devices.removeObserver(this)
            if (this::storageObserver.isInitialized) {
                StoragePermissionsDelegate.storageAccessGranted.removeObserver(storageObserver)
            }
        }
        super.release()
    }

    override fun onChanged(list: MutableList<UsbDevice>) {
        if (list.isNullOrEmpty()) {
            if (otgPosition != -1) {
                dataset.remove(otgPosition)
                otgPosition = -1
            }
        } else if (otgPosition == -1) {
            val otg = MLServiceLocator.getAbstractMediaWrapper("otg://".toUri()).apply {
                title = context.getString(R.string.otg_device_title)
                type = MediaWrapper.TYPE_DIR
            }
            otgPosition = storagePosition+1
            dataset.add(otgPosition, otg)
        }
    }
}