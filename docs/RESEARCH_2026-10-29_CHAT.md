# Research for the chat redesign (2026-10-29)

Quick research before redesigning DeskAI's Chat screen. Devin's choices so far: one continuous conversation (the
app's sessions were phone-only; the agent always had one conversation), no "new chat" or "fresh start", no avatar
on every message, media you can tap (videos play in place, images and documents open), and more spacing. A persona
changer is on the agent's Ideas board as a brainstorm.

Most sources are 2026 blogs and vendor pages, not official docs; treat them as signals, not facts.

## What other apps do, and what it means for DeskAI

**1. Answers as cards, not walls of text ("generative UI").** Guides now describe the best assistants replying with
a card that has buttons (a flight comparison with "Book") instead of paragraphs. ([UXPin](https://www.uxpin.com/studio/blog/chat-user-interface-design/), [Lazarev](https://www.lazarev.agency/articles/ai-chatbot-ui-design))
- *For DeskAI:* the agent already sends task proposals, schedules, videos, pins, weather. Each kind can be its
  own card in the chat (a video card that plays, a pin card with Approve, a task card with live progress) instead
  of text plus a link. The mockup's video card is this pattern.

**2. Long things open beside or over the chat ("artifacts").** Drafts and code open in a pane so you can keep
talking about them without losing the thread. ([UXPin](https://www.uxpin.com/studio/blog/chat-user-interface-design/))
- *For DeskAI:* Canvas already does this for drafts and website previews. The redesign should make "open in
  Canvas" one tap from any long reply.

**3. Four things a good assistant interface shows.** What it can do, how to recover when it gets something wrong,
how sure it is, and accessibility. ([Fuselab](https://fuselabcreative.com/chatbot-interface-design-guide/))
- *For DeskAI:* "what it can do" fits the input's placeholder ("Talk to AlwaysOnAgent, or type / for commands") and
  the hub. "Recovery" fits a quick "that's wrong" / undo on cards (the agent already corrects memories).

**4. Personas are separate setups you switch between.** Gemini's Gems are saved setups with standing instructions,
picked from a list; each conversation starts with one. ChatGPT has Custom GPTs and modes (standard vs deeper
reasoning), not a mid-chat personality switch. No source showed a good way to switch personas *within one
conversation*. ([Gems guide](https://innerforge.ai/blog/gemini-gems-personality-setup-guide), [PopSci](https://popsci.com/diy/how-to-create-custom-ai-chatbots-google-gemini), [Gigazine](https://gigazine.net/gsc_news/en/20240515-gemini-ai-gems))
- *For DeskAI:* that's an opening. Because DeskAI is one conversation with one memory, a persona would be a
  "lens" over the same assistant (tone, which tools and memories come first, a tint), switchable any time without
  losing anything. Nobody seems to do this well yet.

**5. Memory you can see, and where it came from.** ChatGPT's 2026 memory recalls across a year of chats and links
each remembered thing back to the conversation it came from. Rankings judge memory by whether you can see, edit and
delete it, and say where it came from. ([Sovereign](https://www.sovereignmagazine.com/article/chatgpt-s-memory-upgrade-transforms-ai-assistants-into-personal-knowledge-systems), [Littlebird](https://littlebird.ai/blog/best-ai-assistants-with-memory), [Vellum](https://www.vellum.ai/md/blog/best-personal-ai-assistants-with-memory))
- *For DeskAI:* the agent stores where each fact came from. A "remembered from Oct 12" link that jumps to that
  spot in the one continuous chat is a natural fit, and the Ideas board already has "show memory saves in chat".

**6. Remembering too much can feel like being misread.** 2026 studies report that the more an assistant
generalises about you, the more it can get you wrong; the advice is to show the *inferred* picture of you, not just
stored facts, so it can be corrected. ([36Kr](https://eu.36kr.com/en/p/3893359765830531), [Contrary](https://research.contrary.com/report/privacy-and-identity-in-the-age-of-ai-memory), [arXiv 2409.11192](https://arxiv.org/abs/2409.11192v1))
- *For DeskAI:* the agent builds a profile of Devin. Letting him see and fix that profile ("thinks you prefer short
  answers") matters more than a list of facts.

## Not found

No source documented UI patterns for a single never-ending conversation, or detailed input-bar guidance. DeskAI's
choices there are our own (the mockup, plus testing on the phone).

# Part 2: the agent's personality, and a watcher that studies Devin (2026-10-29)

## Where the agent is today

- **Personality:** one fixed persona (`ASSISTANT_PERSONA` in AlwaysOnAgent's `core/memory.py`): honesty rules first
  ("only say what's true"), then how to work. No tone or character beyond that.
- **What it knows about Devin:** facts from conversations, plus a short "core profile" rebuilt from them. That
  profile is deliberately strict: "Do not infer traits, motives, priorities or preferences". So today the agent
  knows *what* about Devin, never *how he is*. A watcher would add exactly that missing layer.

## What the research says

**1. Guessing someone's personality from chat is only moderately accurate.** LLMs inferring Big Five traits from
chat histories: about r = .44 when the chatbot steers the conversation to find out, but only r = .12 from ordinary
chats; some traits (extraversion, neuroticism) come out better than others, some (agreeableness) poorly. Mostly
2025-26 preprints. ([arXiv 2405.13052](https://arxiv.org/pdf/2405.13052), [arXiv 2604.19785](https://arxiv.org/abs/2604.19785), [TechXplore on the ETH study](https://techxplore.com/news/2026-05-ai-personality-traits-chatgpt-history.html), [arXiv 2501.07532](https://arxiv.org/pdf/2501.07532), [arXiv 2507.14355](https://arxiv.org/pdf/2507.14355))
- *So:* a watcher that labels Devin "high openness, low agreeableness" would be shaky and not very useful.

**2. People are bad at saying what they prefer; behaviour shows it better.** Users mispredict their own
preferences for AI help; learning from what they actually do works better, but only if it's shown to them, or
trust erodes. ([arXiv 2601.04461](https://arxiv.org/pdf/2601.04461), [IRIS, arXiv 2607.26473](https://arxiv.org/html/2607.26473), [PrefIx](https://www.alphaxiv.org/abs/2602.06714))
- *So:* watch behaviour, not personality: which Today cards he saves or skips, which suggestions he takes, when he
  opens the app, how long the replies he likes are, what he corrects.

**3. What it learns must be visible and editable ("scrutable").** The research consensus for personal agents: the
user can inspect and change the agent's model of them, and the data stays under their control.
([arXiv 2503.06729](https://arxiv.org/pdf/2503.06729), [ACM, privacy-aligned agents](https://dl.acm.org/doi/10.1145/3733816.3760752), [OpenAI Model Spec note](https://behaviorlayer.ai/signal/openai-model-spec-2026-08-18))

**4. Studying the user can make an assistant a yes-man.** A 2025 study found user memory profiles raised
agreement sycophancy the most of anything tested; persona "agreeableness" strongly predicts sycophancy; and models
drift from their set personality over long conversations, especially emotional or self-reflective ones.
([Taskade summary](https://www.taskade.com/blog/ai-sycophancy-explained), [ACL 2026](https://aclanthology.org/2026.acl-long.1421.pdf), [Assistant Axis, arXiv 2601.10387](https://arxiv.org/html/2601.10387v1), [Springer 2026](https://link.springer.com/article/10.1007/s00146-026-02993-z))
- *So:* the agent's personality needs a fixed spine (its honesty rules) that no persona or learned preference can
  bend: "he likes short answers" may change style, never what's true.

## What this suggests for DeskAI (ideas, not decided)

- **A watcher that writes observations, not a personality score.** In the background (no AI call per message;
  cheap, like the away shift), it notices patterns and writes them as hypotheses with evidence: "You skip AI-news
  cards (skipped 9 of 11 this week)", "You reply most between 9 and 11 pm", "You cut long answers short".
- **"How I think you work":** a section in the app beside the facts profile, each observation with a ✓ / ✗.
  Only confirmed ones shape how the agent behaves; rejected ones are never guessed again.
- **Personas as tone over a fixed spine:** if the persona changer happens, a persona changes tone and focus, and the
  honesty rules stay first in every one.
- ~~**The agent's own character could come from the watcher:** its tone settles from what Devin responds to.~~
  **Rejected (Devin, 2026-10-29):** "if the agent's persona were allowed to change dynamically it would lead it to
  being much more of a yes man". He's right: a personality tuned on what he responds to is trained on his approval,
  the same mechanism that made models sycophantic (point 4). The agent's character stays fixed; anything the
  watcher learns may only change *practical* things (format, timing, what to show), never its opinions or
  willingness to disagree. Needs deeper research before anything is built (see below).

## Open questions for the deep research

1. How do you give an assistant a stable character that can't drift into a yes-man over months of one
   conversation, with memory of the owner in every prompt? (Prompt design, checks, measuring drift.)
2. How can it learn the owner's practical preferences without that leaking into agreeing with him? Where's the
   line between "adapts to me" and "tells me what I want to hear"?
3. How do you test for sycophancy in a personal assistant (evals that fit AlwaysOnAgent's existing eval setup)?
4. Do personas or modes help or hurt here? Is a fixed set of owner-chosen personas safer than any adaptive one?
5. What do the best long-term assistants and companions (Pi, Replika, ChatGPT memory, Gemini) get wrong, by their
   users' own accounts?
6. AI talking to AI drifts into agreement and repetition ("attractor states"; Devin: "they tend to always just
   drift towards agreeing with each other and then wind up saying the same things over and over"). What keeps
   AlwaysOnAgent's own AI-checks-AI loops honest (the memory second opinion, Claude and the DeskAI developer AI
   via ALIGNMENT_DIALOG.md, Claude/Gemini handovers)? Different models, grounding in evidence, measured
   disagreement? Start with [arXiv 2606.30571](https://arxiv.org/pdf/2606.30571).
   Devin's correction: AI-with-AI drift he can already control, because a fresh session resets it at once. The
   only place with no reset is his one continuous conversation with the agent. There each reply is already built
   fresh (fixed persona, profile, memories, summaries, only the last few exchanges verbatim), so drift can't snowball
   through a long log; the risk is that it **leaks into what carries over**: summaries ("we agreed X"), saved
   memories and the profile, the agent's own words fed back every reply. Research: how to keep those neutral, and
   whether a **fresh-context second opinion** (no profile or summaries, just the question and facts) on advice and
   decisions catches drift, showing Devin both answers when they differ.
