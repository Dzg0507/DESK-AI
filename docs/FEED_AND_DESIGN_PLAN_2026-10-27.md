# A Feed screen, and growing up the app's look (plan for review, 2026-10-27)

Devin asked for a "News Feed" screen of his own stuff that he can customize by asking the agent or by typing in an
editable box, something like Meta's Muse, built well rather than run-of-the-mill. He also said the aesthetics feel
"elementary" next to how advanced the agent is. Nothing has been changed yet; this is for review first.

---

## 1. What the others are doing (research)

| Product | What the feed is | Worth taking | Worth avoiding |
|---|---|---|---|
| **Meta Muse** (Sept 2026) | Part of a personal-agent app with a main chat, side chats, a **feed**, goals, an activity log, an approvals queue and an "upcoming" section. The feed shows updates from connected Facebook/Instagram and web stories, "depending on what they want the feed to aggregate", plus proactive suggestions and ideas. | The feed sits **next to** the chat as an equal; it is the agent's proactive side made visible. An **activity log** and **approvals queue** live beside it. | TIME reports it "building a dossier" on its 4M users, including people who don't use it. Ours stays on the Mini. |
| **ChatGPT Pulse** (Sept 2025) | 5-10 visual cards a day, researched in the background from chat history, memory and (optional) Gmail/Calendar. Cards expand or turn into a follow-up chat. A **"Curate"** box and 👍/👎 per card steer future editions. | **Finite by design:** the day's cards expire unless saved, and it ends with "that's it for today" (a deliberate guard against endless scrolling). Plain-language curation. Card → chat. | It's one fixed time a day. Devin has no regular schedule, so ours follows when he's around (as the brief already does). |
| **Bluesky Attie** (Mar 2026) | You **describe the feed you want in plain language** ("folklore and traditional music", "long-form fiction from people I follow") and the assistant builds it. Runs on Claude. | Natural language as the main control, compiled into a real, inspectable feed definition. | Black-box ranking. We should show the compiled rules back so he can see and edit them. |
| **Meta Vibes** | Endless AI-generated video, tuned by engagement. | Nothing. | This is the "AI slop" feed Devin doesn't want. |

**The lesson:** the feeds that work are **finite, explainable, steerable in plain words, and one tap from a
conversation**. The ones people hate are endless and opaque.

---

## 2. Why DeskAI can do this better than a generic feed

The agent **already makes the content**. Today it either competes for one of a few chat messages or never gets seen:

- `proactive_candidates`: signals, open loops, curiosity, interests, patterns, research. Each has a short subject, a
  `why` line, an urgency (`now`/`today`/`whenever`) and an expiry. Most candidates are **held** because only a few
  may interrupt him in chat. Those held items are good feed cards.
- Interest research findings (links, judged for novelty), YouTube channel feeds (`core/feeds.py`), TikTok posts and
  their times, Pinterest numbers, the gallery's new images, finished background work, watches, open loops that
  settled ("the court granted the IFP motion"), the brief (weather, calendar, important mail).

**So the feed is mostly a new home for existing work, not a new generator.** That matters for cost: it adds almost
no model calls, and it takes pressure off chat. Only `now` items need to interrupt him; `today` and `whenever` can wait
quietly in the feed. That also helps the "too many background calls / proactive messages" problem from earlier today.

---

## 3. The Feed, as proposed

### 3.1 Shape: an *edition*, not an endless scroll

- A new edition is put together when Devin comes back after being away (≥ 6 h, the same presence signal the brief
  uses) and there is something new. It is never on a clock.
- About 6-12 cards, ranked, then a clear **"You're caught up"** end with the time of the next edition. Older editions
  stay one swipe back. Saved cards stay forever.
- A small live strip at the top: things that land mid-edition (an urgent watch, a finished task) without reshuffling
  the cards below.

### 3.2 Card types (each with its own layout, not one generic box)

| Card | Looks like | From |
|---|---|---|
| **Edition header** | Large date, weather glyph and temperature, "3 things today" line | brief |
| **Story** | Big image (the page's own preview image), headline, 2-line summary, source and "why you're seeing this" | interest/research findings |
| **Video** | 16:9 thumbnail, channel, length; plays in the app or YouTube | YouTube follows |
| **Your channel** | TikTok/Pinterest post thumbnails with numbers and a tiny trend line | TikTok/Pinterest stats |
| **Settled** | "You were waiting on X → here's what happened", with source | open loops |
| **Question** | The agent's curious question with an inline reply field (answer without leaving the feed) | curiosity |
| **Made for you** | Gallery image(s) full-bleed | ImageGen, TikTok renders |
| **Done while you were away** | Compact list of finished background tasks, tap for details | tasks/activity |

Every card has the same three actions: **Ask about this** (opens chat with the card attached as context; this is the
only time feed content enters the chat prompt, so chat tokens stay low), **More like this / Less like this**, and
**Save**. A long-press shows **"Why this"** (the candidate's `why` plus which recipe rule matched).

### 3.3 Customizing it: the "Feed recipe"

The top of the feed (and a settings sheet) has a **plain-text box**, Attie-style:

> More Arkansas outdoors and trail-cam stuff, my legal case updates, new Drake Anthony videos. No crypto. Keep it
> short: 8 cards max. Show my TikTok numbers once a day.

When he saves it, **one** model call compiles it into structured rules, shown right under the box as editable chips:

- **Topics** with weights (outdoors ↑, legal ↑, crypto ✕)
- **Follows** (YouTube channels, sites, people)
- **Sources on/off** (TikTok stats daily, Pinterest weekly, gallery on)
- **Size** (8 cards)

He can also do it from chat ("add Ozark fishing reports to my feed", "stop showing Pinterest"). That extends the
existing `proactive_settings` tool, which already does follow/unfollow for YouTube. Taps, "less like this" and
ignored cards feed the reply-rate and threshold machinery that `candidates.py` already has.

The compiled rules are plain data, so they're **inspectable and reversible**. Nothing is a hidden profile.

### 3.4 Agent side (AlwaysOnAgent)

- `feed_items` table: id, edition, card_type, title, body, image_ref, link, source, why, created_at, state
  (new/seen/saved/dismissed/expired), and candidate_id when it came from the pool.
- An **edition builder** in code (not a model). It takes held candidates and new findings that match the recipe, then
  the existing ranker, a variety rule (no more than 2 cards from one source in a row), and the size cap.
- Link preview images: the page's `og:image`, fetched once and cached on the Mini, served like `/api/files/m` (token in
  the header, never in the URL).
- Routes: `GET /api/feed?edition=`, `POST /api/feed/{id}/react`, `GET/PUT /api/feed/recipe`, plus an SSE `feed` event
  for the live strip. They get documented in API.md (the contract test checks that).
- Free tier only: the recipe compile is one call per edit, and ranking reuses the existing call.

---

## 4. The aesthetics problem, honestly

Devin's instinct is right, and it's measurable. From the app's code today:

| | Now | Why it reads as "elementary" |
|---|---|---|
| Type | A type scale exists in `Type.kt` but is used **once**; **371** hard-coded font sizes in **17** different values | No hierarchy: headings, labels and body all drift. This is the single biggest "amateur" signal |
| Fonts | System default only | No voice. Every Android app looks like this |
| Shapes | **12** different corner radii (1.5-30 dp) | Things don't look like they belong together |
| Color | `Color.kt` holds ~70 colors: indigo, violet, purple, pink, orange, teal, amber… under names that no longer match ("ElectricCyan" is green) | Too many accents means no accent. The green "Circuit" identity is diluted |
| Structure | One chat screen and **44** bottom sheets and dialogs | Everything is a pop-up; nothing feels like a place |
| Process | I build the APK but **never see a screen** | Code gets tested, visuals never do. That's how the look fell behind the agent |

### 4.1 Direction (keep the identity, refine it)

Keep what's his: the black, the terminal green and the deep cerulean. Use them like a premium product would,
**as signal, not wallpaper**:

- **Surfaces:** near-black with two quiet elevation steps. The circuit texture only on the chat background and
  headers, not under every card.
- **Accent:** green means "alive / yours / good", cerulean means "the agent / links", amber means waiting, rose means
  error. Everything else in `Color.kt` goes.
- **Type:** two families, used through tokens only:
  - *Option A, "Instrument":* **Space Grotesk** (display) + **Inter** (text) + **JetBrains Mono** (status, code).
    Technical, sharp, close to today's personality.
  - *Option B, "Editorial":* **Newsreader** or **Fraunces** serif for feed headlines + **Inter** for everything else.
    The feed reads like a magazine, the chat stays technical. That contrast is what makes the feed feel special.
- **Shape:** three radii only (8 small / 16 cards / 28 sheets).
- **Spacing:** 4-dp grid tokens.
- **Motion:**
  - Cards animate in, staggered, when an edition opens.
  - Tapping a card's image grows it into the detail view (Compose shared elements).
  - A small "caught up" moment at the end.
  - Story cards take a subtle tint from their own image's dominant color (androidx Palette), so the feed isn't a wall
    of identical boxes.
- **Navigation:** two real places, **Chat** and **Feed**, side by side (swipe or a slim top switch), so the feed isn't
  sheet #45. The busiest sheets (tasks, gallery, memory) can move into proper screens later.

### 4.2 Make the look checkable (the process fix)

- **Screenshot tests** (Roborazzi on the Robolectric setup the app already has): every build renders the key screens
  to PNGs. I can open those images and review them, and a change that breaks the look shows up as an image diff.
  Visuals get the same treatment as code.
- `@Preview`s for each card type with realistic data.
- Lint rule or test: no raw `fontSize = ` / `RoundedCornerShape(n.dp)` outside the theme files, so it doesn't drift
  again.
- **Upgrade Compose BOM** 2024.09 → current 2025/26 BOM. That brings Material 3 Expressive (motion scheme, shape
  morphing) and stable shared-element APIs. Moderate risk; it gets a release of its own.

---

## 5. Order of work (each step ships on its own)

1. **Foundation (no visible feature):** BOM upgrade, fonts, type/shape/spacing tokens, palette cleanup, screenshot
   tests. Then sweep the 371 font sizes onto the scale. The app should look noticeably more finished after this alone.
2. **Desk v1:** the agent's `feed_items`, edition builder and routes; the app's Desk screen with the Edition header and
   Story, Video, Settled, Done and Question cards; Ask-about-this and Send-to-agent; the caught-up end. (Question is in
   v1: it's only text plus a reply field, and the curious questions leave chat along with the brief.)
3. **Recipe:** the text box, compiled chips, chat control through `proactive_settings`, and reactions feeding the
   ranker.
4. **Polish:** shared-element card → detail, Palette tints, Your-channel cards with trend lines, the live strip.
   Maybe later a home-screen widget showing the edition header.

## 6. Decisions for Devin

1. **Type direction:** A "Instrument" or B "Editorial" (I lean B for the feed with A's mono for status). I can make a
   visual mockup of both before any code.
2. **Name:** "Feed", "Today", "Edition", "The Desk"…?
3. **Does the daily brief move into the feed** (as the edition header) instead of arriving as a chat message?
   Recommended: yes, with only urgent things still pushed to chat.
4. **Sources at launch:** all of section 3.2, or start with Stories + Videos + Settled + Done?
5. **OK to do step 1 (foundation) first**, before the feed itself?

---

## 7. After Argon's review (2026-10-27): what changes

Taken as given:

- **Palette tints cut.** Wild `og:image` colors give mud and contrast failures on dark. Cards stay on the surface
  tokens. At most a faint glow behind the image (≤ 12% opacity, color clamped to low lightness and chroma).
- **Scoped sweep in step 1.**
  - Add the tokens (`AppTypography`, `AppShapes`, `AppColors`) and the lint rule, enforced on new or touched files.
  - Migrate the chat screen, top bar and the five most-used sheets.
  - Build the Desk on clean tokens only.
  - The other legacy sheets migrate when they're next touched. No blind 371-site sweep.
- **No root swipe.** A segmented pill at the top switches Chat ⇄ Desk. Horizontal swipe is used only inside the Desk,
  for older editions, so it never fights reply-swipes or code blocks.
- **Text-first Story card** when `og:image` is missing, broken or under 400 px wide: bigger headline, favicon and
  source pill, 3-line excerpt. It's a designed state, not a broken image.
- **Hairline borders, not shadows.** Cards are dark with a 1 dp white border at ~7% (~12% on the top edge).
- **Green as signal only.** Bright terminal green is kept for status dots, sparklines and active toggles. Structure
  uses a muted deep emerald.
- **Tabular figures** (`tnum`) on Inter and JetBrains Mono wherever numbers appear (stats, temperatures, times).
- **Collapsible recipe bar:** one row of rule chips plus "✎ Edit recipe", which expands into the text box.
- **"Send to agent"** on cards: fire off background work (summarize the video, add to case notes, draft a reply)
  without leaving the Desk; the result lands in the live strip. Same rules as today: anything public or outward-facing
  still waits for Devin's tap, and nothing runs on the PC's screen unattended.
- **"▶ Play edition"** in the header: the phone's own text-to-speech reads the weather, today's things and the top
  card summaries. It's free and on-device. Planned for step 4.
- **Launch with the core 5:** Edition header, Story, Video, Settled, Done. Your-channel sparklines and full-bleed
  gallery cards wait for polish.
- **The daily brief becomes the edition header**, and stops arriving as a chat message. Only `now` items interrupt
  chat.

Adjusted:

- **Serif:** Newsreader rather than Instrument Serif. Instrument Serif comes in one weight only, and Argon's own spec
  asks for medium. So: Newsreader for Desk headlines (tracking −0.02em), Inter for body and chat, JetBrains Mono for
  metadata, timestamps, "why" tags and chips.
- **Surface color:** Argon's neutral #060709 / #0D0F12 would drop the green-black that is the "Circuit" identity
  Devin picked on 10-11. Keep the same lightness steps with a faint green cast (e.g. #050806 / #0B100D). That's a
  small call for Devin.
- **Haptics: only three moments.** A light tick on the Chat ⇄ Desk switch, a tick when a chip snaps, and a double tick
  at "You're caught up". Haptics on everything stop meaning anything.

Second round (Argon), for the build:

- **Mint-tinted hairline, one modifier.** The border is pale mint (#E2F8E6), not white, so it doesn't look chalky on
  green-black. It's about 8% on the sides and 14% at the top, all in one reusable `Modifier.deskCardSurface()`
  (`Modifier.border(1.dp, brush, AppShapes.Card)` with a vertical gradient).
  - Adjustment: the highlight fades over a fixed ~24 dp, not 35% of the card's height. With a fraction, a tall card
    would get a long grey wash down its sides.
  - So the brush is built in `drawWithCache` from the measured size. It's still one line at the call site.
- **Question card is in step 2** (section 5 updated).
- **Fonts bundled in `res/font/`, never downloadable.** Downloadable fonts fall back to Roboto offline and in
  Robolectric/Roborazzi.
  - Adjustment: use **static** TTFs for the weights we use, not variable fonts. The app supports Android 7
    (minSdk 24), and variable-font weights only work from Android 8 (API 26). Static files render the same
    everywhere, including the screenshot tests.
  - The weights: Inter 400/500/600, Newsreader 500 (+ 600 for the edition date), JetBrains Mono 400/500.
  - Roughly 1 MB in total.

**Decided (Devin, 2026-10-27): the screen is called "Today"**, and step 1 is a go. Where this plan says Feed or Desk,
read Today.

Was open for Devin: the name (**Desk** is the front-runner, since the app is DeskAI and "Feed" sounds like social
media) and the go-ahead for step 1.

---

## 8. Visual target: spatial glass (Devin, 2026-10-27)

Mockups: [design/target_chat.png](design/target_chat.png) and [design/target_today.png](design/target_today.png). They
were drawn with "Desk"; the screen is **Today**. Ignore the stray "status JetBrains Mono" label in the second one.

**Built now, for chat (v2.30.0):**
- The chat background is near-black with a soft emerald/cerulean glow from the top (`AtmosphereBackground`). The
  circuit texture is gone.
- The agent's replies have no box. They read straight off the background, with the name and time above them. The
  owner's messages are smoked glass (`Modifier.glass`: white 8%, 1 dp white-15% edge).
- The full-width status banner became a floating frosted pill: beacon, "AlwaysOnAgent · 1 running" and what it's
  doing.
- Tasks, Memory and Videos (also in the hub menu) sit behind one collapsible toggle beside the pill. Devin: "some kind
  of collapsable icon, that hides them".
- Video cards are full-bleed: the thumbnail fills a 24 dp card (`DeskShapes.media`), with a scrim over the bottom 60%
  (transparent → #F2050806), a frosted play button, and the name and size on a glass pill.
- The input bar's buttons are frosted circles.

**For step 2 (Today), not built yet (Devin: "Dont go to step 2"):**
- The edition header (date, weather) sits unboxed on the canvas over the top glow, not in a card.
- Story and Video cards are 100% full-bleed (24 dp, Crop). The scrim covers the bottom 60%, and the Newsreader
  headline, metadata and frosted buttons (white 14% + 1 dp #26FFFFFF) sit inside the bottom of the image.
- Compact cards (Settled, Done, Stats) form a 2-column bento row (`spacedBy(12.dp)`): square smoked-glass tiles, each
  with one action pill at the bottom.
- The Chat | Today toggle is a frosted segmented pill at the top, with the live agent pill next to it.

---

Sources: [TIME on Muse](https://time.com/article/2026/10/06/meta-muse-ai-agent-privacy/) ·
[Social Media Today: Meta launches Muse](https://www.socialmediatoday.com/news/meta-launches-muse-personal-ai/829984/) ·
[CNBC: Muse Spark agents](https://www.cnbc.com/2026/09/08/meta-personal-ai-agents-public-reckoning-privacy-safety.html) ·
[MindStudio: Muse explained](https://www.mindstudio.ai/blog/meta-muse-ai-agent) ·
[eWeek: ChatGPT Pulse](https://www.eweek.com/news/openai-chatgpt-pulse/) ·
[The Neuron: Pulse](https://www.theneuron.ai/explainer-articles/chatgpt-pulse-makes-ai-your-personal-research-assistant-while-you-sleep) ·
[WebProNews: Bluesky Attie](https://www.webpronews.com/bluesky-bets-on-ai-to-solve-social-medias-oldest-problem-finding-what-you-actually-want-to-see/) ·
[Android Developers: Androidify, delightful UI with Compose](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html) ·
[TechCrunch: Meta Vibes](https://techcrunch.com/2025/09/25/meta-launches-vibes-a-short-form-video-feed-of-ai-slop)
