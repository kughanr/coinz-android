**Coinz**

Coinz is an Android application developed as a university project. It is a location-based game where players collect, deposit, and message(transfer) coins that are spawned on a map (in Edinburgh). The game utilizes real-life movement as players walk around to interact with virtual coins.

Note: google-services.json file from Firebase removed for security reasons

Technologies Used
Kotlin: Main programming language used for application development.
XML: Used for designing the user interface.
Gradle: Build system for managing dependencies and building the project.
Firebase: Used for user management and as a database backend.
MapBox: Integrated for building and displaying maps within the application.
Java: Utilized for writing tests to ensure code quality and reliability.

Features
Coin Collection: Multiple types of coins are spawned on the map, and players can collect them by physically moving to their location.
Coin Deposit: Players can deposit collected coins into their virtual account.
Messaging: The ability to transfer coins of different types to another user.
User Management: Firebase is used for user authentication and management.
Database backend: NoSQL Firestore database

Usage
Upon launching the application, users are prompted to log in or create an account.
Once logged in, users can view the map with spawned coins marked around Edinburgh.
Users can move around in real life to collect coins.
Collected coins can be deposited into the user's account.
Coins can be sent to another user.

Testing
Java was utilized for writing tests to ensure the reliability and functionality of the application. Tests cover various aspects such as user authentication, coin collection, and messaging functionality.

