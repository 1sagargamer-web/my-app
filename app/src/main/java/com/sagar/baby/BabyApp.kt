package com.sagar.baby

import android.app.Application

class BabyApp : Application() {
    lateinit var speaker: Speaker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        speaker = Speaker(this)
    }

    companion object {
        lateinit var instance: BabyApp
            private set
    }
}
