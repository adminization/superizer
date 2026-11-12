package cx.m42.superizer.host.files

import cx.m42.superizer.app.AppId

/** A page has no folder the person can open, so an app on the web gets no `AppFolder`. */
internal actual fun platformFileIo(): FileIo? = null

internal actual fun defaultFolderRoot(hostName: String): String? = null

internal actual fun describeFolder(path: String, hostName: String, appId: AppId, isDefault: Boolean): String = path

internal actual suspend fun <T> onIo(block: () -> T): T = block()
