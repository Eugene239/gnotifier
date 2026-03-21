# GNotifier

HTTP endpoint that accepts a notification body and forwards it to a Telegram channel using a bot (JSON, plain text, or form field `message`). Built with [Ktor](https://ktor.io/), packaged as a Docker image, published to GitHub Container Registry on every push to `master`.

Stack: **Kotlin 2.3.20**, **Ktor 3.4.1** (Gradle plugin pins server/client deps), **Gradle 9.4.1**, JVM **21**, Docker runtime **eclipse-temurin:21-jre-noble**.

## API

`GET /health`

- No authentication. **HTTP 200** with **JSON** (suitable for **Coolify** health checks — e.g. `https://<host>/health`).
- Response: `{"status":"ok","uptimeMs":12345,"version":"2025032014-a1b2c3d"}` — `uptimeMs` is JVM uptime in milliseconds; `version` is **build timestamp (UTC `YYYYMMddHH`)** + **short Git SHA** (from the build; local `./gradlew` uses the current repo when `.git` is present).

`POST /notify`

- Header: `Authorization: Bearer <NOTIFY_BEARER_TOKEN>` (required).
- Body (pick one **Content-Type**):
  - **`application/json`**: `{"message":"Hello"}`. Keys and string values must use **double quotes** in JSON. Unknown keys are ignored.
  - **`text/plain`**: the **entire body** is the message (UTF-8).
  - **`application/x-www-form-urlencoded`**: form field **`message`** (e.g. `message=Hello`).
- Max **8192** characters for the message; empty message returns `400`.
- Success: `204 No Content`.
- Errors: `401` (bad/missing token), `400` (invalid JSON, missing `message` in form, empty message, or Telegram rejected content), `413` (body too large), `415` (unsupported content type), `502` (Telegram unreachable or error).

Telegram messages are capped at **4096** characters (longer input is truncated).

### Example request (shape)

**Server (URL):** `https://<your-host>/notify` — use your public URL or `http://<ip>:<port>/notify`.

**Headers:**

```http
Authorization: Bearer <NOTIFY_BEARER_TOKEN>
Content-Type: application/json; charset=utf-8
```

Replace `<NOTIFY_BEARER_TOKEN>` with the value from env (never commit it).

**Body (JSON):**

```json
{"message":"Your notification text"}
```

**Windows cmd** (escape inner quotes in `-d`):

```cmd
curl.exe -sS -X POST "https://<your-host>/notify" -H "Authorization: Bearer <NOTIFY_BEARER_TOKEN>" -H "Content-Type: application/json" -d "{\"message\":\"Your notification text\"}"
```

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `TELEGRAM_BOT_TOKEN` | yes | Bot token from [@BotFather](https://t.me/BotFather) |
| `TELEGRAM_CHAT_ID` | yes | Channel or chat ID (channels often look like `-100xxxxxxxxxx`) |
| `NOTIFY_BEARER_TOKEN` | yes | Secret for `Authorization: Bearer …` |
| `PORT` | no | HTTP port (default `8080`) |

The bot must be able to post in the channel (e.g. add the bot as an **administrator** of the channel).

Logs go to **stdout** (Logback). Telegram failures and exceptions are logged with a short API response snippet where applicable.

On each process start, the service sends **`Gnotifier Started`** to the configured Telegram chat (same path as `/notify`). If that send fails, a warning is written to the logs.

## Local run

```bash
export TELEGRAM_BOT_TOKEN="..."
export TELEGRAM_CHAT_ID="..."
export NOTIFY_BEARER_TOKEN="..."
./gradlew run
```

Example:

JSON:

```bash
curl -sS -X POST "http://127.0.0.1:8080/notify" \
  -H "Authorization: Bearer $NOTIFY_BEARER_TOKEN" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{"message":"Hello from GNotifier"}'
```

Plain text (no JSON):

```bash
curl -sS -X POST "http://127.0.0.1:8080/notify" \
  -H "Authorization: Bearer $NOTIFY_BEARER_TOKEN" \
  -H "Content-Type: text/plain; charset=utf-8" \
  --data-binary "Hello from GNotifier"
```

## Docker

Build a fat JAR:

```bash
./gradlew buildFatJar
```

Image build (from repo root):

```bash
docker build -t gnotifier:local .
docker run --rm -p 8080:8080 \
  -e TELEGRAM_BOT_TOKEN=... \
  -e TELEGRAM_CHAT_ID=... \
  -e NOTIFY_BEARER_TOKEN=... \
  gnotifier:local
```

## GitHub Container Registry

On push to `master`, [.github/workflows/publish.yml](.github/workflows/publish.yml) builds and pushes:

- `ghcr.io/<owner>/<repo>:master`
- `ghcr.io/<owner>/<repo>:sha-<short>`

Enable **Packages** for the repo if needed. For a **private** image, create a GitHub PAT with `read:packages` and use it in Coolify as the registry password (username = GitHub username).

Optional GitHub **Actions** secrets (after a successful image push):

| Secret | Purpose |
|--------|---------|
| **`DEPLOY_WEBHOOK`** | Full deploy webhook URL (e.g. `https://<coolify>/api/v1/deploy?uuid=…&force=false`). If set, the workflow runs **`GET`** on this URL. |
| **`DEPLOY_WEBHOOK_TOKEN`** | **Bearer token** when the webhook is marked *auth required* (e.g. Coolify). If empty, the request is sent **without** `Authorization`. |

For **Coolify** with “Deploy Webhook (auth required)”: enable **API access** in Coolify (**Settings → Configuration → Advanced → API Access**), create an **API token** with **Deploy** permission (**Keys & Tokens → API Tokens**), put the webhook URL in **`DEPLOY_WEBHOOK`** and the token in **`DEPLOY_WEBHOOK_TOKEN`**. Official flow: [GitHub Actions | Coolify](https://coolify.io/docs/applications/ci-cd/github/actions).

## Coolify

1. New resource: **Docker Image**.
2. Image: `ghcr.io/<your-github-user>/<repo>:master` (lowercase).
3. Registry: `https://ghcr.io`, user + PAT with `read:packages`.
4. Set the four environment variables above (and `PORT` if the platform assigns one).
5. Enable **watch / redeploy on new image** if your Coolify version supports it for private registries.

Point a domain at the app if you expose it through Coolify’s reverse proxy.
