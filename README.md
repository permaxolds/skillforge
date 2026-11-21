# SkillForge — Android app + AI backend (phase 1)

> **Status: source code only. It has NOT been compiled or run on a device.**
> The environment this was written in has no Android SDK and no access to Google/Maven repositories, so no APK exists.
> Only the backend was executed (against a mock AI API). Expect to fix a few compile errors on first build.

## What is implemented
| Area | State |
|---|---|
| Room DB (goals, completions, chat, memory) | written, untested |
| Recurrence (daily / weekly / monthly / custom weekdays / one-time) | written, untested |
| Goals: create, edit, delete, complete, move (reschedule) | written, untested |
| Home: greeting, today %, week %, month %, streak, 7-day chart, today/weekly/monthly lists, upcoming reminders | written, untested |
| Real notifications: AlarmManager exact alarms, reboot/timezone/time-change restore, WorkManager 12-hourly safety net, Done + Snooze actions, tap opens goal, POST_NOTIFICATIONS + exact-alarm handling | written, untested |
| AI chat screen + backend; AI reads real goal data; proposals need your confirmation; chat stored locally | app untested; backend tested with mock AI |
| Chat memory: AI saves short non-sensitive facts; view/edit/delete/clear in Settings | written, untested |
| Settings: permissions, test notification, backend URL/token, memory, clear chat | written, untested |

## NOT built yet (next phases)
Calendar screen · Skills screen (levels/topics/hours) · Analytics screen (only the Home 7-day chart exists) · Focus/Pomodoro timer · custom & digest notifications (morning/evening/weekly/monthly) · AI "generate plan" button · data export/backup · theme selection · cloud sync/auth. Weekly goals are due on Monday and monthly goals on the 1st.

## 1. Run the backend
Needs Node 18+. The AI key goes ONLY here, as an environment variable (never in the app):
```
cd backend && npm install
export ANTHROPIC_API_KEY=sk-ant-...      # from console.anthropic.com
export APP_TOKEN=pick-a-long-random-string   # the app sends this; protects your key from strangers
# optional: export ANTHROPIC_MODEL=claude-sonnet-5-5
npm start
```
For a phone you need a public HTTPS URL: deploy the `backend` folder to Render / Railway / Fly.io / Cloud Run and set the same two variables in their dashboard ("Environment"). Start command: `npm start`.
Emulator-only testing: use `http://10.0.2.2:8787` (the debug build allows http).

## 2. Build the app
1. Install Android Studio (Koala or newer) with JDK 17; open the `SkillForge` folder; let Gradle sync (it downloads Gradle 8.7 from `gradle-wrapper.properties`).
2. Phone: enable Developer options → USB debugging, plug in, press ▶ Run. 
3. APK: Build → Build Bundle(s)/APK(s) → Build APK(s); file is in `app/build/outputs/apk/debug/app-debug.apk`. Copy to the phone and install (allow "unknown sources").
4. In the app: Settings → enter Backend URL + App token → Allow notifications (Android 13+) → Allow exact alarms (Android 12+) → "Send test notification".

## Android limits you should know
- Android 13+: notifications need the runtime permission, requested at launch and in Settings.
- Android 12+: exact alarms need "Alarms & reminders" access (SCHEDULE_EXACT_ALARM). Without it reminders still fire but can be several minutes late.
- Force-stopping the app cancels its alarms until you open it again. Some phones (Xiaomi, Oppo, Vivo, Samsung "sleeping apps") kill background apps — set SkillForge to "Unrestricted" battery.
- Reminder time is local; alarms are rebuilt after reboot, timezone or clock change.

## Test checklist (not yet performed)
Create goal with reminder 2 min ahead → lock phone → notification arrives → tap opens goal → Done / Snooze work → reboot, confirm next reminder still fires → airplane mode: create/complete goals works → chat: "Create a daily goal to practice C++ for 30 minutes at 7 PM" → Confirm → goal appears in Goals → "Show my pending weekly goals" matches the Goals screen.
