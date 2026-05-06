# PaiCollab Fonksiyonel Dokümantasyon

Bu doküman, PaiCollab projesindeki temel sınıfların ve fonksiyonların işlevlerini açıklamaktadır. Proje, gerçek zamanlı işbirlikçi bir çizim uygulamasıdır.

---

## 1. UI Modülü (`uiframe`)

### 1.1. MainFrame.java
Uygulamanın ana penceresini ve panel geçişlerini yönetir.

- `init()`: Pencereyi, CardLayout'u ve temel panelleri (Login, Lobby, Canvas) başlatır.
- `loginPanel()`: Giriş ekranını oluşturur. Sunucu IP ve Kullanıcı adı girişlerini alır.
- `lobbyPanel()`: Oda oluşturma veya odaya katılma seçeneklerinin olduğu ekranı oluşturur.
- `canvasPanel()`: Çizim alanını, araç çubuğunu ve yan menüyü (üye listesi, obje listesi) içerir.
- `setupNetworkHooks()`: Canvas üzerindeki olayları (çizim, imleç hareketi, yapıştırma) yakalayıp `ClientNetworkManager` üzerinden sunucuya gönderir.

### 1.2. DrawingCanvas.java
Çizim mantığının, yerel ve uzak objelerin işlendiği ana bileşendir.

- `setupMouseListeners()`: Fare tıklama, sürükleme ve bırakma olaylarını yakalayarak çizim araçlarını yönetir.
- `finalizeShape()`: Çizimi biten bir objeyi yerel listeye ekler ve sunucuya gönderilmesini sağlar.
- `paintComponent(Graphics g)`: Tüm çizim objelerini, uzak imleçleri ve seçim çerçevesini ekrana çizer.
- `pasteFromClipboard()`: Sistem panosundaki resmi veya uygulama içi kopyalanan objeleri tuvale yapıştırır.
- `applyCut()`: Seçili alandaki objeleri "keser" (yerelden siler ve sunucuya silme komutu gönderir).
- `updateRemoteCursor(CursorPosition cp)`: Diğer kullanıcıların fare imleçlerini günceller.
- `addRemoteShape/addRemoteImage`: Sunucudan gelen yeni objeleri tuvale ekler.

---

## 2. Network Modülü (`network`)

### 2.1. ClientNetworkManager.java
İstemci tarafındaki TCP bağlantısını ve mesaj alışverişini yönetir.

- `connect()`: Belirtilen IP ve Port üzerinden sunucuya bağlanır.
- `startListening()`: Sunucudan gelen mesajları sürekli dinleyen ayrı bir thread başlatır.
- `handleMessage(String msg)`: Gelen ham mesajı ayrıştırıp ilgili UI güncellemelerini tetikler.
- `sendShape(DrawShape shape)`: Çizilen bir şekli protokol formatına çevirip sunucuya gönderir.

### 2.2. NetworkProtocol.java
İstemci ve sunucu arasındaki mesaj formatını (PCMP) belirleyen yardımcı sınıftır.

- `buildBase(...)`: Mesaj ID, Zaman damgası ve Gönderici bilgilerini içeren temel formatı oluşturur.
- `buildLogin/buildCreateRoom/buildJoinRoom`: Oturum ve oda yönetimi mesajlarını oluşturur.
- `buildSquare/buildCircle/buildLine/...`: Çizim komutlarını pipe-delimited formatta paketler.
- `buildImage(...)`: Resim verisini Base64 formatına çevirip paketler.

---

## 3. Server Modülü (`server`)

### 3.1. CollabServer.java
Sunucunun ana giriş noktasıdır. Belirlenen portu dinler ve her yeni bağlantı için bir `ClientHandler` oluşturur.

### 3.2. ClientHandler.java
Her bir istemci ile olan iletişimi yöneten thread sınıfıdır.

- `handleRawMessage(String raw)`: İstemciden gelen ham komutları işler (Oda yönetimi, Çizim yayını).
- `broadcastToOthers(String msg)`: Gelen bir mesajı, odadaki diğer tüm kullanıcılara iletir.
- `leaveCurrentRoom()`: Kullanıcı ayrıldığında odayı günceller ve gerekirse odayı kapatır.

### 3.3. Room.java
Bir çizim odasının durumunu (üyeler, çizim geçmişi) tutar.

- `addCanvasItem(CanvasItem item)`: Odaya yapılan her çizimi "snapshot" olarak saklar.
- `getCanvasSnapshot()`: Yeni katılan birine odanın o anki halini göndermek için tüm objeleri döner.

---

## 4. Model Modülü (`models`)

- **DrawShape**: Şekillerin (tip, koordinat, renk, kalınlık) verilerini tutar.
- **PastedImage**: Yapıştırılan resimlerin verisini ve koordinatlarını tutar.
- **CanvasItem**: Tuvaldeki her bir öğeyi (Şekil veya Resim) ve kimin eklediğini temsil eder.
- **CursorPosition**: Kullanıcıların imleç koordinatlarını ve renklerini temsil eder.
