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
