/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.components.ServiceManager
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

        // dfs
        fun depthLookup(stacks: ArrayDeque<List<ModuleInfo>>): Pair<List<ModuleInfo>?, SdkInfo?> {
            while (stacks.isNotEmpty()) {
                val stack = stacks.poll()

                val item = stack.last()
                if (cache.containsKey(item)) {
                    cache[item]?.let { return stack to it } ?: continue
                }

                cache[item] = null
                val dependencies = run {
                    if (item is LibraryInfo) {
                        val (libraries, sdks) = libraryDependenciesCache.getLibrariesAndSdksUsedWith(item)
                        sdks.firstOrNull()?.let {
                            return stack to it
                        }
                        libraries
                    } else {
                        item.dependencies()
                            .also { dependencies ->
                                dependencies.firstIsInstanceOrNull<SdkInfo>()?.let {
                                    return stack to it
                                }
                            }
                    }
                }

                dependencies.forEach { dependency ->
                    cache[dependency]?.let {
                        (stack + dependency) to it
                    }
                    if (!cache.containsKey(dependency)) {
                        stacks.add(stack + dependency)
                    }
                }
            }
            return null to null
        }

        val (stack, sdkInfo) =
            depthLookup(ArrayDeque<List<ModuleInfo>>().also {
                it.add(listOf(moduleInfo))
            })
        sdkInfo?.let { stack?.forEach { cache[it] = sdkInfo } }
        return sdkInfo
    }
}