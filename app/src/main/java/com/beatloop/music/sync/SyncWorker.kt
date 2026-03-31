package com.beatloop.music.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return syncManager.mergeLocalAndRemote().fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount >= 3) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        )
    }
}
