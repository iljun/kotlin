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
        // get an operate on the fixed instance of a cache to avoid case of roots modification in the middle of lookup
        val instance = cache
        // synchronized is needed as well for `doFindSdk` that does multiple writes during lookup
        synchronized(instance) {
            if (!instance.containsKey(moduleInfo)) {
                instance[moduleInfo] = doFindSdk(instance, moduleInfo)
            }

            return instance[moduleInfo]
        }
    }

    private fun doFindSdk(cache: MutableMap<ModuleInfo, SdkInfo?>, moduleInfo: ModuleInfo): SdkInfo? {
        moduleInfo.safeAs<SdkInfo>()?.let { return it }

        val libraryDependenciesCache = LibraryDependenciesCache.getInstance(this.project)

        // return value (graph, sdk) is used to mark entire graph could be resolved to sdk (when sdk is not a null)
        fun depthLookup(graphs: ArrayDeque<List<ModuleInfo>>): Pair<List<ModuleInfo>?, SdkInfo?> {
            // graphs is a stack of graphs is used to implement DFS without recursion
            // it depends on a number of libs, that could be > 10k for a huge monorepos
            while (graphs.isNotEmpty()) {
                ProgressManager.checkCanceled()
                // graph of DFS from the root i.e from `moduleInfo`
                val graph = graphs.poll()

                val last = graph.last()
                if (cache.containsKey(last)) {
                    // the result could be immediately returned when cache already has it
                    cache[last]?.let { return graph to it }
                    // or it is known that the item graph is a dead end and no reason to process it again
                        ?: continue
                }

                // don't know result yet, have to mark as a dead end to avoid repetition
                // note: entire graph will be marked as resolved when anything deeper will be found
                cache[last] = null
                val dependencies = run {
                    if (last is LibraryInfo) {
                        // use a special case for LibraryInfo to reuse values from a library dependencies cache
                        val (libraries, sdks) = libraryDependenciesCache.getLibrariesAndSdksUsedWith(last)
                        sdks.firstOrNull()?.let {
                            return graph to it
                        }
                        libraries
                    } else {
                        last.dependencies()
                            .also { dependencies ->
                                dependencies.firstIsInstanceOrNull<SdkInfo>()?.let {
                                    return graph to it
                                }
                            }
                    }
                }

                dependencies.forEach { dependency ->
                    // sdk is found when some dependency is already resolved
                    cache[dependency]?.let {
                        return (graph + dependency) to it
                    }
                    // otherwise add a new graph of (existed graph + dependency) as candidates for DFS lookup
                    if (!cache.containsKey(dependency)) {
                        graphs.add(graph + dependency)
                    }
                }
            }
            return null to null
        }

        val (stack, sdkInfo) =
            depthLookup(ArrayDeque<List<ModuleInfo>>().also {
                // initial graph item
                it.add(listOf(moduleInfo))
            })
        // when sdk is found: mark all graph elements could be resolved to the same sdk
        sdkInfo?.let { stack?.forEach { cache[it] = sdkInfo } }
        return sdkInfo
    }
}