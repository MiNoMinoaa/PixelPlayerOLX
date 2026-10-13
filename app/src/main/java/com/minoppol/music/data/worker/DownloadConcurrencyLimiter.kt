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
    private val queue = ArrayDeque<Waiter>()
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
            val waiter = Waiter(cont)
            val registration = scope.launch {
                val pending = mutableListOf<CancellableContinuation<Unit>>()
                mutex.withLock {
                    if (queue.isEmpty() && activeCount < permits && cont.isActive) {
                        activeCount++
                        waiter.granted = true
                        pending.add(cont)
                    } else if (cont.isActive) {
                        queue.addLast(waiter)
                    }
                }
                pending.forEach { it.resume(Unit) {} }
            }
            cont.invokeOnCancellation {
                registration.cancel()
                scope.launch {
                    val pending = mutableListOf<CancellableContinuation<Unit>>()
                    mutex.withLock {
                        if (queue.remove(waiter)) {
                            return@withLock
                        }
                        if (waiter.granted) {
                            activeCount = (activeCount - 1).coerceAtLeast(0)
                            grantNext(pending)
                        }
                    }
                    pending.forEach { it.resume(Unit) {} }
                }
            }
        }
    }

    suspend fun release() {
        val pending = mutableListOf<CancellableContinuation<Unit>>()
        mutex.withLock {
            activeCount = (activeCount - 1).coerceAtLeast(0)
            grantNext(pending)
        }
        pending.forEach { it.resume(Unit) {} }
    }

    private suspend fun updatePermits(newPermits: Int) {
        val pending = mutableListOf<CancellableContinuation<Unit>>()
        mutex.withLock {
            permits = newPermits
            grantNext(pending)
        }
        pending.forEach { it.resume(Unit) {} }
    }

    private fun grantNext(pending: MutableList<CancellableContinuation<Unit>>) {
        while (activeCount < permits && queue.isNotEmpty()) {
            val waiter = queue.removeFirst()
            if (!waiter.cont.isActive) continue
            activeCount++
            waiter.granted = true
            pending.add(waiter.cont)
        }
    }

    private class Waiter(val cont: CancellableContinuation<Unit>) {
        @Volatile
        var granted = false
    }
}
