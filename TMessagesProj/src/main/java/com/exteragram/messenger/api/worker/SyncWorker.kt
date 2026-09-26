package com.exteragram.messenger.api.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.exteragram.messenger.api.ApiController

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return if (ApiController.performSync()) Result.success() else Result.retry()
    }
}
