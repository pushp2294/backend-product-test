package com.example.myproducts

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application class. @HiltAndroidApp generates the app-wide dependency container. */
@HiltAndroidApp
class CatalogueApp : Application()
