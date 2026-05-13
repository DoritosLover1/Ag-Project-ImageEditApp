# PaiCollab Ağ Mimarisi ve İletişim Altyapısı (Teknik Rapor)

PaiCollab, yüksek performanslı, dilden bağımsız ve gerçek zamanlı iş birliğine dayalı bir çizim platformudur. Bu rapor, sistemin temelindeki ağ mimarisini ve veri akışını detaylandırmaktadır.

---

## 1. Mimari Genel Bakış

Sistem, **Java NIO (Non-blocking I/O)** kütüphanesi üzerine inşa edilmiş, olay tabanlı (Event-Driven) bir mimari kullanır. Geleneksel "her istemciye bir thread" yaklaşımı yerine, tek bir **Selector** thread'i üzerinden binlerce eş zamanlı bağlantıyı yönetebilir.

### 1.1. NIO Selector Yapısı
Sunucu (`CollabServer`), işletim sistemi seviyesindeki soket olaylarını (`ACCEPT`, `READ`, `WRITE`) izler. Bu sayede işlemci, veri gelmesini bekleyerek (blocking) vakit kaybetmez; sadece veri hazır olduğunda ilgili fonksiyonu tetikler.

---

## 2. Gelişmiş İletişim Mekanizmaları

### 2.1. Non-Blocking Write Queue (Yazma Kuyruğu)
Büyük verilerin (özellikle Base64 resimler) ağ üzerinden gönderilmesi sırasında soket tamponu dolabilir. Bu durumu yönetmek için her istemci (`ClientHandler`) bir **Write Queue**'ya sahiptir:

1.  **Talep:** Bir mesaj gönderilmek istendiğinde, mesaj `ByteBuffer` olarak kuyruğa eklenir.
2.  **Kayıt:** Kanal için Selector üzerinde `OP_WRITE` (Yazmaya Hazır) bayrağı açılır.
3.  **Tüketim:** Selector, işletim sisteminden "Soket yazmaya müsait" bilgisini aldığında kuyruktaki veriyi parçalar halinde gönderir.
4.  **Tamamlama:** Kuyruk boşaldığında `OP_WRITE` bayrağı kapatılır.

Bu yapı, bir istemcinin yavaş internetinin veya büyük bir resim transferinin tüm sunucuyu kilitlemesini engeller.

### 2.2. Dilden Bağımsız Protokol (PCMP)
Sistem, binary serileştirme (Java Serialization) yerine **PCMP (PaiCollab Messaging Protocol)** adını verdiğimiz metin tabanlı bir protokol kullanır.
- **Pipe Delimited:** Veriler `|` karakteri ile ayrılır.
- **Base64 Encoding:** İmaj verileri ve özel karakter içerebilecek chat mesajları Base64 ile güvenli hale getirilir.
- **UTF-8:** Tüm iletişim dünya standartlarında karakter setini kullanır.

---

## 3. Veri Akışı ve Senkronizasyon

### 3.1. Oda ve Kalıcılık (Persistence)
Odalar (`Room`), sunucu tarafında hem bellekte hem de diskte yaşar.
- **Disk Kaydı:** Her oda değişikliği (yeni çizim, silme, mesaj) anında `saved_canvases/{roomCode}.canvas` dosyasına kaydedilir.
- **Lazy Loading:** Bir kullanıcı olmayan bir odaya girmeye çalıştığında, `RoomManager` önce diskteki dosyaları kontrol eder ve oda varsa otomatik olarak "uyandırır".

### 3.2. Snapshot ve State Reconstitution
Yeni bir kullanıcı odaya girdiğinde (Join Room), sunucu mevcut durumu şu adımlarla yeniden inşa eder:
1.  **Clear:** İstemciye `ROOM_INFO` gönderilerek mevcut ekranı temizlenir.
2.  **Sync:** Sunucu, bellekteki `CanvasItem` listesini tek tek PCMP paketlerine dönüştürerek istemciye yollar.
3.  **Chat Recovery:** Geçmiş mesajlar `CHAT_HISTORY` komutuyla tarih sırasına göre iletilir.

---

## 4. Güvenlik ve Hata Yönetimi

- **Mükerrer Kayıt:** İstemci, her objeye benzersiz bir UUID atar. `DrawingCanvas`, gelen paketlerin ID'sini kontrol ederek aynı objenin iki kez eklenmesini (duplication) engeller.
- **Nickname Doğrulama:** Sunucu, giriş anında `activeClients` listesini kontrol ederek aynı ismin alınmasına izin vermez.
- **Zaman Damgası (Timestamp):** Her paket bir zaman damgası taşır; bu sayede mesajlar ve çizimler her zaman doğru kronolojik sırayla işlenir.
