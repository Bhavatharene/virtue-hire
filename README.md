# VirtueHire: AI-Powered Talent Assessment & Management Platform

VirtueHire is an enterprise-grade recruitment ecosystem that bridges the gap between talent and opportunity through intelligent assessments, real-time monitoring, and seamless user management.

---

## 🏗 Key Functional Modules

### 1. Candidate Lifecycle & Experience
VirtueHire provides a streamlined journey for candidates to showcase their skills:
- **Smart Registration**: Multi-step onboarding with profile picture and resume uploads.
- **Adaptive Assessment System**:
    - **Graded Levels**: Candidates progress through Easy, Medium, and Hard assessments.
    - **Automated Grading**: Instant results and performance analysis.
    - **Expert Badges**: Top performers (high cumulative scores) earn the prestigious **"Java Expert"** badge, visible to recruiters.
- **Personalized Dashboard**: A dedicated view for candidates to track their attempt history, scores, and active credentials.

### 2. HR & Recruiter Capabilities
Recruiters gain deep insights and live control over the hiring pipeline:
- **Comprehensive Candidate Discovery**:
    - Filter the talent pool by skills, experience, and assessment scores.
    - Instant access to candidate resumes and profile details.
- **Assessment Management**:
    - **CSV Question Bank Upload**: Bulk import questions with support for specific subjects and levels.
    - **Dynamic Configuration**: Configure test sections, time limits, and question distribution (e.g., 10 Basic, 10 Intermediate, 10 Hard questions).
    - **Live Monitoring (WebSocket)**: The **Live Monitoring** tab uses STOMP over WebSockets to track active candidate tests in real-time, showing exactly where they are and their current progress.
- **Dashboard Analytics**: Top-level stats on recruitment health (Total Candidates, Tests Taken, Pending Approvals).

### 3. Administrator Operations
Admins maintain the platform's integrity and monitor system-wide activity:
- **User Verification Workflow**: Review and approve/reject HR and Candidate account requests.
- **Global Data Control**:
    - Manage the master list of all candidates and HR personnel.
    - Oversee the integrated **Question Bank Management** (Edit/Delete questions).
- **Payment & Subscription Logging**: Monitor all financial transactions and subscription plan updates across the platform.

---

## 💰 Subscription & Monetization Model

VirtueHire features a tiered subscription system for HR professionals:
- **Free Account**: Basic candidate viewing and limited assessments.
- **Monthly Unlimited**: Unlimited candidate views and full access for 30 days.
- **10 Candidates Pack**: A one-time purchase for specific project-based hiring needs.
- **Single Candidate View**: Pay-per-view access for niche recruitment.
*Payments are logged and verified via an integrated gateway flow (simulated).*

---

## ⚡ Technical Implementation Highlights

### Real-Time Live Monitoring
The platform utilizes **STOMP over WebSockets** to provide recruiters with a live feed of active assessments. This enables:
- Real-time status updates (Started, In-Progress, Completed).
- Proactive monitoring of candidate activity during high-stakes evaluations.

### Robust Registration & Security
- **Email Uniqueness Check**: Prevents duplicate accounts by enforcing strict email validation during registration.
- **Multipart Data Handling**: Securely manages concurrent uploads of resumes (PDF/DOC) and profile pictures.
- **Scoped CSS Architecture**: Modern design system using component-specific CSS to prevent style leakage and ensure a premium, consistent visual experience.

---

## 🛠 Tech Stack

- **Frontend**: React 19, Lucide Icons, Vanilla CSS (Glassmorphism), Axios
- **Backend**: Spring Boot 3.1.4, Spring Security, MySQL 8
- **Communication**: REST APIs & STOMP WebSockets
- **Infrastructure**: Maven, Local Storage for Media

---

## 🚀 Local Setup & Production

### Deployment
The system is pre-configured for production hosting:
- **Backend Link**: [https://backend.virtuehire.in](https://backend.virtuehire.in)
- **Frontend Link**: [https://admin.virtuehire.in](https://admin.virtuehire.in)

### Development
1. **Backend**: `mvn spring-boot:run` (Port 8081)
2. **Frontend**: `npm start` (Port 3000)
*Centralized environment switching via `Frontend/src/config.js`.*
