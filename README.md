# 🎨 PaiCollab: Real-Time Collaborative Drawing Platform
### *Advanced Java Swing & TCP Socket Programming Project*

---

## 📑 Table of Contents / İçindekiler
1.  [English Documentation](#english)
    *   [Introduction](#intro-en)
    *   [Architecture & Design](#arch-en)
    *   [Communication Protocol](#proto-en)
    *   [Key Features](#features-en)
    *   [Installation & Usage](#install-en)
2.  [Türkçe Dokümantasyon](#türkçe)
    *   [Giriş](#intro-tr)
    *   [Mimari ve Tasarım](#arch-tr)
    *   [İletişim Protokolü](#proto-tr)
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
| `JOIN` | `JOIN|RoomCode|Username` | User attempts to join a specific room. |
| `DRAW` | `DRAW|User|Type|X|Y|...` | Broadcasts a new shape (Rect, Circle, Triangle, etc.) |
| `CURSOR`| `CURSOR|User|X|Y|Color` | Real-time tracking of remote cursors. |
| `DELETE`| `DELETE|User|ShapeID` | Removes a specific item from all canvases. |
| `CLEAR` | `CLEAR|User` | Clears the entire canvas for everyone. |

<a name="features-en"></a>
### 4. Key Features
*   **Dynamic Triangle Logic:** Intelligently calculates vertex orientation based on drag direction (Up/Down).
*   **Unicode Emoji UI:** Modernized toolbar using `Segoe UI Emoji` for an intuitive user experience.
*   **Selection & Cut:** Advanced logic to select multiple items on the canvas and remove them globally.
*   **Persistent Themes:** Fully customizable UI colors that stay saved between sessions via `theme.properties`.
*   **Real-time Cursor Tracking:** See exactly where other collaborators are pointing in real-time with their unique colors and names.

<a name="install-en"></a>
### 5. Installation & Usage
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
| `JOIN` | `JOIN|OdaKodu|Kullanıcı` | Kullanıcı bir odaya katılmaya çalışır. |
| `DRAW` | `DRAW|Kul|Tip|X|Y|...` | Yeni bir şekli (Dikdörtgen, Elips, Üçgen vb.) yayınlar. |
| `CURSOR`| `CURSOR|Kul|X|Y|Renk` | Uzaktaki imleçlerin gerçek zamanlı takibi. |
| `DELETE`| `DELETE|Kul|ID` | Belirli bir öğeyi tüm tuvallerden siler. |
| `CLEAR` | `CLEAR|Kul` | Herkes için tüm tuvali temizler. |

<a name="features-tr"></a>
### 4. Temel Özellikler
*   **Dinamik Üçgen Mantığı:** Sürükleme yönüne (Yukarı/Aşağı) göre köşe yönelimini akıllıca hesaplar.
*   **Unicode Emoji UI:** Sezgisel bir kullanıcı deneyimi için `Segoe UI Emoji` kullanan modernize edilmiş araç çubuğu.
*   **Seçme ve Kesme:** Tuval üzerindeki birden fazla öğeyi seçip küresel olarak kaldırma mantığı.
*   **Kalıcı Temalar:** Oturumlar arasında kayıtlı kalan, tamamen özelleştirilebilir UI renkleri.
*   **Gerçek Zamanlı İmleç Takibi:** Diğer kullanıcıların nereyi işaret ettiğini, isimleri ve kendilerine özel renkleri ile anlık olarak görün.

<a name="install-tr"></a>
### 5. Kurulum ve Kullanım
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
*   **Java Development Kit (JDK) 21** or higher.
*   **Network:** Local or Global network with TCP access (Default Port: 12345).

---
