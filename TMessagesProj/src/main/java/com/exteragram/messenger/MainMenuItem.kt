package com.exteragram.messenger

enum class MainMenuItem(val id: Int) {
    DIVIDER(-1),
    PROFILE(18),
    ARCHIVE(14),
    BOTS(105),
    NEW_GROUP(2),
    CONTACTS(6),
    NEW_CHANNEL(3),
    CALLS(10),
    SAVED(11),
    SETTINGS(8),
    PLUGINS(102),
    BROWSER(101),
    QR(17),
    FEED(106);

    companion object {
        @JvmStatic
        fun getById(id: Int): MainMenuItem? = entries.firstOrNull { it.id == id }
    }
}
