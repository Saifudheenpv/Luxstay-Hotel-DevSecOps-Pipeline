# Hotel Booking System

A full-stack hotel booking system built with Spring Boot, Thymeleaf, MySQL, and Bootstrap.

## Features

- **User Management**: Registration, login, profile management
- **Hotel Search**: Search hotels by name, city, or location
- **Room Booking**: Book available rooms with date selection
- **Booking Management**: View, cancel, and manage bookings
- **Responsive Design**: Mobile-friendly interface
- **Secure Authentication**: Password encryption and session management

## Technology Stack

- **Backend**: Spring Boot, Spring Data JPA, Spring MVC
- **Frontend**: Thymeleaf, Bootstrap 5, JavaScript
- **Database**: MySQL
- **Security**: Custom password encryption
- **Build Tool**: Maven

## Prerequisites

- Java 11 or higher
- MySQL 8.0 or higher
- Maven 3.6 or higher

## Installation & Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd hotel-booking-system

2. **Set up the database**
   ```bash
   ./scripts/setup-database.sh
   **
3. **Build the application**    
   ```bash
   ./build-and-run.sh
   
**Or manually:**
   ```bash
   mvn clean package
   java -jar target/hotel-booking-system-1.0.0.jar
   
4. ***Access the application***
Open your browser and navigate to: http://localhost:8080

Default Accounts
Admin: admin / admin123

User: john_doe / password123

Project Structure
text
src/main/java/com/hotel/
├── config/          # Configuration classes
├── controller/      # MVC Controllers
├── model/          # Entity classes
├── repository/     # Data access layer
├── service/        # Business logic
└── util/           # Utility classes

src/main/resources/
├── templates/      # Thymeleaf templates
├── static/         # CSS, JS, images
└── application.properties
API Endpoints
GET / - Home page

GET /hotels - List all hotels

GET /hotels/{id} - Hotel details and rooms

GET /bookings/new/{roomId} - Booking form

POST /bookings/create - Create booking

GET /bookings/my-bookings - User bookings

GET /auth/login - Login page

POST /auth/login - Process login

GET /auth/register - Registration page

POST /auth/register - Process registration

Database Schema
The system uses the following main tables:

users - User accounts

hotels - Hotel information

rooms - Room details

bookings - Booking records

Configuration
Key configuration in application.properties:

Database connection settings

JPA configuration

Server port

Thymeleaf settings

Development
The application uses Spring Boot DevTools for hot reload

Database is automatically populated with sample data

Logs are configured for debugging

Deployment
The application can be deployed using:

Traditional WAR deployment

Docker container

Cloud platforms (Heroku, AWS, etc.)

License
This project is licensed under the MIT License.

Support
For support and questions, please contact the development team.
