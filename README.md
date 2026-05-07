# ⚡ ZapLink

> A full-stack URL shortening service with analytics and QR code support.
> Built with Spring Boot, React, and MySQL.

---

## 📑 Table of Contents

- [About](#-about)
- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Prerequisites](#-prerequisites)
- [Installation & Setup](#-installation--setup)
- [Environment Variables](#-environment-variables)
- [Database Schema](#-database-schema)
- [API Endpoints](#-api-endpoints)
- [Folder Structure](#-folder-structure)
- [Usage](#-usage)
- [Screenshots](#-screenshots)
- [Deployment](#-deployment)
- [Roadmap](#-roadmap)

---

## 📖 About

ZapLink is a full-stack URL shortening service that lets users convert long, unwieldy URLs into short, shareable links. Beyond simple shortening, ZapLink provides click analytics, QR code generation, and link expiration controls, giving users full visibility and control over their shared links.

The application also exposes a public REST API, making it easy for developers to integrate URL shortening into their own tools and workflows. Built with Spring Boot, React, and MySQL, ZapLink is designed to be lightweight, fast, and free for everyone.

This project was built as a learning and portfolio piece to explore full-stack development with Spring Boot, React, and MySQL — while creating a genuinely useful tool.

---

## ✨ Features

### 🔗 Core
- Shorten long URLs into short, shareable links
- Redirect short URLs to their original destination
- Set expiration dates for links (auto-invalidate after expiry)

### 👤 User Accounts
- User registration and login (authentication)
- Personal dashboard to view and manage your links
- Delete your own short links anytime

### 📊 Analytics
- Track total click count per short link
- View click timestamps and visualize activity over time with graphs

### 🛡️ Security & Validation
- Validate URL format before shortening
- Block malicious and phishing URLs using a blacklist / safe-browsing check
- Rate limiting on shortening requests to prevent abuse and spam

### 🚀 Extras
- Generate a QR code for any short link
- Public REST API for programmatic URL shortening

### 🛠️ Admin
- Admin dashboard to view all users and all links
- Disable or remove abusive / reported links
- User management — ban or delete users
- System-wide analytics and reports (total links, clicks, active users, trends)

---

## 🛠️ Tech Stack

**Frontend**
- React.js
- Bootstrap (styling)
- Axios (HTTP client)
- React Router (client-side routing)

**Backend**
- Java 17+
- Spring Boot
- Spring Web (REST APIs)
- Spring Security (authentication & authorization)
- Spring Data JPA (ORM)
- Maven (build tool)

**Database**
- MySQL 8+

**Authentication**
- JWT (JSON Web Tokens) for stateless authentication

**Other Tools & Libraries**
- ZXing — QR code generation
- Bucket4j — in-memory rate limiting
- Google Safe Browsing API — malicious / phishing URL detection
- Lombok — reduce boilerplate (getters, setters, constructors)
- Chart.js or Recharts — analytics graphs on the frontend

---

## 🏗️ Architecture

ZapLink follows a classic 3-tier architecture:

```
┌─────────────┐      HTTP/REST       ┌──────────────┐      JDBC      ┌─────────┐
│   React     │  ───────────────►    │ Spring Boot  │  ───────────►  │  MySQL  │
│  Frontend   │  ◄───────────────    │   Backend    │  ◄───────────  │   DB    │
└─────────────┘      JSON            └──────────────┘                └─────────┘
```

- **Frontend (React)** — UI for shortening URLs, dashboard, analytics, QR code display
- **Backend (Spring Boot)** — REST APIs, auth, business logic, rate limiting, URL validation, QR generation
- **Database (MySQL)** — stores users, links, click events

### 🔄 Example Flows

#### 1. Shortening a URL
1. User submits a long URL via the React frontend
2. Request is sent to Spring Boot REST API with JWT in the header
3. Backend validates the URL format and checks against the safe-browsing blacklist
4. Bucket4j enforces rate limiting per user
5. A unique short code is generated (Base62 of an auto-increment ID)
6. The link is saved in MySQL and the short URL is returned to the frontend

#### 2. Redirecting a Short URL
1. User visits `https://zaplink.com/{shortCode}`
2. Backend looks up the short code in MySQL
3. If found and not expired:
   - Increments click count
   - Logs the click timestamp
   - Redirects (HTTP 302) to the original long URL
4. If expired or not found, returns a "Link not available" page

#### 3. QR Code Generation
1. User clicks "Generate QR" for a short link on their dashboard
2. Frontend sends a GET request to the QR endpoint with the short code
3. Backend uses ZXing to generate a QR image encoding the short URL
4. QR code is returned as a PNG (or Base64-encoded image) to the frontend
5. User can preview, download, or share the QR code

#### 4. Viewing Analytics
1. User opens the dashboard
2. Frontend requests click data for the user's links
3. Backend queries MySQL for click counts and timestamps
4. Aggregated data is returned and rendered as graphs (Chart.js / Recharts)

---

## 📦 Prerequisites

Make sure the following are installed on your system before setting up ZapLink:

### Required
- **Java 17** (LTS) — [Download](https://adoptium.net/)
- **Maven 3.8+** — [Download](https://maven.apache.org/download.cgi)
- **Node.js 18+ and npm** — [Download](https://nodejs.org/)
- **MySQL 8+** — [Download](https://dev.mysql.com/downloads/)
- **Git** — [Download](https://git-scm.com/)

### Optional (recommended)
- **IntelliJ IDEA** — [Download](https://www.jetbrains.com/idea/download/) or **Eclipse** — [Download](https://www.eclipse.org/downloads/) for backend development
- **VS Code** — [Download](https://code.visualstudio.com/) for frontend development
- **Postman** — [Download](https://www.postman.com/downloads/) or **Insomnia** — [Download](https://insomnia.rest/download) for testing the REST API
- **MySQL Workbench** — [Download](https://dev.mysql.com/downloads/workbench/) for database management

---

## ⚙️ Installation & Setup

ZapLink is organized as a monorepo with two folders: `backend/` (Spring Boot) and `frontend/` (React).

### 1. Clone the Repository
```bash
git clone https://github.com/<your-username>/zaplink.git
cd zaplink
```

### 2. Database Setup
Open MySQL and create the database:
```sql
CREATE DATABASE zaplink;
```

Then run the provided schema script to create the required tables:
```bash
mysql -u root -p zaplink < backend\src\main\resources\schema.sql
```

### 3. Backend Setup
```bash
cd backend
```

Configure your database credentials in `src\main\resources\application.properties` (see the [Environment Variables](#-environment-variables) section below).

Build and run:
```bash
mvn clean install
mvn spring-boot:run
```
The backend will start on **http://localhost:8080**.

### 4. Frontend Setup
Open a new terminal:
```bash
cd frontend
npm install
npm start
```
The frontend will start on **http://localhost:3000**.

### 5. Access the Application
Open your browser and go to **http://localhost:3000** to start using ZapLink.

---

## 🔐 Environment Variables

ZapLink requires configuration for both the backend and frontend. **Never commit real credentials or API keys to version control.** Use the `.env.example` files as templates.

### Backend (`backend/src/main/resources/application.properties`)

```properties
# === Server ===
server.port=8080
# Port the Spring Boot backend runs on

# === Database ===
spring.datasource.url=jdbc:mysql://localhost:3306/zaplink
# JDBC connection URL for your MySQL database
spring.datasource.username=<your_mysql_username>
# MySQL username
spring.datasource.password=<your_mysql_password>
# MySQL password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
# MySQL driver class

# === JPA / Hibernate ===
spring.jpa.hibernate.ddl-auto=none
# 'none' since schema is managed manually via schema.sql
spring.jpa.show-sql=true
# Logs SQL queries to the console (useful in dev)
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
# Hibernate dialect for MySQL 8

# === JWT ===
zaplink.jwt.secret=<your_jwt_secret_key_at_least_256_bits>
# Secret key used to sign JWT tokens (keep this private)
zaplink.jwt.expiration-ms=86400000
# Token validity in milliseconds (default: 24 hours)

# === Rate Limiting (Bucket4j) ===
zaplink.ratelimit.capacity=20
# Max requests allowed in the bucket
zaplink.ratelimit.refill-tokens=20
# Tokens added per refill interval
zaplink.ratelimit.refill-duration-minutes=1
# Refill interval in minutes

# === Google Safe Browsing ===
zaplink.safebrowsing.api-key=<your_google_safe_browsing_api_key>
# API key for malicious / phishing URL detection

# === Application ===
zaplink.base-url=http://localhost:8080
# Base URL used when constructing short URLs
```

### Frontend (`frontend/.env`)

```
REACT_APP_API_BASE_URL=http://localhost:8080/api
# Base URL of the backend REST API
```

### `.env.example` Template Files

The repository includes example template files with placeholder values:
- `backend/src/main/resources/application.properties.example`
- `frontend/.env.example`

Copy these to their actual filenames and fill in your own values:

```bash
copy backend\src\main\resources\application.properties.example backend\src\main\resources\application.properties
copy frontend\.env.example frontend\.env
```

> ⚠️ **Important:** Add `application.properties` and `.env` to your `.gitignore` to prevent committing secrets.

---

## 🗄️ Database Schema

ZapLink uses a MySQL database with three main tables: `users`, `links`, and `clicks`.

### `users`
Stores registered user accounts.

| Column          | Type                        | Constraints                          | Description                          |
|-----------------|-----------------------------|--------------------------------------|--------------------------------------|
| `id`            | BIGINT                      | PRIMARY KEY, AUTO_INCREMENT          | Unique user ID                       |
| `username`      | VARCHAR(50)                 | UNIQUE, NOT NULL                     | User's chosen username               |
| `email`         | VARCHAR(100)                | UNIQUE, NOT NULL                     | User's email address                 |
| `password_hash` | VARCHAR(255)                | NOT NULL                             | Hashed password (BCrypt)             |
| `role`          | ENUM('USER', 'ADMIN')       | NOT NULL, DEFAULT 'USER'             | Role for authorization               |
| `is_active`     | BOOLEAN                     | NOT NULL, DEFAULT TRUE               | Used to ban / disable users          |
| `created_at`    | TIMESTAMP                   | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | Account creation time                |

### `links`
Stores shortened URLs created by users.

| Column         | Type          | Constraints                                  | Description                                      |
|----------------|---------------|----------------------------------------------|--------------------------------------------------|
| `id`           | BIGINT        | PRIMARY KEY, AUTO_INCREMENT                  | Unique link ID (used to derive `short_code`)     |
| `short_code`   | VARCHAR(10)   | UNIQUE, NOT NULL                             | Base62-encoded short identifier                  |
| `long_url`     | TEXT          | NOT NULL                                     | The original long URL                            |
| `user_id`      | BIGINT        | NOT NULL, FOREIGN KEY → `users(id)`          | Owner of the link                                |
| `expires_at`   | TIMESTAMP     | NULL                                         | Optional expiration date                         |
| `is_active`    | BOOLEAN       | NOT NULL, DEFAULT TRUE                       | Set to FALSE if disabled by admin                |
| `created_at`   | TIMESTAMP     | NOT NULL, DEFAULT CURRENT_TIMESTAMP          | Link creation time                               |

### `clicks`
Stores individual click events for analytics.

| Column        | Type       | Constraints                              | Description                          |
|---------------|------------|------------------------------------------|--------------------------------------|
| `id`          | BIGINT     | PRIMARY KEY, AUTO_INCREMENT              | Unique click ID                      |
| `link_id`     | BIGINT     | NOT NULL, FOREIGN KEY → `links(id)`      | The link that was clicked            |
| `clicked_at`  | TIMESTAMP  | NOT NULL, DEFAULT CURRENT_TIMESTAMP      | Time of the click                    |

### Relationships
- **One user → many links** (`users.id` → `links.user_id`)
- **One link → many clicks** (`links.id` → `clicks.link_id`)

> 📄 The full schema with indexes is available in `backend/src/main/resources/schema.sql`.

---

## 🔌 API Endpoints

ZapLink exposes a REST API for all functionality. All `/api/*` endpoints (except `/api/auth/*`) require a JWT token in the `Authorization` header. The redirect endpoint (`/{shortCode}`) is public.

> 📘 **Live API Docs:** Once the backend is running, full interactive Swagger UI is available at
> **http://localhost:8080/swagger-ui.html**

### 🔐 Authentication

| Method | Endpoint              | Auth   | Description                          |
|--------|-----------------------|--------|--------------------------------------|
| POST   | `/api/auth/register`  | Public | Register a new user                  |
| POST   | `/api/auth/login`     | Public | Log in and receive a JWT token       |

### 🔗 Links

| Method | Endpoint                  | Auth     | Description                                      |
|--------|---------------------------|----------|--------------------------------------------------|
| POST   | `/api/links`              | Required | Create a new short link                          |
| GET    | `/api/links`              | Required | List all links owned by the current user         |
| GET    | `/api/links/{id}`         | Required | Get details of a specific link                   |
| DELETE | `/api/links/{id}`         | Required | Delete a link owned by the current user          |
| GET    | `/api/links/{id}/qr`      | Required | Get the QR code (PNG) for a short link           |
| GET    | `/{shortCode}`            | Public   | Redirect to the original long URL                |

### 📊 Analytics

| Method | Endpoint                          | Auth     | Description                                            |
|--------|-----------------------------------|----------|--------------------------------------------------------|
| GET    | `/api/links/{id}/analytics`       | Required | Get total clicks + click timestamps for a single link  |

### 🛠️ Admin

| Method | Endpoint                            | Auth         | Description                              |
|--------|-------------------------------------|--------------|------------------------------------------|
| GET    | `/api/admin/users`                  | ADMIN only   | List all users                           |
| PATCH  | `/api/admin/users/{id}/ban`         | ADMIN only   | Ban a user                               |
| DELETE | `/api/admin/users/{id}`             | ADMIN only   | Delete a user                            |
| GET    | `/api/admin/links`                  | ADMIN only   | List all links in the system             |
| PATCH  | `/api/admin/links/{id}/disable`     | ADMIN only   | Disable an abusive / reported link       |
| GET    | `/api/admin/reports`                | ADMIN only   | System-wide analytics & reports          |

### 📦 Example Requests & Responses

#### 🔐 Register
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "securePass123"
}
```
**Response (201 Created)**
```json
{
  "id": 1,
  "username": "johndoe",
  "email": "john@example.com",
  "role": "USER"
}
```

#### 🔐 Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "johndoe",
  "password": "securePass123"
}
```
**Response (200 OK)**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
  "expiresIn": 86400000
}
```

#### 🔗 Create Short Link
```http
POST /api/links
Authorization: Bearer <your_jwt_token>
Content-Type: application/json

{
  "longUrl": "https://example.com/some/very/long/article-url",
  "expiresAt": "2025-12-31T23:59:59"
}
```
**Response (201 Created)**
```json
{
  "id": 42,
  "shortCode": "abc123",
  "shortUrl": "http://localhost:8080/abc123",
  "longUrl": "https://example.com/some/very/long/article-url",
  "expiresAt": "2025-12-31T23:59:59",
  "createdAt": "2025-05-03T10:15:30"
}
```

#### 📊 Get Link Analytics
```http
GET /api/links/42/analytics
Authorization: Bearer <your_jwt_token>
```
**Response (200 OK)**
```json
{
  "linkId": 42,
  "shortCode": "abc123",
  "totalClicks": 137,
  "clicks": [
    { "clickedAt": "2025-05-01T09:12:45" },
    { "clickedAt": "2025-05-01T09:13:02" },
    { "clickedAt": "2025-05-02T14:22:18" }
  ]
}
```

#### 🔗 Redirect (Public)
```http
GET /abc123
```
**Response:** `302 Found` → redirects to the original long URL.

### ⚠️ Error Responses

All errors follow a consistent JSON structure:
```json
{
  "timestamp": "2025-05-03T10:15:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid URL format",
  "path": "/api/links"
}
```

Common HTTP status codes:
- `400` — Validation error (bad URL, missing fields)
- `401` — Missing or invalid JWT token
- `403` — Forbidden (e.g., non-admin hitting admin endpoint)
- `404` — Link / user not found
- `409` — Conflict (e.g., username already taken)
- `429` — Too many requests (rate limit exceeded)

---

## 📁 Folder Structure

ZapLink is organized as a monorepo with two top-level folders: `backend/` (Spring Boot) and `frontend/` (React).

```
zaplink/
├── backend/                              # Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/zaplink/
│   │   │   │   ├── controller/           # REST endpoint handlers
│   │   │   │   ├── service/              # Business logic (URL shortening, analytics, etc.)
│   │   │   │   ├── repository/           # JPA repositories (database access)
│   │   │   │   ├── model/                # JPA entities (User, Link, Click)
│   │   │   │   ├── dto/                  # Request / response DTOs
│   │   │   │   ├── config/               # App configuration (CORS, Swagger, Bucket4j)
│   │   │   │   ├── security/             # JWT filter, auth provider, password encoder
│   │   │   │   ├── exception/            # Custom exceptions & global error handler
│   │   │   │   ├── util/                 # Helpers (Base62 encoder, QR generator, etc.)
│   │   │   │   └── ZaplinkApplication.java
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       ├── application.properties.example
│   │   │       └── schema.sql            # MySQL schema script
│   │   └── test/                         # Unit & integration tests
│   └── pom.xml                           # Maven build file
│
├── frontend/                             # React application
│   ├── public/                           # Static files (index.html, favicon, etc.)
│   ├── src/
│   │   ├── components/                   # Reusable UI components (Navbar, LinkCard, etc.)
│   │   ├── pages/                        # Route-level pages (Home, Login, Dashboard, Admin)
│   │   ├── services/                     # API calls (Axios)
│   │   ├── context/                      # React Context (e.g., AuthContext for JWT)
│   │   ├── utils/                        # Helpers (formatters, validators)
│   │   ├── App.js                        # Root component & routing
│   │   └── index.js                      # Entry point
│   ├── package.json
│   ├── .env
│   └── .env.example
│
├── README.md                             # You're reading it
└── .gitignore
```

---

## 🚀 Usage

### 👤 As a Regular User

1. **Register an account** — Click *Sign Up* and create your account with username, email, and password.
2. **Log in** — Enter your credentials to access your personal dashboard.
3. **Shorten a URL** — Paste a long URL into the input field, optionally set an expiration date, and click *Shorten*.
4. **Copy or share** — Copy the generated short URL or scan the QR code to share it.
5. **Manage your links** — View all your shortened links in the dashboard.
6. **View analytics** — Open any link to see its total click count and activity graph over time.
7. **Generate a QR code** — Click *QR* on any link to download a shareable QR image.
8. **Delete a link** — Remove any link you no longer need.

### 🛠️ As an Admin

1. **Log in** — Use an admin account to access the admin dashboard.
2. **View all users & links** — Browse the full list of registered users and shortened links across the system.
3. **Moderate links** — Disable or remove any link reported as abusive.
4. **Manage users** — Ban or delete users who violate the platform's terms.
5. **System reports** — View overall statistics: total links created, total clicks, active users, and trends over time.

### 🔌 As a Developer (via REST API)

1. **Get a JWT token** — `POST /api/auth/login` with your credentials.
2. **Call the API** — Include the token in the `Authorization: Bearer <token>` header.
3. **Shorten programmatically** — `POST /api/links` with the long URL.
4. **Refer to the [API Endpoints](#-api-endpoints) section** for full details.

> 📘 Full interactive API documentation is available at **http://localhost:8080/swagger-ui.html** when the backend is running.

---

## 📸 Screenshots

> 🚧 *UI is currently under development. Screenshots will be added here once the frontend is complete.*

Planned screenshots (will be placed in `assets/images/`):

### User Dashboard
*Personal dashboard listing all of the user's shortened links with click counts.*
<!-- ![Dashboard](assets/images/dashboard.png) -->

### Analytics
*Detailed analytics view showing total clicks and click activity over time.*
<!-- ![Analytics](assets/images/analytics.png) -->

### Admin Dashboard
*Admin panel showing all users, all links, and system-wide reports.*
<!-- ![Admin Dashboard](assets/images/admin-dashboard.png) -->

---

## 🚢 Deployment

ZapLink is currently in development. The planned deployment approach is:

- **Backend** — Render, Railway, or AWS Elastic Beanstalk (any platform supporting Spring Boot JARs)
- **Frontend** — Vercel or Netlify (any static hosting platform)
- **Database** — Managed MySQL on AWS RDS, Railway, or PlanetScale

### Build for Production

**Backend:**
```bash
cd backend
mvn clean package
```
The runnable JAR will be at `backend\target\zaplink-0.0.1-SNAPSHOT.jar`.

**Frontend:**
```bash
cd frontend
npm run build
```
Production-ready static files will be in `frontend\build\`.

> ⚠️ Remember to update environment variables (`zaplink.base-url`, `REACT_APP_API_BASE_URL`, database credentials, JWT secret, Safe Browsing API key) for the production environment.

---

## 🗺️ Roadmap

The following features are planned for future releases:

### 🔗 Core
- [ ] Custom alias support (user-chosen short codes)
- [ ] Edit destination URL after creation
- [ ] Bulk URL shortening via CSV upload
- [ ] Link preview page before redirect

### 👤 User Accounts
- [ ] Anonymous URL shortening (no login required)
- [ ] Auto-claim guest links on signup

### 📊 Analytics
- [ ] Geographic location tracking (country / city)
- [ ] Device & browser tracking
- [ ] Referrer tracking (where the click came from)

### 🛡️ Security
- [ ] Password-protected short links
- [ ] CAPTCHA for anonymous shortening

### 🔌 API
- [ ] API keys for developers
- [ ] Per-key rate limiting
- [ ] Webhooks for click events

### 💰 Monetization & Teams
- [ ] Free / Paid tiers
- [ ] Custom branded domains (e.g., `go.yoursite.com`)
- [ ] Team / organization accounts

### 🛠️ Admin & Ops
- [ ] Audit logs for admin actions
- [ ] Email notifications (link expiry, abuse reports)

### 🧪 Quality
- [ ] Comprehensive test suite (unit, integration, E2E)
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] Dockerized deployment
