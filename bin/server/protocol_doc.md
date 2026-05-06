# RFC 001: PaiCollab Messaging Protocol (PCMP)

**Status:** Final  
**Version:** 1.2  
**Transport:** TCP/IP  
**Encoding:** UTF-8  

---

## 1. Introduction
The PaiCollab Messaging Protocol (PCMP) is a lightweight, text-based, pipe-delimited protocol designed for real-time collaborative drawing applications. It is language-agnostic, allowing clients written in different languages (Java, Python, C++, etc.) to interact seamlessly.

## 2. Transport and Framing
- **Protocol:** TCP
- **Default Port:** 12345
- **Message Boundary:** Each message MUST end with a newline character (`\n`).
- **Character Set:** UTF-8

## 3. Message Structure
All messages follow a strict pipe-delimited format:
`MESSAGE_ID | TIMESTAMP | SENDER | COMMAND | [DATA_FIELDS...]`

- **MESSAGE_ID**: A unique 8-character identifier for the message (e.g., UUID snippet).
- **TIMESTAMP**: Unix timestamp in milliseconds.
- **SENDER**: The nickname of the user or `SERVER`.
- **COMMAND**: A case-sensitive string identifying the action.
- **DATA_FIELDS**: Zero or more fields specific to the command, separated by `|`.

## 4. Connection Lifecycle
1. **Handshake:** Client connects via TCP and sends a `LOGIN` command.
2. **Session:** Client can create or join rooms.
3. **State Sync:** Upon joining a room, the server sends all existing canvas objects.
4. **Termination:** Client sends `LEAVE_ROOM` or closes the connection.

---

## 5. Command Reference

### 5.1. Session Management

| Command | Direction | Data Format | Description |
| :--- | :--- | :--- | :--- |
| `LOGIN` | C -> S | `Nickname` | Requests to register a nickname on the server. |
| `CREATE_ROOM` | C -> S | `NEW` | Requests creation of a new drawing room. |
| `JOIN_ROOM` | C -> S | `RoomCode` | Requests to join an existing room via 4-digit code. |
| `LEAVE_ROOM` | C -> S | `LEAVE` | Notifies server the client is leaving the current room. |
| `ROOM_INFO` | S -> C | `RoomCode` | Confirmation of room entry/creation. |
| `USER_LIST` | S -> C | `User1,User2,...` | List of all active users in the current room. |
| `ERROR` | S -> C | `ErrorMessage` | Informs client of a failure (e.g., "Nickname taken"). |

### 5.2. Drawing Commands

Data fields for shapes follow specific patterns. Colors are typically Hex strings (e.g., `#FF0000`).

| Command | Data Format | Description |
| :--- | :--- | :--- |
| `SQUARE` | `X\|Y\|W\|H\|Color\|Stroke\|Filled\|ID` | Draws a rectangle at (X,Y). |
| `CIRCLE` | `X\|Y\|W\|H\|Color\|Stroke\|Filled\|ID` | Draws an ellipse inside the (X,Y,W,H) bounds. |
| `LINE` | `X1\|Y1\|X2\|Y2\|Color\|Stroke\|ID` | Draws a line from (X1,Y1) to (X2,Y2). |
| `FREEHAND` | `Xs\|Ys\|Color\|Stroke\|ID` | `Xs` and `Ys` are comma-separated coordinate lists. |
| `TEXT` | `X\|Y\|Content\|Color\|ID` | Renders text at (X,Y). |
| `IMAGE` | `X\|Y\|W\|H\|Base64\|ID` | Renders a Base64 encoded image at (X,Y). |
| `DELETE` | `TargetID` | Removes the object with the specified `ID`. |
| `CLEAR` | `ALL` | Wipes the entire canvas for everyone in the room. |

### 5.3. Synchronization

| Command | Direction | Data Format | Description |
| :--- | :--- | :--- | :--- |
| `CURSOR` | C -> S -> C | `X\|Y\|Color` | Broadcasts user's mouse position to others. |

---

## 6. Implementation Notes

### 6.1. Coordinate System
- Origin `(0,0)` is at the top-left corner of the canvas.
- All coordinates and dimensions are integers.

### 6.2. Object Persistence
- The server maintains a list of objects per room.
- When a new client joins, the server iterates through the room's history and sends individual shape commands (e.g., `SQUARE`, `LINE`) to the new client to reconstruct the canvas state.

### 6.3. Conflict Resolution
- The protocol relies on "Last Write Wins" for state updates.
- Unique `ID`s for shapes allow for non-destructive edits and specific deletions across multiple clients.

---

## 7. Example Message
`a1b2c3d4|1625083200000|Alice|SQUARE|100|150|50|50|#00FF00|2|true|shape_99`

- **ID:** `a1b2c3d4`
- **Time:** `1625083200000`
- **User:** `Alice`
- **Action:** Draw a Green Filled Square at (100, 150) with size 50x50 and ID `shape_99`.
