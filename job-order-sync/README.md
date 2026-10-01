# Job Orders — sync through one common Gmail and Google Drive

With sync on, every phone and computer running **Job Orders** shares the same customers, items
and orders. The data lives in a Google Drive folder owned by **one common Gmail account**
(for example `orders.alwaha@gmail.com`). Staff do not need to sign in to Google; each device
only needs the **sync link** and the **team PIN**.

Cost: free (Google Apps Script and Drive in a normal Gmail account).

## One-time setup (owner of the common Gmail, about 10 minutes, on a computer)

1. Sign in to the common Gmail in Chrome.
2. Open **https://script.google.com** → **New project**.
3. Click on *Untitled project* at the top and rename it **Job Orders Sync**.
4. Delete everything in the editor, then paste the whole content of [`Code.gs`](Code.gs).
5. On the line `const TEAM_PIN = "CHANGE-ME";` replace `CHANGE-ME` with your own PIN, for
   example `const TEAM_PIN = "7391";`. Use something staff can type but outsiders cannot guess.
6. Click **Save** (disk icon).
7. In the function list at the top choose **setup** and click **Run**.
   Google asks for permission: **Review permissions** → choose the common Gmail →
   *Google hasn't verified this app* → **Advanced** → **Go to Job Orders Sync (unsafe)** →
   **Allow**. (It is your own script; this warning is normal.)
   The log shows *Ready*. A folder **Job Orders Data** now exists in that Drive.
8. Click **Deploy** → **New deployment** → gear icon → **Web app**:
   - Description: `Job Orders sync`
   - Execute as: **Me**
   - Who has access: **Anyone**
   - Click **Deploy** and copy the **Web app URL** (starts with `https://script.google.com/macros/s/`).
9. Send the URL and the PIN to your staff (WhatsApp is fine).

## On every device

1. Open **Job Orders** → **Settings**.
2. **This device → Order number series**: give each device its own series, for example
   `JO-A` (owner's phone), `JO-B` (shop phone), `JO-C` (office PC). Tap **Save device settings**.
3. **Sync between devices**: paste the sync link and the team PIN → **Connect**.
   - The first device connected with real data uploads it.
   - A device that still shows sample data drops the samples and downloads the shared data.
4. The top-right corner shows **Synced 10:42**. Tap it any time to sync now.

The app syncs when it opens, a few seconds after every change, every minute while it is open,
and when the internet comes back. It keeps working offline and sends changes later.

## What is in the Drive folder

```
Job Orders Data/
├── data.json              all records (the shared copy)
└── backups/
    ├── 2026-10-01.json    one snapshot per day, the last 90 days are kept
    └── …
```

Each daily file can be restored in the app: download it from Drive, then
**Settings → Backup → Restore from file**.
You can share the **Job Orders Data** folder with staff Gmail accounts as *Viewer* so they can see
the backups; editing happens only through the app.

## Good to know

- If two people change the **same order** at nearly the same time, the later change wins.
  Different orders never conflict.
- Deleting a record on one device deletes it on all devices.
- To change the PIN: edit `TEAM_PIN`, **Save**, then **Deploy → Manage deployments → Edit (pencil)
  → Version: New version → Deploy**. The link stays the same; enter the new PIN on each device.
- After any change to `Code.gs`, always deploy a **new version** as above, or the old code keeps
  running.
- Google limits free accounts to about 20,000 script calls a day. Ten devices syncing every
  minute for 10 hours use about 6,000.
