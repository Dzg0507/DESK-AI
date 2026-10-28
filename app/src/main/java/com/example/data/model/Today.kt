package com.example.data.model

/** Today (2026-10-28): an edition of cards the agent put together (GET /api/today). */
data class TodayPage(
    val edition: TodayEdition?,
    val items: List<TodayItem>,
    val older: List<TodayEditionRef> = emptyList()
)

data class TodayEdition(
    val id: Long,
    val builtAt: String,
    val date: String,               // yyyy-MM-dd
    val count: Int,
    val weather: TodayWeather?,
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
