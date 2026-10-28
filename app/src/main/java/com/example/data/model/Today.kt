package com.example.data.model

/** Today (2026-10-28): an edition of cards the agent put together (GET /api/today). */
data class TodayPage(
    val header: TodayHeader,
    val edition: TodayEdition?,
    val items: List<TodayItem>,
    val older: List<TodayEditionRef> = emptyList(),
    val recipe: TodayRecipe = TodayRecipe()
)

/** Read when Today is opened, not when the edition was built: the date, the weather now, and the day's brief. */
data class TodayHeader(
    val date: String,               // yyyy-MM-dd
    val weather: TodayWeather?,
    val brief: String?,             // the day's brief (it comes here, not to the chat)
    val location: String
)

data class TodayEdition(
    val id: Long,
    val builtAt: String,
    val count: Int,
    val nextAfter: String?          // when the next edition can be put together
)

data class TodayWeather(val tempF: Int, val summary: String, val place: String, val isDay: Boolean)

data class TodayEditionRef(val id: Long, val builtAt: String)

data class TodayItem(
    val id: Long,
    val card: String,               // story | video | settled | done | question
    val title: String,
    val body: String,
    val link: String,
    val site: String,
    val why: String,
    val state: String,              // new | seen | saved | less | more | dismissed | answered
    val imageUrl: String?           // absolute, on the agent: load it with the token header
) {
    val saved: Boolean get() = state == "saved"
}

/** Devin's own words for Today, and the rules the agent compiled from them (shown as chips). */
data class TodayRecipe(
    val text: String = "",
    val more: List<String> = emptyList(),
    val less: List<String> = emptyList(),
    val never: List<String> = emptyList(),
    val channels: List<String> = emptyList(),
    val maxCards: Int? = null,
    val hidden: List<String> = emptyList(),     // card kinds turned off: story, video, settled, done, question
    val location: String = ""
) {
    val isEmpty: Boolean get() = text.isBlank()
}
