/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.ArrayDeque

/**
 * Maintains and caches mapping ModuleInfo -> SdkInfo *form its dependencies*
 * (note that this SDK might be different from Project SDK)
 *
 * Cache is needed because one and the same library might (and usually does)
 * participate as dependency in several modules, so if someone queries a lot
 * of modules for their SDKs (ex.: determine built-ins for each module in a
 * project), we end up inspecting one and the same dependency multiple times.
 *
 * With projects with abnormally high amount of dependencies this might lead
 * to performance issues.
 */
interface SdkInfoCache {
    fun findOrGetCachedSdk(moduleInfo: ModuleInfo): SdkInfo?

    companion object {
        fun getInstance(project: Project): SdkInfoCache =
            ServiceManager.getService(project, SdkInfoCache::class.java)
    }
}

class SdkInfoCacheImpl(private val project: Project) : SdkInfoCache {
    private val cache: MutableMap<ModuleInfo, SdkInfo?>
        // Note: it's ok to use thread-unsafe map, because 'doFindSdk' is pure
        get() = project.cacheInvalidatingOnRootModifications { mutableMapOf() }

    override fun findOrGetCachedSdk(moduleInfo: ModuleInfo): SdkInfo? {
        if (!cache.containsKey(moduleInfo)) {
            cache[moduleInfo] = doFindSdk(moduleInfo)
        }

        return cache[moduleInfo]
    }

    private fun doFindSdk(moduleInfo: ModuleInfo): SdkInfo? {
        moduleInfo.safeAs<SdkInfo>()?.let { return it }

        val libraryDependenciesCache = LibraryDependenciesCache.getInstance(this.project)

        val checkedLibraryInfo = mutableSetOf<ModuleInfo>()
        val stack = ArrayDeque<ModuleInfo>()

        stack += moduleInfo

        // bfs
        while (stack.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val poll = stack.poll()
            if (!checkedLibraryInfo.add(poll)) continue

            stack += run {
                if (poll is LibraryInfo) {
                    val (libraries, sdks) = libraryDependenciesCache.getLibrariesAndSdksUsedWith(poll)
                    sdks.firstOrNull()?.let { return it }
                    libraries
                } else {
                    poll.dependencies()
                        .also { dependencies -> dependencies.firstIsInstanceOrNull<SdkInfo>()?.let { return it } }
                }
            }

        }

        return null
    }
}