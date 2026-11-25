# Notes for Claude

- The user writes in Korean; reply in Korean.
- Dialogue (GTK game): every spoken line lives in `sinhwa-online/game_pub/lines.json`, keyed by situation; the user writes the lines.
  Whenever a new feature needs a spoken line, add the situation to the waiting list in `sinhwa-online/DIALOGUE.md` (and wire it with `speak(F, '<key>')`).
  When the user asks what lines are needed, answer from `DIALOGUE.md`.
