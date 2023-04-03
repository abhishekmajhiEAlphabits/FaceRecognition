package com.example.facerecognition

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

fun provideDispatcher(nThreads: Int = 2) =
    Executors.newFixedThreadPool(nThreads).asCoroutineDispatcher()