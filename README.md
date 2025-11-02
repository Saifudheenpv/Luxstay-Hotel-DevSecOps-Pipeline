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
   
3. **Build the application**    
   ```bash
   ./build-and-run.sh
   
4. **Or manually:**
   ```bash
   mvn clean package
   java -jar target/hotel-booking-system-1.0.0.jar
   ```
5. ***Access the application***
Open your browser and navigate to: http://localhost:8080


