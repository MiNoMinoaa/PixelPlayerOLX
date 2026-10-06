package com.minoppol.music.data.worker

import com.minoppol.music.data.preferences.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DownloadConcurrencyLimiter @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
) {
    @Volatile
    private var permits: Int = UserPreferencesRepository.DEFAULT_DOWNLOAD_MAX_THREADS
    private var activeCount = 0
    private val queue = ArrayDeque<CancellableContinuation<Unit>>()
    private val mutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            userPreferencesRepository.downloadMaxThreadsFlow.collect { value ->
                updatePermits(value.coerceIn(1, 5))
            }
        }
    }

    suspend fun acquire() {
        mutex.withLock {
            if (queue.isEmpty() && activeCount < permits) {
                activeCount++
                return
            }
        }
        suspendCancellableCoroutine { cont ->
            scope.launch {
                val granted = mutex.withLock {
                    if (activeCount < permits) {
                        activeCount++
                        true
                    } else {
                        queue.addLast(cont)
                        false
                    }
                }
                if (granted) cont.resume(Unit) {}
            }
            cont.invokeOnCancellation {
                scope.launch {
                    mutex.withLock { queue.remove(cont) }
                }
            }
        }
    }

    suspend fun release() {
        val next = mutex.withLock {
            activeCount = (activeCount - 1).coerceAtLeast(0)
            if (activeCount < permits && queue.isNotEmpty()) {
                activeCount++
                queue.removeFirst()
            } else {
                null
            }
        }
        next?.resume(Unit) {}
    }

    private suspend fun updatePermits(newPermits: Int) {
        val toResume = mutex.withLock {
            permits = newPermits
            buildList {
                while (activeCount < permits && queue.isNotEmpty()) {
                    activeCount++
                    add(queue.removeFirst())
                }
            }
        }
        toResume.forEach { it.resume(Unit) {} }
    }
}
