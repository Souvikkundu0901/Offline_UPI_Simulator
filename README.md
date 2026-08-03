# Offline UPI Payment Simulator

An offline UPI-style payment simulator built for learning Java, SQLite, frontend design, transaction handling, and idempotent payment processing.

This is not a real UPI app. Real UPI payments require banks, NPCI infrastructure, and network connectivity. This project simulates the flow locally for academic and portfolio use.

## Features

- User registration with name, mobile number, UPI ID, and 4-digit PIN
- Login using UPI ID and PIN
- Add demo wallet balance
- Send money to another locally registered UPI ID
- Payment data packets with request IDs
- Idempotency handling to prevent duplicate debits on retry
- Transaction history
- Registered users list
- SQLite database storage for the Java app
- Console version
- Java Swing desktop version
- Modern HTML/CSS/JavaScript frontend

## Tech Stack

- Java
- Java Swing
- SQLite
- SQLite JDBC driver
- HTML
- CSS
- JavaScript

## Project Structure

```text
.
+-- README.md
+-- src/
|   +-- main/
|       +-- java/
|           +-- com/
|               +-- offlineupi/
|                   +-- Main.java
|                   +-- PaymentService.java
|                   +-- StorageService.java
|                   +-- SwingMain.java
+-- web/
    +-- index.html
    +-- styles.css
    +-- app.js
```

## Database

The Java console and Swing apps store data in SQLite:

```text
data/offline-upi.db
```

SQLite tables:

- `users`: stores account details and balance
- `transactions`: stores debit and credit ledger entries
- `packet_receipts`: stores processed packet request IDs for idempotency

The web frontend stores demo data in browser `localStorage`. A plain browser page cannot directly write to a SQLite database without a backend server or extra browser-side database library.

## Idempotency Logic

Each payment is sent as a `PaymentPacket` with:

- request ID
- sender UPI ID
- receiver UPI ID
- amount
- UPI PIN

The request ID acts as an idempotency key.

If the same packet is processed again, the app returns the saved result and does not debit the sender twice. If the same request ID is reused with different payment details, the app rejects it as an idempotency conflict.

## Requirements

- JDK 8 or later
- SQLite JDBC driver jar

Download the SQLite JDBC driver and place it here:

```text
lib/sqlite-jdbc.jar
```

The jar is not included in this repository. Download it locally before running the Java app.

## Run Java Version

Open a terminal in the project folder.

Compile:

```bash
javac -d out src/main/java/com/offlineupi/*.java
```

Run the console app:

```bash
java -cp "out;lib/sqlite-jdbc.jar" com.offlineupi.Main
```

Run the Swing desktop app:

```bash
java -cp "out;lib/sqlite-jdbc.jar" com.offlineupi.SwingMain
```

On macOS/Linux, use `:` instead of `;` in the classpath:

```bash
java -cp "out:lib/sqlite-jdbc.jar" com.offlineupi.SwingMain
```

## Run in VS Code

1. Install JDK 8 or later.
2. Install the VS Code Extension Pack for Java.
3. Open this repository folder in VS Code.
4. Add `lib/sqlite-jdbc.jar` to the project.
5. Open `src/main/java/com/offlineupi/SwingMain.java`.
6. Click Run above `public static void main`.

## Run Web Frontend

Open this file in a browser:

```text
web/index.html
```

The web frontend works offline and does not need npm or internet access.

## GitHub Notes

Do not commit generated build output, local database files, or downloaded dependency jars. This project includes a `.gitignore` for:

- `out/`
- `data/`
- `*.class`
- `lib/*.jar`

## Example Workflow

1. Register two users, for example `alice@offline` and `bob@offline`.
2. Login as `alice@offline`.
3. Add demo money to Alice's wallet.
4. Send a payment packet to `bob@offline`.
5. Retry the same packet.
6. Check that Alice is not debited twice.
7. Try using the same request ID with different payment details.
8. Check that the app rejects the conflict.

## Limitations

- This is only a simulator.
- It does not connect to banks or real UPI systems.
- PINs are stored plainly for learning simplicity. A production app must hash and protect secrets.
- The web frontend and Java SQLite database are separate because the browser version has no backend server.

## Use Case

This project is useful for learning:

- Java OOP
- Swing UI development
- SQLite persistence
- Payment flow simulation
- Idempotency keys
- Transaction history design
- Offline-first application behavior
