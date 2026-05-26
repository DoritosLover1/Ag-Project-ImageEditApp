# 🎨 PaiCollab: Real-Time Collaborative Drawing Platform
### *Advanced Java Swing & TCP Socket Programming Project*

---

## 📑 Table of Contents / İçindekiler
1.  [English Documentation](#english)
    *   [Introduction](#intro-en)
    *   [Architecture & Design](#arch-en)
    *   [Communication Protocol](#proto-en)
    *   [PCMP/2.1 Full Protocol Specification](#pcmp-en)
    *   [Key Features](#features-en)
    *   [Installation & Usage](#install-en)
2.  [Türkçe Dokümantasyon](#türkçe)
    *   [Giriş](#intro-tr)
    *   [Mimari ve Tasarım](#arch-tr)
    *   [İletişim Protokolü](#proto-tr)
    *   [PCMP/2.1 Tam Protokol Spesifikasyonu](#pcmp-tr)
    *   [Temel Özellikler](#features-tr)
    *   [Kurulum ve Kullanım](#install-tr)

---

<a name="english"></a>
## 🇬🇧 English Documentation

<a name="intro-en"></a>
### 1. Introduction
**PaiCollab** is a sophisticated multi-user drawing application that allows users to create art collectively on a shared digital canvas. Unlike simple paint apps, PaiCollab synchronizes every brush stroke, shape, and cursor position across a network in real-time, providing a seamless collaborative experience.

<a name="arch-en"></a>
### 2. Architecture & Design
The project is built on a **Centralized Server-Client Model**:
*   **Multithreaded Server:** Handles incoming connections using a `Thread-per-Client` approach. It manages "Rooms", enabling private sessions via unique room codes.
*   **State Synchronization:** The server maintains a "Master Snapshot" of the canvas. When a new user joins, the server sends the entire drawing history to ensure the newcomer sees exactly what everyone else sees.
*   **Double-Buffered Rendering:** Custom `DrawingCanvas` uses Java AWT/Swing double-buffering to prevent flickering during rapid updates.
*   **Persistence Layer:** Themes are stored in `theme.properties` using the Java Properties API, while drawings can be serialized to the `saved_canvases` directory.

<a name="proto-en"></a>
### 3. Communication Protocol (PaiProtocol)
The application uses a high-speed, lightweight pipe-delimited string protocol:

| Command | Format | Description |
| :--- | :--- | :--- |
| `JOIN` | `JOIN\|RoomCode\|Username` | User attempts to join a specific room. |
| `DRAW` | `DRAW\|User\|Type\|X\|Y\|...` | Broadcasts a new shape (Rect, Circle, Triangle, etc.) |
| `CURSOR`| `CURSOR\|User\|X\|Y\|Color` | Real-time tracking of remote cursors. |
| `DELETE`| `DELETE\|User\|ShapeID` | Removes a specific item from all canvases. |
| `CLEAR` | `CLEAR\|User` | Clears the entire canvas for everyone. |

---

<a name="pcmp-en"></a>
### 4. PCMP/2.1 Full Protocol Specification

```
PaiCollab Messaging Protocol                                  PCMP/2.1
Category: Application Layer Protocol                          May 2026
```

#### Summary

This document defines the communication between the server and clients in the PaiCollab drawing and messaging application. You can build a compliant client in any programming language or platform and communicate with the server. The protocol is language- and platform-independent.

---

#### Table of Contents

1. Transport Layer
2. Message Structure (Envelope)
3. State Machine (FSM)
4. Message Types and Actions Upon Receipt
   - 4.1 Session Management
   - 4.2 Room Management
   - 4.3 Drawing Commands
   - 4.4 Messaging
5. Broadcast Rules
6. Room Entry Flow Sequence
7. Data Types and Constraints

---

#### 4.1 Transport Layer

PCMP is built on top of the TCP protocol to provide reliable, ordered, and stream-based communication.

| Property       | Value                       |
| :------------- | :-------------------------- |
| Protocol       | TCP                         |
| Default Port   | 12345                       |
| Character Set  | UTF-8                       |
| Message End    | LF (0x0A)                   |
| Field Separator| Vertical Bar / Pipe (0x7C)  |

Each message consists of a single line terminated by a newline character (LF). The receiving party (the server) splits the incoming data stream by the LF character to separate messages, then reads and processes the values in the required fields.

---

#### 4.2 Message Structure (Envelope)

Each message consists of four fixed header fields followed by command-specific data fields. All fields are separated by the pipe character.

```
MESSAGE_SET:
  [MESSAGE_ID] | [TIMESTAMP] | [SENDER] | [COMMAND] | [DATA_1] | [DATA_2] | ...
      0               1            2           3           4           5
```

| Field       | Position | Type     | Description                                          |
| :---------- | :------- | :------- | :--------------------------------------------------- |
| MESSAGE_ID  | 0        | Text     | 8-character unique message identifier                |
| TIMESTAMP   | 1        | Number   | Timestamp in Unix epoch milliseconds                 |
| SENDER      | 2        | Text     | Sender's username or the constant value `SERVER`     |
| COMMAND     | 3        | Text     | Command name written in uppercase                    |
| DATA_N      | 4+       | Variable | Command-specific parameters                          |

All messages from the server carry `SERVER` in the SENDER field. On the client side, the SENDER field contains the client's username.

---

#### 4.3 State Machine (FSM)

A client can be in one of five states throughout its lifecycle and acts according to its current state when sending or receiving messages:

```
         ┌──────────────┐
         │ DISCONNECTED │
         └──────┬───────┘
                │ TCP connection established
                ▼
         ┌──────────────┐
         │  CONNECTED   │──── LOGIN sent ──────────►┌────────────────┐
         └──────────────┘                           │ AUTHENTICATING │
                                                    └───────┬────────┘
                                                            │ LOGIN_SUCCESS received
                                                            ▼
                                               ┌────────────────────┐
                                    ┌─────────►│       LOBBY        │◄────────┐
                                    │          └──────────┬──────────┘        │
                                    │                     │ CREATE_ROOM       │
                                    │                     │ or JOIN_ROOM      │
                                    │                     ▼                   │
                                    │          ┌────────────────────┐         │
                                    └──────────│       ROOM         │─────────┘
                              LEAVE_ROOM recv  └────────────────────┘ LEAVE_ROOM
                                              CHAT / DRAW / CURSOR operations
```

| State          | Description                                                                            |
| :------------- | :------------------------------------------------------------------------------------- |
| DISCONNECTED   | TCP connection has not yet been established or has been terminated.                    |
| CONNECTED      | TCP connection is established; authentication has not been performed.                  |
| AUTHENTICATING | LOGIN message has been sent; waiting for server response.                              |
| LOBBY          | Authentication is complete; not currently in any room.                                 |
| ROOM           | Joined a room; drawing and messaging operations are active.                            |

---

#### 4.4 Message Types and Actions Upon Receipt

##### Session Management

---

###### LOGIN
**Direction:** Client → Server

Sent to log in to the server with a username. If the username is available, the server sends a LOGIN_SUCCESS message; otherwise it sends an ERROR message. The client sends this message while in the CONNECTED state.

| Position | Field    | Type | Description             |
| :------- | :------- | :--- | :---------------------- |
| 4        | username | Text | Requested username      |

**Server Response:** LOGIN_SUCCESS or ERROR

---

###### LOGIN_SUCCESS
**Direction:** Server → Client

Confirms that the login request has been accepted. If the login fails, an ERROR message is sent.

| Position | Field    | Type | Description             |
| :------- | :------- | :--- | :---------------------- |
| 4        | nickname | Text | Approved username       |

**When the client receives this message:**
- Stores the approved username locally
- Transitions to LOBBY state and shows the room selection screen

---

###### ERROR
**Direction:** Server → Client

Sent to the client when an operation or command is not accepted by the server. The message content contains error information about the operation or command.

| Position | Field         | Type | Description       |
| :------- | :------------ | :--- | :---------------- |
| 4        | error_message | Text | Error description |

**When the client receives this message:**
- Displays the error message to the user
- Makes no state change

---

##### Room Management

---

###### CREATE_ROOM
**Direction:** Client → Server

The client sends a request to create a new room while in the LOBBY state. If the room is successfully created, a ROOM_INFO message followed by a snapshot sequence is sent. Otherwise, an ERROR message is sent.

| Position | Field | Type | Description          |
| :------- | :---- | :--- | :------------------- |
| 4        | —     | Text | Constant value: `NEW` |

**Server Response:** ROOM_INFO followed by snapshot sequence, or ERROR

---

###### JOIN_ROOM
**Direction:** Client → Server

The client sends a request to join an existing room while in the LOBBY state, using a 6-character room code. If successful, a ROOM_INFO message followed by a snapshot sequence is sent. Otherwise, an ERROR message is sent.

| Position | Field    | Type | Description               |
| :------- | :------- | :--- | :------------------------ |
| 4        | roomCode | Text | 6-character room code     |

**Server Response:** ROOM_INFO followed by snapshot sequence, or ERROR

---

###### ROOM_INFO
**Direction:** Server → Client

Confirms that the room entry has been approved and marks the beginning of the snapshot sequence. If the user cannot join the room, an ERROR message is sent. If successful, the user begins receiving the snapshot sequence to get the current state of the room. After the snapshot sequence ends, the user list in the room is sent. After the user list, CHAT_HISTORY messages are sent. The user is then ready to chat and draw.

| Position | Field    | Type | Description          |
| :------- | :------- | :--- | :------------------- |
| 4        | roomCode | Text | Code of the entered room |

**When the client receives this message:**
- Clears the drawing area completely
- Clears the chat history
- Removes all remote cursors from the screen
- Transitions to ROOM state
- Prepares to receive the following snapshot messages
- After the snapshot sequence, receives the room's user list
- After the user list, receives CHAT_HISTORY messages
- The user is now ready to chat and draw

---

###### LEAVE_ROOM
**Direction:** Client → Server

Sends a request to leave the current room. The client can send this message while in the ROOM state.

| Position | Field | Type | Description            |
| :------- | :---- | :--- | :--------------------- |
| 4        | —     | Text | Constant value: `LEAVE` |

**Server:** Removes the client from the room and sends an updated USER_LIST to remaining members.  
**Client:** Returns to LOBBY state. Clears all existing drawings, cursors, and messages.

---

###### QUIT
**Direction:** Client → Server

Ensures the connection is gracefully terminated before closing the application.

| Position | Field | Type | Description           |
| :------- | :---- | :--- | :-------------------- |
| 4        | —     | Text | Constant value: `QUIT` |

**Server:** Removes the client from the room and system, closes the TCP connection.  
**Client:** Clears all drawings, cursors, and messages. Closes the connection.

---

###### USER_LIST
**Direction:** Server → All Room Members

Notifies the current member list of the room. Automatically sent whenever a user joins or leaves.

| Position | Field | Type | Description                                    |
| :------- | :---- | :--- | :--------------------------------------------- |
| 4        | users | Text | Comma-separated list of usernames              |

**When the client receives this message:**
- Updates the member list
- Removes the cursor of any user no longer in the list

---

###### NEW_USERNAME
**Direction:** Client → Server

Sends a request to change the username. If available, the server sends NAME_CHANGED; otherwise ERROR. Can only be sent in the LOBBY state.

| Position | Field   | Type | Description                     |
| :------- | :------ | :--- | :------------------------------ |
| 4        | oldNick | Text | Current username                |
| 5        | newNick | Text | Requested new username          |

**Server Response:** NAME_CHANGED or ERROR

---

###### NAME_CHANGED
**Direction:** Server → Client

Notifies the client that the username change request has been approved.

| Position | Field   | Type | Description                  |
| :------- | :------ | :--- | :--------------------------- |
| 4        | newNick | Text | Approved new username        |

**When the client receives this message:**
- Updates the local username
- Uses the new name in subsequent messages sent to the server
- The server delivers an updated USER_LIST to other room members

---

##### Drawing Commands

All drawing commands can be sent and received while in the ROOM state. Each drawing object carries a UUID generated by the client that is unique across the network.

All color values are 7-character hexadecimal strings in `#RRGGBB` format.

---

###### SQUARE — Rectangle
**Direction:** Client → Server → Other Clients

| Position | Field  | Type    | Description                     |
| :------- | :----- | :------ | :------------------------------ |
| 4        | x      | Integer | Top-left corner X coordinate    |
| 5        | y      | Integer | Top-left corner Y coordinate    |
| 6        | w      | Integer | Width (pixels)                  |
| 7        | h      | Integer | Height (pixels)                 |
| 8        | color  | Text    | Fill or stroke color            |
| 9        | stroke | Integer | Border thickness (pixels)       |
| 10       | filled | Boolean | Filled? (`true` / `false`)      |
| 11       | id     | Text    | Unique object identifier        |

**When the client receives this message:**
- Adds the rectangle to the drawing area if the ID has not been added before
- Ignores the message if the ID already exists (duplicate protection)
- Adds the object to the local list to match the server's stored snapshot list

---

###### CIRCLE — Ellipse
**Direction:** Client → Server → Other Clients

Field structure is identical to SQUARE; the object is drawn as an ellipse fitting inside the bounding rectangle.

| Position | Field  | Type    | Description                              |
| :------- | :----- | :------ | :--------------------------------------- |
| 4        | x      | Integer | Bounding box top-left X                  |
| 5        | y      | Integer | Bounding box top-left Y                  |
| 6        | w      | Integer | Width (pixels)                           |
| 7        | h      | Integer | Height (pixels)                          |
| 8        | color  | Text    | Fill or stroke color                     |
| 9        | stroke | Integer | Border thickness (pixels)                |
| 10       | filled | Boolean | Filled? (`true` / `false`)               |
| 11       | id     | Text    | Unique object identifier                 |

**When the client receives this message:** Same processing as SQUARE.

---

###### LINE — Line Segment
**Direction:** Client → Server → Other Clients

| Position | Field  | Type    | Description                     |
| :------- | :----- | :------ | :------------------------------ |
| 4        | x1     | Integer | Start point X                   |
| 5        | y1     | Integer | Start point Y                   |
| 6        | x2     | Integer | End point X                     |
| 7        | y2     | Integer | End point Y                     |
| 8        | color  | Text    | Line color                      |
| 9        | stroke | Integer | Line thickness (pixels)         |
| 10       | id     | Text    | Unique object identifier        |

**When the client receives this message:** Same duplicate protection as SQUARE.

---

###### TRIANGLE — Triangle
**Direction:** Client → Server → Other Clients

| Position | Field  | Type    | Description                     |
| :------- | :----- | :------ | :------------------------------ |
| 4        | x1     | Integer | 1st vertex X                    |
| 5        | y1     | Integer | 1st vertex Y                    |
| 6        | x2     | Integer | 2nd vertex X                    |
| 7        | y2     | Integer | 2nd vertex Y                    |
| 8        | x3     | Integer | 3rd vertex X                    |
| 9        | y3     | Integer | 3rd vertex Y                    |
| 10       | color  | Text    | Fill or stroke color            |
| 11       | stroke | Integer | Border thickness (pixels)       |
| 12       | filled | Boolean | Filled? (`true` / `false`)      |
| 13       | id     | Text    | Unique object identifier        |

**When the client receives this message:** Same duplicate protection as SQUARE.

---

###### FREEHAND — Free Drawing
**Direction:** Client → Server → Other Clients

| Position | Field    | Type    | Description                                          |
| :------- | :------- | :------ | :--------------------------------------------------- |
| 4        | x_points | Text    | Comma-separated array of X coordinates               |
| 5        | y_points | Text    | Comma-separated array of Y coordinates               |
| 6        | color    | Text    | Line color                                           |
| 7        | stroke   | Integer | Line thickness (pixels)                              |
| 8        | id       | Text    | Unique object identifier                             |

The x_points and y_points arrays must be the same length; each index represents a coordinate pair.

**When the client receives this message:** Same duplicate protection as SQUARE.

---

###### IMAGE — Pasted Image
**Direction:** Client → Server → Other Clients

| Position | Field       | Type    | Description                                  |
| :------- | :---------- | :------ | :------------------------------------------- |
| 4        | x           | Integer | Image top-left X                             |
| 5        | y           | Integer | Image top-left Y                             |
| 6        | w           | Integer | Width (pixels)                               |
| 7        | h           | Integer | Height (pixels)                              |
| 8        | base64_data | Text    | Base64-encoded PNG data                      |
| 9        | id          | Text    | Unique object identifier                     |

Image data is encoded with Base64 per RFC 4648.

**When the client receives this message:**
- If the ID has not been added before, decodes the Base64 data and places the image at the specified location
- Ignores the message if the ID already exists

---

###### DELETE — Delete Object
**Direction:** Client → Server → Other Clients

| Position | Field    | Type | Description                   |
| :------- | :------- | :--- | :---------------------------- |
| 4        | targetId | Text | ID of the object to delete    |

**When the client receives this message:**
- Removes the object with the specified ID from the drawing area
- Silently ignores the message if the object is not found locally

---

###### CLEAR — Clear All
**Direction:** Client → Server → All Members (Including Sender)

| Position | Field | Type | Description          |
| :------- | :---- | :--- | :------------------- |
| 4        | —     | Text | Constant value: `ALL` |

**When the client receives this message:**
- Deletes all objects from the drawing area
- Clears the local object list
- The sending client also receives this message (see Section 5)

---

###### CURSOR — Cursor Position
**Direction:** Client → Server → Other Clients

| Position | Field | Type    | Description              |
| :------- | :---- | :------ | :----------------------- |
| 4        | x     | Integer | Cursor X coordinate      |
| 5        | y     | Integer | Cursor Y coordinate      |
| 6        | color | Text    | Cursor color             |

CURSOR messages are not stored on the server. They are used for real-time position broadcasting. Throttling high-frequency mouse movements on the network is the client's responsibility.

**When the client receives this message:**
- Moves the remote cursor indicator of the relevant user to the specified position
- Creates the cursor indicator if the user was not previously visible

---

##### Messaging

---

###### CHAT — Instant Message
**Direction:** Client → Server → All Members (Including Sender)

| Position | Field   | Type | Description              |
| :------- | :------ | :--- | :----------------------- |
| 4        | message | Text | Message content (plain text) |

The server relays the message to all room members and saves it to the room history.

---

###### CHAT_HISTORY — Message History
**Direction:** Server → Client (Upon room entry)

Used to deliver past chat records to a user who has just joined the room. Message content is Base64-encoded in UTF-8 to avoid interference from separator characters such as `|`.

| Position | Field             | Type   | Description                                       |
| :------- | :---------------- | :----- | :------------------------------------------------ |
| 4        | originalSender    | Text   | Username of the original message sender           |
| 5        | base64_message    | Text   | Base64-encoded message content (UTF-8)            |
| 6        | originalTimestamp | Number | Time the message was originally sent (epoch ms)   |

**When the client receives this message:**
- Decodes the Base64 content
- Adds the message to the chat area with the original sender name and original timestamp
- The message is displayed as a historical message, not a live one

---

#### 4.5 Broadcast Rules

The server relays each message type according to a specific distribution rule.

| Command                                           | Relayed to Sender? | Stored on Server?              |
| :------------------------------------------------ | :----------------- | :----------------------------- |
| SQUARE / CIRCLE / LINE / TRIANGLE / FREEHAND      | No                 | Yes                            |
| IMAGE                                             | No                 | Yes                            |
| DELETE                                            | No                 | Yes (relevant object deleted)  |
| CLEAR                                             | Yes                | Yes (all objects deleted)      |
| CURSOR                                            | No                 | No                             |
| CHAT                                              | Yes                | Yes                            |
| USER_LIST                                         | Yes                | No                             |
| LOGIN_SUCCESS / ERROR / NAME_CHANGED / ROOM_INFO  | Target client only | —                              |

---

#### 4.6 Room Entry Flow Sequence (Snapshot)

When a client sends CREATE_ROOM or JOIN_ROOM, the server follows the sequence below. The client processes snapshot messages with this ordering in mind.

```
Step  Message Type        Client Action
----  ------------------  ------------------------------------------------
  1   ROOM_INFO           Canvas and chat cleared, all cursors removed
  2   USER_LIST           Member list updated
  3   SQUARE/CIRCLE/...   Existing drawing objects added to canvas one by one
  4   IMAGE               Existing images added to canvas one by one
  5   CHAT_HISTORY        Past messages added to chat area one by one
```

The number of messages in steps 3, 4, and 5 can be zero or more depending on the room's current content. The client must check each object by its ID to avoid adding the same object twice.

---

#### 4.7 Data Types and Constraints

| Data Type   | Description                                                             |
| :---------- | :---------------------------------------------------------------------- |
| Text        | UTF-8 encoded string; must not contain pipe or newline characters       |
| Integer     | Non-decimal number within signed 32-bit integer range                   |
| Number (long)| Signed 64-bit integer; used for Unix epoch millisecond timestamps      |
| Boolean     | Only `true` or `false`                                                  |
| Base64      | RFC 4648 standard; standard alphabet, including padding character       |

| Constant         | Format                                | Example       |
| :--------------- | :------------------------------------ | :------------ |
| Room Code        | 6-character uppercase letters & digits | `ABC123`     |
| Color            | 7-character hex color code            | `#FF0000`     |
| Object ID        | Universally unique identifier (UUID)  | `a1b2c3d4...` |
| MESSAGE_ID       | First 8 characters of a UUID         | `a1b2c3d4`    |

When a pipe character (`|`) must be used in message content, the relevant field must be Base64-encoded. For this reason, message content in CHAT_HISTORY messages is mandatorily Base64-encoded.

---

<a name="features-en"></a>
### 5. Key Features
*   **Dynamic Triangle Logic:** Intelligently calculates vertex orientation based on drag direction (Up/Down).
*   **Unicode Emoji UI:** Modernized toolbar using `Segoe UI Emoji` for an intuitive user experience.
*   **Selection & Cut:** Advanced logic to select multiple items on the canvas and remove them globally.
*   **Persistent Themes:** Fully customizable UI colors that stay saved between sessions via `theme.properties`.
*   **Real-time Cursor Tracking:** See exactly where other collaborators are pointing in real-time with their unique colors and names.

<a name="install-en"></a>
### 6. Installation & Usage
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/yourusername/PaiCollab.git
    ```
2.  **Compile all modules:**
    ```bash
    javac -d bin --source-path src src/server/CollabServer.java src/uiframe/MainFrame.java
    ```
3.  **Run Server:**
    ```bash
    java -cp bin server.CollabServer
    ```
4.  **Run Client:**
    ```bash
    java -cp bin uiframe.MainFrame
    ```

---

## gRPC + RabbitMQ Version (New)

This repository now includes an alternative transport layer based on **gRPC** (HTTP/2 + Protobuf) and **RabbitMQ**
for room fan-out broadcasting (publish/subscribe). This is aligned with the recommended patterns from
[gRPC](https://grpc.io/) and [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials).

### Prerequisites
- **JDK 21+**
- **Apache Maven 3.9+**
- **RabbitMQ** running locally (default `localhost:5672`, user/pass `guest/guest`)

### 1) Start RabbitMQ
Use any RabbitMQ installation. If you prefer Docker:

```bash
docker run --rm -it -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

Management UI: `http://localhost:15672` (guest/guest)

### 2) Build (generates gRPC stubs from `.proto`)

```bash
mvn -DskipTests package
```

### 3) Run gRPC Server
Default port is **50051** (override with `.env` `GRPC_PORT`).

```bash
mvn -DskipTests exec:java -Dexec.mainClass=grpcserver.GrpcServerMain
```

Or run the compiled classes:

```bash
java -cp target/classes;target/dependency/* grpcserver.GrpcServerMain
```

### 4) Run Swing Client (gRPC)
In the login screen, enter server as `localhost:50051` (or your `GRPC_PORT`).

```bash
mvn -DskipTests exec:java -Dexec.mainClass=uiframe.MainFrame
```

### Environment variables (`.env`)
Supported keys (optional):
- `GRPC_PORT` (default 50051)
- `RABBIT_HOST` (default localhost)
- `RABBIT_PORT` (default 5672)
- `RABBIT_USER` / `RABBIT_PASS` (default guest/guest)
- `RABBIT_VHOST` (default `/`)

---

<a name="türkçe"></a>
## 🇹🇷 Türkçe Dokümantasyon

<a name="intro-tr"></a>
### 1. Giriş
**PaiCollab**, kullanıcıların paylaşılan dijital bir tuval üzerinde toplu halde sanat yapmalarına olanak tanıyan gelişmiş bir çok kullanıcılı çizim uygulamasıdır. Basit boyama uygulamalarının aksine PaiCollab, her fırça darbesini, şekli ve imleç konumunu ağ üzerinden gerçek zamanlı olarak senkronize eder.

<a name="arch-tr"></a>
### 2. Mimari ve Tasarım
Proje, **Merkezi Sunucu-İstemci Modeli** üzerine kurulmuştur:
*   **Çok İş Parçacıklı Sunucu:** Bağlantıları `Thread-per-Client` yaklaşımıyla yönetir. Benzersiz oda kodları aracılığıyla özel oturumlar (Odalar) oluşturulmasını sağlar.
*   **Durum Senkronizasyonu:** Sunucu, tuvalin bir "Ana Kaydını" tutar. Yeni bir kullanıcı katıldığında sunucu, yeni gelenin herkesin gördüğünü görmesini sağlamak için tüm çizim geçmişini gönderir.
*   **Çift Arabellekli İşleme:** Özel `DrawingCanvas`, hızlı güncellemeler sırasında titremeyi önlemek için Java AWT/Swing çift arabelleğe alma (double-buffering) tekniğini kullanır.
*   **Kalıcılık Katmanı:** Temalar Java Properties API kullanılarak `theme.properties` dosyasında, çizimler ise `saved_canvases` dizininde saklanır.

<a name="proto-tr"></a>
### 3. İletişim Protokolü (PaiProtocol)
Uygulama, yüksek hızlı ve hafif bir boru işaretli (`|`) dize protokolü kullanır:

| Komut | Format | Açıklama |
| :--- | :--- | :--- |
| `JOIN` | `JOIN\|OdaKodu\|Kullanıcı` | Kullanıcı bir odaya katılmaya çalışır. |
| `DRAW` | `DRAW\|Kul\|Tip\|X\|Y\|...` | Yeni bir şekli (Dikdörtgen, Elips, Üçgen vb.) yayınlar. |
| `CURSOR`| `CURSOR\|Kul\|X\|Y\|Renk` | Uzaktaki imleçlerin gerçek zamanlı takibi. |
| `DELETE`| `DELETE\|Kul\|ID` | Belirli bir öğeyi tüm tuvallerden siler. |
| `CLEAR` | `CLEAR\|Kul` | Herkes için tüm tuvali temizler. |

---

<a name="pcmp-tr"></a>
### 4. PCMP/2.1 Tam Protokol Spesifikasyonu

```
PaiCollab Messaging Protocol                                  PCMP/2.1
Kategori: Uygulama Katmanı Protokolü                       Mayıs 2026
```

#### Özet

Bu belge, PaiCollab çizim ve mesajlaşma uygulamasında sunucu ile istemciler arasındaki iletişimi tanımlar. Herhangi bir programlama dili veya platformda bu belgeye uygun bir istemci oluşturabilir ve sunucu ile haberleşebilirsiniz. Belge programlama dilinden ve platformdan bağımsızdır.

---

#### İçindekiler

1. Taşıma Katmanı
2. Mesaj Yapısı (Envelope)
3. Durum Makinası (FSM)
4. Mesaj Tipleri ve Alındığında Yapılacak İşlemler
   - 4.1 Oturum Yönetimi
   - 4.2 Oda Yönetimi
   - 4.3 Çizim Komutları
   - 4.4 Mesajlaşma
5. Broadcast Kuralları
6. Oda Girişi Akış Sırası
7. Veri Tipleri ve Kısıtlamalar

---

#### 4.1 Taşıma Katmanı

PCMP, güvenilir, sıralı ve akış tabanlı iletişim sağlamak amacıyla TCP protokolü üzerine inşa edilmiştir.

| Özellik         | Değer                      |
| :-------------- | :------------------------- |
| Protokol        | TCP                        |
| Varsayılan Port | 12345                      |
| Karakter Seti   | UTF-8                      |
| Mesaj Sonu      | LF (0x0A)                  |
| Alan Ayırıcı    | Dikey Çizgi / Pipe (0x7C)  |

Her mesaj tek bir satırdan oluşur ve satır sonu karakteri (LF) ile sonlandırılır. Alıcı taraf yani sunucu, gelen veri akışını LF karakterine göre bölerek mesajları birbirinden ayırır. Gerekli kısımlardaki değerleri okuyarak/işleyerek mesajları gerekli yerlere iletir ve işler.

---

#### 4.2 Mesaj Yapısı (Envelope)

Her mesaj, dört adet sabit başlık alanı ve ardından komuta özgü veri alanlarından meydana gelmektedir. Tüm alanlar pipe karakteri ile ayrılmıştır.

```
MESAJ_KÜMESI:
  [MESSAGE_ID] | [TIMESTAMP] | [SENDER] | [COMMAND] | [DATA_1] | [DATA_2] | ...
      0               1            2           3           4           5
```

| Alan        | Konum | Tip      | Açıklama                                          |
| :---------- | :---- | :------- | :------------------------------------------------ |
| MESSAGE_ID  | 0     | Metin    | 8 karakterlik benzersiz mesaj tanımlayıcısı       |
| TIMESTAMP   | 1     | Sayı     | Unix epoch milisaniye cinsinden zaman damgası     |
| SENDER      | 2     | Metin    | Gönderenin kullanıcı adı veya `SERVER` sabit değeri |
| COMMAND     | 3     | Metin    | Büyük harflerle yazılmış komut adı                |
| DATA_N      | 4+    | Değişken | Komuta özgü parametreler                          |

Sunucudan gelen tüm mesajlarda SENDER alanı `SERVER` değerini taşır. İstemci tarafında ise gönderilen mesajlarda bu alan istemcinin kullanıcı adıdır.

---

#### 4.3 Durum Makinası (FSM)

Bir istemcinin hayat döngüsü boyunca aşağıdaki beş durumdan birinde bulunabilir:

```
         ┌──────────────┐
         │ DISCONNECTED │
         └──────┬───────┘
                │ TCP bağlantısı kurulur
                ▼
         ┌──────────────┐
         │  CONNECTED   │──── LOGIN gönderilir ────►┌────────────────┐
         └──────────────┘                           │ AUTHENTICATING │
                                                    └───────┬────────┘
                                                            │ LOGIN_SUCCESS alınır
                                                            ▼
                                               ┌────────────────────┐
                                    ┌─────────►│       LOBBY        │◄────────┐
                                    │          └──────────┬──────────┘        │
                                    │                     │ CREATE_ROOM       │
                                    │                     │ veya JOIN_ROOM    │
                                    │                     ▼                   │
                                    │          ┌────────────────────┐         │
                                    └──────────│       ROOM         │─────────┘
                              LEAVE_ROOM alınır└────────────────────┘ LEAVE_ROOM
                                              CHAT / DRAW / CURSOR işlemleri
```

| Durum          | Açıklama                                                                           |
| :------------- | :--------------------------------------------------------------------------------- |
| DISCONNECTED   | TCP bağlantısı henüz kurulmamış veya kesilmiş durumu.                              |
| CONNECTED      | TCP bağlantısı kurulmuş, kimlik doğrulaması yapılmamış durum.                      |
| AUTHENTICATING | LOGIN mesajı gönderilmiş, sunucu yanıtı bekleniyor.                                |
| LOBBY          | Kimlik doğrulaması tamamlanmış, herhangi bir odada değil.                          |
| ROOM           | Bir odaya katılmış, çizim ve mesajlaşma işlemleri aktif.                           |

---

#### 4.4 Mesaj Tipleri ve Alındığında Yapılacak İşlemler

##### Oturum Yönetimi

---

###### LOGIN
**Yön:** İstemci → Sunucu

Sunucuya kullanıcı adı ile giriş yapmak için gönderilir. Kullanıcı adı müsait ise sunucu LOGIN_SUCCESS mesajını gönderir; aksi takdirde ERROR mesajını gönderir. İstemci CONNECTED durumunda iken bu mesajı gönderir.

| Konum | Alan     | Tip   | Açıklama              |
| :---- | :------- | :---- | :-------------------- |
| 4     | username | Metin | İstenen kullanıcı adı |

**Sunucu Yanıtı:** LOGIN_SUCCESS veya ERROR

---

###### LOGIN_SUCCESS
**Yön:** Sunucu → İstemci

Giriş isteğinin başarıyla kabul edildiğinin mesajıdır.

| Konum | Alan     | Tip   | Açıklama                |
| :---- | :------- | :---- | :---------------------- |
| 4     | nickname | Metin | Onaylanan kullanıcı adı |

**İstemci, bu mesajı aldığında:**
- Onaylanan kullanıcı adını yerel olarak saklar
- LOBBY durumuna geçer ve oda seçim ekranını gösterir

---

###### ERROR
**Yön:** Sunucu → İstemci

Bir işlemin veya komutun sunucu tarafından kabul edilmediğinde gönderilir.

| Konum | Alan          | Tip   | Açıklama        |
| :---- | :------------ | :---- | :-------------- |
| 4     | error_message | Metin | Hata açıklaması |

**İstemci, bu mesajı aldığında:**
- Hata mesajını kullanıcıya gösterir
- Durum değişikliği yapmaz

---

##### Oda Yönetimi

---

###### CREATE_ROOM
**Yön:** İstemci → Sunucu

İstemci LOBBY durumunda iken yeni bir oda oluşturma isteği gönderir. Başarılı ise ROOM_INFO ardından snapshot dizisi; aksi takdirde ERROR gönderilir.

| Konum | Alan | Tip   | Açıklama              |
| :---- | :--- | :---- | :-------------------- |
| 4     | —    | Metin | Sabit değer: `NEW`    |

**Sunucu Yanıtı:** ROOM_INFO ardından snapshot dizisi veya ERROR

---

###### JOIN_ROOM
**Yön:** İstemci → Sunucu

İstemci LOBBY durumunda iken var olan bir odaya 6 karakterlik oda koduyla katılma isteği gönderir.

| Konum | Alan     | Tip   | Açıklama                   |
| :---- | :------- | :---- | :------------------------- |
| 4     | roomCode | Metin | 6 karakterlik oda kodu     |

**Sunucu Yanıtı:** ROOM_INFO ardından snapshot dizisi veya ERROR

---

###### ROOM_INFO
**Yön:** Sunucu → İstemci

Odaya giriş işleminin onaylandığını bildirir ve snapshot dizisinin başlangıcını işaret eder.

| Konum | Alan     | Tip   | Açıklama            |
| :---- | :------- | :---- | :------------------ |
| 4     | roomCode | Metin | Girilen odanın kodu |

**İstemci, bu mesajı aldığında:**
- Çizim alanını tamamen temizler
- Sohbet geçmişini temizler
- Tüm uzak imleçleri ekrandan kaldırır
- ROOM durumuna geçer
- Ardından gelen snapshot mesajlarını almaya hazırlanır
- Snapshot dizisi bittikten sonra odadaki kullanıcı listesi alınır
- Kullanıcı listesinin ardından CHAT_HISTORY mesajları alınır
- Kullanıcı mesajlaşma ve çizim yapmaya hazırdır

---

###### LEAVE_ROOM
**Yön:** İstemci → Sunucu

Bulunulan odadan ayrılma isteği gönderir. İstemci ROOM durumunda bu mesajı gönderebilir.

| Konum | Alan | Tip   | Açıklama              |
| :---- | :--- | :---- | :-------------------- |
| 4     | —    | Metin | Sabit değer: `LEAVE`  |

**Sunucu:** İstemciyi odadan çıkarır, kalan üyelere güncel USER_LIST gönderir.  
**İstemci:** LOBBY durumuna döner. Tüm çizimleri, imleçleri ve mesajları temizler.

---

###### QUIT
**Yön:** İstemci → Sunucu

Uygulamayı kapatmadan önce bağlantının düzgün sonlandırılmasını sağlar.

| Konum | Alan | Tip   | Açıklama              |
| :---- | :--- | :---- | :-------------------- |
| 4     | —    | Metin | Sabit değer: `QUIT`   |

**Sunucu:** İstemciyi odadan ve sistemden çıkarır, TCP bağlantısını kapatır.  
**İstemci:** Tüm çizimleri, imleçleri ve mesajları temizler. Bağlantıyı kapatır.

---

###### USER_LIST
**Yön:** Sunucu → Odadaki Tüm Üyeler

Odanın güncel üye listesini bildirir. Bir kullanıcı odaya her katıldığında veya ayrıldığında otomatik olarak gönderilir.

| Konum | Alan  | Tip   | Açıklama                                    |
| :---- | :---- | :---- | :------------------------------------------ |
| 4     | users | Metin | Virgülle ayrılmış kullanıcı adları listesi  |

**İstemci, bu mesajı aldığında:**
- Üye listesini günceller
- Listede artık yer almayan kullanıcıların imlecini ekrandan kaldırır

---

###### NEW_USERNAME
**Yön:** İstemci → Sunucu

Kullanıcı adını değiştirme isteği gönderir. Sadece LOBBY durumunda gönderilebilir.

| Konum | Alan    | Tip   | Açıklama                    |
| :---- | :------ | :---- | :-------------------------- |
| 4     | oldNick | Metin | Mevcut kullanıcı adı        |
| 5     | newNick | Metin | İstenen yeni kullanıcı adı  |

**Sunucu Yanıtı:** NAME_CHANGED veya ERROR

---

###### NAME_CHANGED
**Yön:** Sunucu → İstemci

Kullanıcı adı değiştirme isteğinin kabul edildiğini bildirir.

| Konum | Alan    | Tip   | Açıklama                     |
| :---- | :------ | :---- | :--------------------------- |
| 4     | newNick | Metin | Onaylanan yeni kullanıcı adı |

**İstemci, bu mesajı aldığında:**
- Yerel kullanıcı adını günceller
- Sonraki mesajlarda yeni adı kullanır
- Sunucu odadaki diğer üyelere güncel USER_LIST iletir

---

##### Çizim Komutları

Tüm çizim komutları ROOM durumundayken gönderilebilir ve alınabilir. Her çizim nesnesi istemci tarafında üretilen ve ağ genelinde benzersiz olan bir UUID taşır. Tüm renk değerleri `#RRGGBB` biçiminde yedi karakterlik onaltılık sayı dizisidir.

---

###### SQUARE — Dikdörtgen
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan   | Tip       | Açıklama                    |
| :---- | :----- | :-------- | :-------------------------- |
| 4     | x      | Tam sayı  | Sol üst köşe X koordinatı   |
| 5     | y      | Tam sayı  | Sol üst köşe Y koordinatı   |
| 6     | w      | Tam sayı  | Genişlik (piksel)           |
| 7     | h      | Tam sayı  | Yükseklik (piksel)          |
| 8     | color  | Metin     | Dolgu veya kenar rengi      |
| 9     | stroke | Tam sayı  | Kenar kalınlığı (piksel)    |
| 10    | filled | Mantıksal | Dolu mu? (`true` / `false`) |
| 11    | id     | Metin     | Nesnenin benzersiz kimliği  |

**İstemci, bu mesajı aldığında:**
- Kimliği daha önce eklenmemiş ise dikdörtgeni çizim alanına ekler
- Kimlik zaten varsa mesajı yok sayar (mükerrer koruma)
- Nesneyi yerel listeye ekler

---

###### CIRCLE — Elips
**Yön:** İstemci → Sunucu → Diğer İstemciler

Alan yapısı SQUARE ile özdeştir; nesne dikdörtgen sınırlayıcı kutu içine sığan elips olarak çizilir.

| Konum | Alan   | Tip       | Açıklama                              |
| :---- | :----- | :-------- | :------------------------------------ |
| 4     | x      | Tam sayı  | Sınırlayıcı kutunun sol üst köşesi X  |
| 5     | y      | Tam sayı  | Sınırlayıcı kutunun sol üst köşesi Y  |
| 6     | w      | Tam sayı  | Genişlik (piksel)                     |
| 7     | h      | Tam sayı  | Yükseklik (piksel)                    |
| 8     | color  | Metin     | Dolgu veya kenar rengi                |
| 9     | stroke | Tam sayı  | Kenar kalınlığı (piksel)              |
| 10    | filled | Mantıksal | Dolu mu? (`true` / `false`)           |
| 11    | id     | Metin     | Nesnenin benzersiz kimliği            |

**İstemci, bu mesajı aldığında:** SQUARE ile aynı işlem uygulanır.

---

###### LINE — Çizgi
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan   | Tip      | Açıklama                    |
| :---- | :----- | :------- | :-------------------------- |
| 4     | x1     | Tam sayı | Başlangıç noktası X         |
| 5     | y1     | Tam sayı | Başlangıç noktası Y         |
| 6     | x2     | Tam sayı | Bitiş noktası X             |
| 7     | y2     | Tam sayı | Bitiş noktası Y             |
| 8     | color  | Metin    | Çizgi rengi                 |
| 9     | stroke | Tam sayı | Çizgi kalınlığı (piksel)    |
| 10    | id     | Metin    | Nesnenin benzersiz kimliği  |

**İstemci, bu mesajı aldığında:** SQUARE ile aynı mükerrer koruma uygulanır.

---

###### TRIANGLE — Üçgen
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan   | Tip       | Açıklama                    |
| :---- | :----- | :-------- | :-------------------------- |
| 4     | x1     | Tam sayı  | 1. köşe X                   |
| 5     | y1     | Tam sayı  | 1. köşe Y                   |
| 6     | x2     | Tam sayı  | 2. köşe X                   |
| 7     | y2     | Tam sayı  | 2. köşe Y                   |
| 8     | x3     | Tam sayı  | 3. köşe X                   |
| 9     | y3     | Tam sayı  | 3. köşe Y                   |
| 10    | color  | Metin     | Dolgu veya kenar rengi      |
| 11    | stroke | Tam sayı  | Kenar kalınlığı (piksel)    |
| 12    | filled | Mantıksal | Dolu mu? (`true` / `false`) |
| 13    | id     | Metin     | Nesnenin benzersiz kimliği  |

**İstemci, bu mesajı aldığında:** SQUARE ile aynı mükerrer koruma uygulanır.

---

###### FREEHAND — Serbest Çizim
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan     | Tip      | Açıklama                                   |
| :---- | :------- | :------- | :----------------------------------------- |
| 4     | x_points | Metin    | Virgülle ayrılmış X koordinatları dizisi   |
| 5     | y_points | Metin    | Virgülle ayrılmış Y koordinatları dizisi   |
| 6     | color    | Metin    | Çizgi rengi                                |
| 7     | stroke   | Tam sayı | Çizgi kalınlığı (piksel)                   |
| 8     | id       | Metin    | Nesnenin benzersiz kimliği                 |

x_points ve y_points dizileri aynı uzunlukta olmalı; her indeks bir koordinat çiftini temsil eder.

**İstemci, bu mesajı aldığında:** SQUARE ile aynı mükerrer koruma uygulanır.

---

###### IMAGE — Yapıştırılan Görsel
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan        | Tip      | Açıklama                                  |
| :---- | :---------- | :------- | :---------------------------------------- |
| 4     | x           | Tam sayı | Görselin sol üst köşesi X                 |
| 5     | y           | Tam sayı | Görselin sol üst köşesi Y                 |
| 6     | w           | Tam sayı | Genişlik (piksel)                         |
| 7     | h           | Tam sayı | Yükseklik (piksel)                        |
| 8     | base64_data | Metin    | PNG verisinin Base64 kodlanmış hali       |
| 9     | id          | Metin    | Nesnenin benzersiz kimliği                |

Görsel verisi RFC 4648 standardına uygun Base64 ile kodlanır.

**İstemci, bu mesajı aldığında:**
- Kimliği daha önce eklenmemiş ise Base64 verisini çözer ve görseli belirtilen konumda çizim alanına yerleştirir
- Kimlik zaten varsa mesajı yok sayar

---

###### DELETE — Nesne Sil
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan     | Tip   | Açıklama                   |
| :---- | :------- | :---- | :------------------------- |
| 4     | targetId | Metin | Silinecek nesnenin kimliği |

**İstemci, bu mesajı aldığında:**
- Belirtilen kimliğe sahip nesneyi çizim alanından kaldırır
- Nesne yerel listede bulunamazsa mesaj sessizce yok sayılır

---

###### CLEAR — Tümünü Temizle
**Yön:** İstemci → Sunucu → Tüm Üyeler (Gönderen Dahil)

| Konum | Alan | Tip   | Açıklama             |
| :---- | :--- | :---- | :------------------- |
| 4     | —    | Metin | Sabit değer: `ALL`   |

**İstemci, bu mesajı aldığında:**
- Çizim alanındaki tüm nesneleri siler
- Yerel nesne listesini temizler
- Gönderen istemci de bu mesajı alır (bkz. Bölüm 5)

---

###### CURSOR — İmleç Konumu
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan  | Tip      | Açıklama                    |
| :---- | :---- | :------- | :-------------------------- |
| 4     | x     | Tam sayı | İmleç X koordinatı          |
| 5     | y     | Tam sayı | İmleç Y koordinatı          |
| 6     | color | Metin    | İmleç rengi                 |

CURSOR mesajları sunucuda saklanmaz. Anlık konum bilgisi aktarımı için kullanılır. Yüksek frekanslı fare hareketlerini ağda kısıtlamak istemcinin sorumluluğundadır.

**İstemci, bu mesajı aldığında:**
- İlgili kullanıcının uzak imleç göstergesini belirtilen konuma taşır
- Kullanıcı daha önce görünmüyorsa imleç göstergesini oluşturur

---

##### Mesajlaşma

---

###### CHAT — Anlık Mesaj
**Yön:** İstemci → Sunucu → Tüm Üyeler (Gönderen Dahil)

| Konum | Alan    | Tip   | Açıklama                  |
| :---- | :------ | :---- | :------------------------ |
| 4     | message | Metin | Mesaj içeriği (düz metin) |

Sunucu mesajı odadaki tüm üyelere iletir ve oda geçmişine kaydeder.

---

###### CHAT_HISTORY — Geçmiş Mesaj
**Yön:** Sunucu → İstemci (Yalnızca Oda Girişinde)

Odaya yeni katılan kullanıcıya geçmiş sohbet kayıtlarını iletmek için kullanılır. Mesaj içeriği `|` gibi ayırıcı karakterlerden etkilenmemesi için UTF-8 olarak Base64 ile kodlanmıştır.

| Konum | Alan              | Tip   | Açıklama                                      |
| :---- | :---------------- | :---- | :-------------------------------------------- |
| 4     | originalSender    | Metin | Mesajı ilk gönderen kullanıcının adı          |
| 5     | base64_message    | Metin | Mesaj içeriğinin Base64 kodlanmış hali (UTF-8)|
| 6     | originalTimestamp | Sayı  | Mesajın ilk gönderildiği zaman (epoch ms)     |

**İstemci, bu mesajı aldığında:**
- Base64 kodlu içeriği çözer
- Mesajı özgün gönderici adı ve özgün zaman damgasıyla sohbet alanına ekler
- Mesaj, anlık değil geçmiş mesaj olarak işaretlenerek görüntülenir

---

#### 4.5 Broadcast Kuralları

Sunucu her mesaj tipini belirli bir dağıtım kuralına göre iletir.

| Komut                                             | Gönderene İletilir mi? | Sunucuda Saklanır mı?         |
| :------------------------------------------------ | :--------------------- | :---------------------------- |
| SQUARE / CIRCLE / LINE / TRIANGLE / FREEHAND      | Hayır                  | Evet                          |
| IMAGE                                             | Hayır                  | Evet                          |
| DELETE                                            | Hayır                  | Evet (ilgili nesne silinir)   |
| CLEAR                                             | Evet                   | Evet (tüm nesneler silinir)   |
| CURSOR                                            | Hayır                  | Hayır                         |
| CHAT                                              | Evet                   | Evet                          |
| USER_LIST                                         | Evet                   | Hayır                         |
| LOGIN_SUCCESS / ERROR / NAME_CHANGED / ROOM_INFO  | Yalnızca hedef istemci | —                             |

---

#### 4.6 Oda Girişi Akış Sırası (Snapshot)

Bir istemci CREATE_ROOM veya JOIN_ROOM gönderdiğinde sunucu aşağıdaki sırayı izler.

```
Adım  Mesaj Tipi          İstemci Eylemi
----  ------------------  ------------------------------------------------
  1   ROOM_INFO           Canvas ve sohbet temizlenir, tüm imleçler silinir
  2   USER_LIST           Üye listesi güncellenir
  3   SQUARE/CIRCLE/...   Mevcut çizim nesneleri birer birer canvas'a eklenir
  4   IMAGE               Mevcut görseller birer birer canvas'a eklenir
  5   CHAT_HISTORY        Geçmiş mesajlar birer birer sohbet alanına eklenir
```

3, 4 ve 5. adımlardaki mesaj sayısı odanın mevcut içeriğine göre sıfır veya daha fazla olabilir. İstemci her nesneyi kimliğine göre kontrol ederek aynı nesneyi iki kez eklememelidir.

---

#### 4.7 Veri Tipleri ve Kısıtlamalar

| Veri Tipi    | Açıklama                                                              |
| :----------- | :-------------------------------------------------------------------- |
| Metin        | UTF-8 kodlu karakter dizisi; pipe ve satır sonu karakteri içeremez    |
| Tam sayı     | İşaretli 32-bit tamsayı aralığında ondalıksız sayı                    |
| Sayı (long)  | İşaretli 64-bit tamsayı; Unix epoch milisaniye zaman damgaları için   |
| Mantıksal    | Yalnızca `true` veya `false` değerini alır                            |
| Base64       | RFC 4648 standardı; standart alfabe, dolgu karakteri dahil            |

| Sabit            | Biçim                                 | Örnek         |
| :--------------- | :------------------------------------ | :------------ |
| Oda Kodu         | 6 karakterlik büyük harf ve rakam     | `ABC123`      |
| Renk             | 7 karakterlik onaltılık renk kodu     | `#FF0000`     |
| Nesne Kimliği    | Evrensel benzersiz tanımlayıcı (UUID) | `a1b2c3d4...` |
| MESSAGE_ID       | UUID'nin ilk 8 karakteri              | `a1b2c3d4`    |

Mesaj içeriğinde pipe karakteri (`|`) kullanılması gerektiğinde ilgili alan Base64 ile kodlanmalıdır. Bu nedenle CHAT_HISTORY mesajlarında mesaj içeriği zorunlu olarak Base64 kodludur.

---

<a name="features-tr"></a>
### 5. Temel Özellikler
*   **Dinamik Üçgen Mantığı:** Sürükleme yönüne (Yukarı/Aşağı) göre köşe yönelimini akıllıca hesaplar.
*   **Unicode Emoji UI:** Sezgisel bir kullanıcı deneyimi için `Segoe UI Emoji` kullanan modernize edilmiş araç çubuğu.
*   **Seçme ve Kesme:** Tuval üzerindeki birden fazla öğeyi seçip küresel olarak kaldırma mantığı.
*   **Kalıcı Temalar:** Oturumlar arasında kayıtlı kalan, tamamen özelleştirilebilir UI renkleri.
*   **Gerçek Zamanlı İmleç Takibi:** Diğer kullanıcıların nereyi işaret ettiğini, isimleri ve kendilerine özel renkleri ile anlık olarak görün.

<a name="install-tr"></a>
### 6. Kurulum ve Kullanım
1.  **Projeyi klonlayın:**
    ```bash
    git clone https://github.com/kullaniciadin/PaiCollab.git
    ```
2.  **Tüm modülleri derleyin:**
    ```bash
    javac -d bin --source-path src src/server/CollabServer.java src/uiframe/MainFrame.java
    ```
3.  **Sunucuyu Başlatın:**
    ```bash
    java -cp bin server.CollabServer
    ```
4.  **İstemciyi Başlatın:**
    ```bash
    java -cp bin uiframe.MainFrame
    ```

---

### 📦 Requirements / Gereksinimler
*   **Java Development Kit (JDK) 21** or higher / veya üzeri.
*   **Network:** Local or Global network with TCP access (Default Port: 12345) / Yerel veya küresel ağ, TCP erişimi (Varsayılan Port: 12345).
