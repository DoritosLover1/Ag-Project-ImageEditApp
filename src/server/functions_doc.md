# PaiCollab Fonksiyonel Modül Dökümantasyonu

Bu döküman, sistemdeki temel sınıfların ve metodların teknik sorumluluklarını açıklar.

---

## 1. Sunucu Tarafı (Server Side)

### 1.1. `CollabServer`
Sunucunun ana giriş noktası ve ağ yöneticisidir.
- `start()`: Sunucuyu başlatır, `.env` dosyasını okur ve NIO Selector döngüsünü çalıştırır.
- `handleAccept()`: Yeni gelen bağlantıları kabul eder ve `ClientHandler` oluşturur.
- `handleRead() / handleWrite()`: Selector üzerinden gelen okuma/yazma olaylarını ilgili handler'a yönlendirir.

### 1.2. `ClientHandler`
Her bir istemci için oluşturulan iletişim köprüsüdür.
- `handleRawMessage()`: Gelen PCMP paketlerini komut bazlı ayrıştırır (Login, Join, Draw vb.).
- `send(String msg)`: Mesajı asenkron yazma kuyruğuna (`writeQueue`) ekler ve `OP_WRITE` kaydı yapar.
- `handleWrite()`: Kanal müsait olduğunda kuyruktaki verileri sokete boşaltır.

### 1.3. `Room` & `RoomManager`
- `Room`: Odadaki çizimlerin (`canvasItems`) ve mesajların (`chatMessages`) listesini tutar. Her değişiklikte `saveToFile()` ile diske yazar.
- `RoomManager`: Oda kodlarını üretir, odaları bellekte tutar ve boşalan odaları temizler.

---

## 2. İstemci Tarafı (Client Side)

### 2.1. `ClientNetworkManager`
İstemcinin ağ katmanıdır.
- `connect()`: Sunucuya TCP bağlantısı açar ve bir dinleme thread'i başlatır.
- `handleMessage()`: Sunucudan gelen paketleri UI bileşenlerine (Canvas, Chat) dağıtır.
- `sendShape() / sendChat()`: UI'daki eylemleri PCMP paketine çevirip sunucuya iletir.

### 2.2. `DrawingCanvas`
Çizimlerin yapıldığı ana bileşendir.
- `paintComponent()`: Tüm `CanvasItem` listesini ve uzak cursor'ları ekrana çizer.
- `addRemoteShape() / addRemoteImage()`: Dışarıdan gelen verileri ID kontrolü yaparak listeye ekler.
- `clearCanvas()`: Ekranı ve yerel geçmişi temizler.

### 2.3. `ChatPanel`
- `receiveMessage()`: Yeni gelen mesajları listeye ekler ve ekranda görüntüler.
- `sendMessage()`: Kullanıcının yazdığı metni hem yerel listeye ekler hem de ağ üzerinden gönderir.
- `updateTheme()`: Dinamik tema değişikliklerini tüm mesaj geçmişine anında uygular.
