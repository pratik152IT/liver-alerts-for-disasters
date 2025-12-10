

 # 🌍 Live Alerts for Disasters  
A real-time global disaster alert system that fetches live events from **NASA EONET** and **USGS Earthquake API**, stores them locally, and serves them through a REST-based web server.  
Includes automated background polling, email notifications, and Docker deployment support.

---

## 🚀 Features

### 🔄 Real-Time Data Fetching
- Fetches disasters from:
  - **NASA EONET API** (fires, storms, floods, volcanic activity, etc.)
  - **USGS Earthquake API**
- Automatically updates every **60 seconds** using a scheduled background job.

### 🗄️ Local Database Storage
- Uses **SQLite** to store all events.
- Supports upsert logic to avoid duplicate entries.
- Persists data even across restarts (when not in ephemeral containers).

### 🌐 REST API Server (Spark/Jetty Based)
Provides endpoints to:
- View all events
- Filter events
- Get recent events
- Send email alerts to subscribers

### 📧 Email Notification System
- Sends disaster alerts via email for high-severity events.
- Configurable using environment variables (SMTP).

### 🐳 Docker + Render Deployment
- Fully containerized with Docker.
- Runs reliably on Render with dynamic port assignment.

---

## 📦 Tech Stack

| Component | Technology |
|----------|------------|
| Language | Java |
| Framework | Spark Java (Jetty) |
| Database | SQLite |
| Build Tool | Maven |
| Deployment | Docker, Render |
| Scheduler | Java Executors API |

---

