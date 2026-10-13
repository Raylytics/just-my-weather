package io.raylytics.justmyweather

/** Which app is running this shared code. The one place a platform name is spelled. */
enum class Platform { ANDROID, IOS }

/** The platform this build of the shared code was compiled for. */
expect val currentPlatform: Platform
