package top.stellortus.stellar_music_server.util.extensions

fun Boolean.ifTrue(behavior: () -> Boolean): Boolean {
    if (this) {
        behavior()
    }
    return this
}


fun Boolean.ifFalse(behavior: () -> Boolean): Boolean {
    if (!this) {
        behavior()
    }
    return this
}
