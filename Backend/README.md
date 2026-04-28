# VirtueHire — Recruitment Assessment Platform

A full-stack online recruitment assessment platform that allows **HR teams** to upload questions, create assessments, monitor candidates in real-time, and track performance — while **candidates** take secure, section-based timed exams.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, React Router v6, Axios, @stomp/stompjs, Lucide React |
| Backend | Spring Boot 3.1.4, Spring Data JPA, Spring WebSocket (STOMP), Spring Mail |
| Database | MySQL 8 |
| CSV Parsing | Apache Commons CSV |
| Build | Maven (backend), npm (frontend) |

---

## Key Features

### 🛡️ Assessment Security (Anti-Cheating)
- **Fullscreen Enforcement**: Exams automatically enter fullscreen. Exiting is tracked as a violation.
- **Tab Switching Detection**: Uses Page Visibility API to detect and warn when candidates switch tabs or minimize the window.
- **Input Restrictions**: Disabled right-click, copy, paste, and text selection to prevent external assistance.
- **Keyboard Shortcut Blocking**: Disables F12, Ctrl+Shift+I, Ctrl+U, and other developer tool shortcuts.
- **Auto-Submission**: If a candidate reaches **3 security violations**, the exam is automatically submitted and their access revoked.
- **Auto-Save**: Answers are periodically saved to the cloud to prevent data loss on connection drop.

### 🎥 Live Monitoring Dashboard (HR Side)
- **Real-time Updates**: HR can monitor active candidates live via **WebSockets**.
- **Violation Alerts**: Real-time alerts when candidates switch tabs or exit fullscreen.
- **Activity Feed**: View exactly when each candidate started, finished, or committed a violation.
- **Status Indicators**: Color-coded rows to quickly identify healthy vs. flagged exam attempts.

### 📊 HR Management & Results
- **Assessment Management**: Lock/Unlock active assessments to control access and safely delete assessments by ID.
- **Detailed Profiles**: View candidate assessment history in a professional table including scores, subjects, and pass/fail status.
- **Cumulative Results**: Automatic badge awarding (e.g., "Python Expert") based on performance across levels.
- **Question Bank**: Dynamically search and manage questions uploaded via multi-section CSVs.

---

## Project Structure

```
Virtue-Candidate/
├── src/                            # Spring Boot backend
│   └── main/java/com/virtuehire/
│       ├── controller/             # REST controllers (HR, Candidate, Assessment, Admin)
│       ├── model/                  # JPA entities (Candidate, HR, Question, ExamActivity...)
│       ├── repository/             # Spring Data repositories
│       ├── service/                # Business logic & WebSocket services
│       └── config/                 # WebSocketConfig, Security configs
├── Frontend/                       # React frontend
│   └── src/
│       ├── components/
│       │   ├── HR/                 # Live Monitoring, TestManager, LiveAssessments
│       │   ├── assessment/         # Assessment security logic, Level UI, Instructions
│       │   ├── Candidate/          # Welcome, Profile
│       │   ├── Admin/              # Dashboards, Questions
│       │   └── Payment/            # Plans
│       ├── services/api.js         # API instance
│       └── services/websocket.js   # WebSocket handling
├── pom.xml
└── dummy-test.csv                  # Sample CSV for testing
```

---

## Getting Started

### Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8 running locally
- Maven 3.8+

### 1. Database Setup

Create a MySQL database and configure `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/virtuehire
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.mail.username=your_gmail@gmail.com
spring.mail.password=your_app_password
file.upload-dir=./uploads
```

### 2. Run the Program

**Backend:**
```bash
mvn spring-boot:run
```
(Starts on **https://backend.virtuehire.in**)

**Frontend:**
```bash
cd Frontend
npm start
```
(Starts on **http://localhost:3000**)

---

## Key API Endpoints

### HR & Monitoring APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/hrs/login` | HR login |
| `POST` | `/api/hrs/questions/upload-csv` | Upload questions CSV |
| `PUT`  | `/api/hrs/assessments/:id/lock` | Toggle assessment lock status |
| `DELETE` | `/api/hrs/assessments/:id` | Delete an assessment by ID |
| `GET`  | `/api/hrs/candidates/results` | View all assessment results |
| `GET`  | `/api/hrs/candidates/:id` | Detailed results for a candidate |
| `WS`   | `/ws-monitoring` | WebSocket endpoint for live monitoring |

### Candidate & Security APIs

| Method | Path | Description |
|---|---|---|
| `GET`  | `/api/assessment/status/:subject` | Check if assessment is locked/active |
| `POST` | `/api/assessment/activity` | Log security violations/events |
| `GET`  | `/api/assessment/:subject/...` | Fetch questions for level |
| `POST` | `/api/assessment/submit` | Final submission logic |

---

## User Roles

| Role | Access |
|---|---|
| **Candidate** | Secure, monitored exams, profile management |
| **HR** | Question banking, exam creation, **Live Monitoring**, results |
| **Admin** | Approval workflows, payment management, global stats |

---

## License

This project is intended for internal recruitment use by VirtueHire. All rights reserved.
