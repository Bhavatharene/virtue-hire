VIRTUEHIRE PROJECT OVERVIEW

1. Introduction
VirtueHire is a full-stack recruitment assessment platform designed to streamline the hiring process through secure, monitored online examinations. The system enables organizations to conduct structured assessments while ensuring integrity through real-time monitoring and strict security mechanisms.

2. Tech Stack	
2.1 Frontend
• Framework: React 19
• Routing: React Router v7
• Styling: Vanilla CSS with FontAwesome and Lucide React icons
• API Communication: Axios (RESTful APIs)
• Real-time Communication: STOMP with SockJS
• CSV Parsing: PapaParse

2.2 Backend
• Programming Language: Java 17
• Framework: Spring Boot 3.1.4
• Database: MySQL 8
• Persistence Layer: Spring Data JPA
• Real-time Communication: Spring WebSocket (STOMP)
• Email Service: Spring Boot Starter Mail (SMTP)
• Utilities: Apache Commons CSV, Lombok, Commons IO
• API Documentation: SpringDoc OpenAPI (Swagger)

3. System Architecture
VirtueHire follows a Monolithic Architecture with a separate frontend and backend, ensuring a clear separation of concerns.
3.1 Infrastructure Layer
The MySQL database stores:
• User data (HR, Candidates, Admins)
• Questions and assessments
• Test results and performance metrics

3.2 API Layer
Spring Boot REST controllers handle:
• Authentication and authorization
• Question upload and management
• Assessment creation and execution
• Result processing and analytics

3.3 Real-Time Monitoring Engine
• Implemented using WebSockets (STOMP protocol)
• Enables HR to monitor candidates during live exams
• Tracks candidate activity and behavior in real time

3.4 Security Layer
The frontend enforces strict exam rules using:
• Page Visibility API (detect tab switching)
• Fullscreen API (force fullscreen mode)
• Disabled right-click and copy/paste
• Automatic violation tracking and reporting to backend

4. Key Features
4.1 Candidate Features
• Personalized dashboard to track:
o Assessment history
o Certifications and performance
• Secure exam environment:
o Forced fullscreen mode
o Tab-switch detection
o Disabled copy/paste and right-click
o Auto-submission after repeated violations
• Timed assessments with:
o Progress tracking
o Auto-save functionality

4.2 HR (Recruiter) Features
• Bulk question upload using CSV files with multiple sections
• Flexible assessment creation:
o Multiple subjects (Aptitude, Technical, Vocabulary, etc.)
o Custom number of sections
• Assessment control:
o Lock / Unlock tests
• Live monitoring dashboard:
o View candidate progress
o Detect violations instantly
• Result analytics:
o Scores and performance insights
o Skill badges (e.g., “Python Expert”)
o Pass/Fail evaluation

4.3 Admin Features
• User management:
o Approve or manage HR and candidate accounts
• Subscription management:
o Monitor payments
o Manage plans
• Global configuration:
o Set passing thresholds
o Manage centralized question banks

5. Project Structure
5.1 Backend Module – Virtue-Candidate
• Developed using Spring Boot
• Contains:
o Business logic
o REST controllers
o JPA repositories
o Security and monitoring services

5.2 Frontend Module
• Developed using React
• Provides user interfaces for:
o Candidates
o HR (Recruiters)
o Admins

6. Conclusion
VirtueHire provides a comprehensive and secure platform for conducting online recruitment assessments. By combining real-time monitoring, strong security mechanisms, and flexible assessment management, the system ensures both efficiency and integrity in the hiring process.

