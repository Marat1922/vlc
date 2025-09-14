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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import androidx.core.net.toUri
import androidx.lifecycle.Observer
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
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate
import org.videolan.vlc.gui.helpers.hf.getDocumentFiles
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.FileUtils
import java.io.File

open class UsbFileProvider(
    context: Context,
    dataset: LiveDataset<MediaLibraryItem>,
    url: String?, private val filePicker: Boolean = false,
    private val showDummyCategory: Boolean = true, sort:Int, desc:Boolean) : BrowserProvider(context, dataset,
    url, sort, desc) {

    private val directoryRepository = DirectoryRepository.getInstance(context)
    private val storageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_MEDIA_MOUNTED -> {
                    val path = intent.data?.path
                    if (path != null && !path.contains(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) {
                        MediaParsingService.scanStorageImmediately(context, path)
                        if (url == null || url == "root") {
                            launch {
                                updateUsbDevicesList()
                            }
                        }
                    }
                }
                Intent.ACTION_MEDIA_UNMOUNTED,
                Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_REMOVED -> {
                    val path = intent.data?.path
                    if (path != null && !path.contains(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) {
                        handleUsbDeviceRemoved(context, path)
                    }
                }
            }
        }
    }

    init {
        fetch()
    }

    private lateinit var storageObserver : Observer<Boolean>

    override suspend fun browseRootImpl() {
        loading.postValue(false)
        try {
            updateUsbDevicesList()
            registerStorageReceiver()
        } catch (e: Exception) {
            dataset.value = mutableListOf()
        } finally {
            loading.postValue(false)
        }
    }

    private suspend fun updateUsbDevicesList() {
        val storages = directoryRepository.getMediaDirectories()
        val devices = mutableListOf<MediaLibraryItem>()

        // Добавляем заголовок только если нужно
        if (!filePicker && showDummyCategory) {
            val browserStorage = context.getString(R.string.browser_storages)
            devices.add(DummyItem(browserStorage))
        }

        var hasUsbDevices = false
        for (mediaDirLocation in storages) {
            val file = File(mediaDirLocation)
            if (!file.exists() || !file.canRead()) continue


            if (AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY == mediaDirLocation) continue

            hasUsbDevices = true

            val directory = MLServiceLocator.getAbstractMediaWrapper(AndroidUtil.PathToUri(mediaDirLocation))
            directory.type = MediaWrapper.TYPE_DIR

            val deviceName = FileUtils.getStorageTag(directory.title)
            if (deviceName != null) directory.setDisplayTitle(deviceName)
            directory.addStateFlags(MediaLibraryItem.FLAG_STORAGE)

            devices.add(directory)
        }


        if (AndroidUtil.isMarshMallowOrLater && !hasUsbDevices && !filePicker) {
            if (!this::storageObserver.isInitialized) {
                storageObserver = Observer { if (it == true) launch { browseRootImpl() } }
                StoragePermissionsDelegate.storageAccessGranted.observeForever(storageObserver)
            }
        } else {
            // Удаляем наблюдатель если разрешения есть
            if (this::storageObserver.isInitialized) {
                StoragePermissionsDelegate.storageAccessGranted.removeObserver(storageObserver)
            }
        }


        dataset.value = if (hasUsbDevices) devices else mutableListOf()
        loading.postValue(false)

    }

    private fun handleUsbDeviceRemoved(context: Context, path: String) {
        if (url == null || url == "root") {
            launch {
                updateUsbDevicesList()
            }
        }
    }

    private fun registerStorageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        try {
            context.registerReceiver(storageReceiver, filter)
        } catch (e: Exception) {
        }
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
            url == "otg://" -> launch {
                loading.postValue(true)
                dataset.value = withContext(coroutineContextProvider.IO) {
                    @Suppress("UNCHECKED_CAST")
                    getDocumentFiles(context, "otg") as? MutableList<MediaLibraryItem> ?: mutableListOf()
                }
                loading.postValue(false)
            }
            url?.startsWith("content:") == true -> launch {
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
            url == "otg://" -> {
                val result = ArrayList<MediaWrapper>()
                val files = withContext(coroutineContextProvider.IO) {
                    @Suppress("UNCHECKED_CAST")
                    getDocumentFiles(context, "otg") as? MutableList<MediaLibraryItem> ?: mutableListOf()
                }.map { it as MediaWrapper }

                result.addAll(files.filter { it.itemType == MediaWrapper.TYPE_MEDIA })
                files.filter { it.itemType == MediaWrapper.TYPE_DIR }.forEach {
                    result.addAll(browseByUrl(it.uri.toString()))
                }
                result.toList()
            }

            url.startsWith("content:") -> {
                val result = ArrayList<MediaWrapper>()
                val files = withContext(coroutineContextProvider.IO) {
                    @Suppress("UNCHECKED_CAST")
                    getDocumentFiles(context, url.toUri().path?.substringAfterLast(':') ?: "") as? MutableList<MediaLibraryItem> ?: mutableListOf()
                }.map { it as MediaWrapper }

                result.addAll(files.filter { it.itemType == MediaWrapper.TYPE_MEDIA })
                files.filter { it.itemType == MediaWrapper.TYPE_DIR }.forEach {
                    result.addAll(browseByUrl(it.uri.toString()))
                }
                result.toList()
            }

            else -> super.browseUrl(url).toList().map { it as MediaWrapper }
        }
    }

    override fun release() {
        try {
            context.unregisterReceiver(storageReceiver)
        } catch (e: Exception) {
        }

        if (this::storageObserver.isInitialized) {
            StoragePermissionsDelegate.storageAccessGranted.removeObserver(storageObserver)
        }

        super.release()
    }
}