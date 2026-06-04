PaiCollab gRPC Messaging Protocol                             PCMP-gRPC/1.0
Kategori: Uygulama Katmanı Protokolü                       Haziran 2026

---

# PaiCollab gRPC Messaging Protocol Sürüm 1.0

## Özet

Bu belge, PaiCollab çizim ve mesajlaşma uygulamasında sunucu ile istemciler arasındaki iletişimin gRPC (Google Remote Procedure Call) ve Protocol Buffers (Protobuf) kullanılarak nasıl yapıldığını tanımlar. Önceki TCP soket tabanlı düz metin (PCMP/2.1) protokolünün yerine, daha tip güvenli (type-safe), hızlı ve modern bir yapı sunmak amacıyla geliştirilmiştir.

---

## İçindekiler

1. Taşıma Katmanı
2. Servis ve Mesaj Yapısı (Zarf - Envelope)
3. Durum Makinası (FSM)
4. Mesaj Tipleri ve Alındığında Yapılacak İşlemler (RPC'ler)
   - 4.1 Oturum Yönetimi
   - 4.2 Oda Yönetimi
   - 4.3 Çizim Komutları (ShapeEvent)
   - 4.4 Mesajlaşma (ChatEvent)
5. Broadcast Kuralları
6. Oda Girişi Akış Sırası
7. Veri Tipleri ve Kısıtlamalar

---

## 1. Taşıma Katmanı

PCMP-gRPC, güvenilir, sıralı ve akış tabanlı iletişim sağlamak amacıyla HTTP/2 destekli gRPC üzerine inşa edilmiştir.

| Özellik        | Değer                      |
| :------------- | :------------------------- |
| İletişim       | HTTP/2 (gRPC)              |
| Serileştirme   | Protocol Buffers (proto3)  |
| Varsayılan Port| Sunucu ayarlarına bağlı    |
| Metin Kodlaması| UTF-8                      |

Eski sistemdeki karakter setleri, mesaj sonu karakteri (LF), alan ayırıcı karakteri (`|`) gibi kısıtlamalar gRPC kullanımı ile tamamen ortadan kalkmıştır. Veriler Protobuf mesajları (message) olarak ikili (binary) formatta ağ üzerinden iletilir.

---

## 2. Servis ve Mesaj Yapısı (Zarf - Envelope)

Tüm mesajlaşma altyapısı `PaiCollabService` gRPC servisi üzerinden sağlanır. Odadaki anlık çizim komutları ve mesajlaşmalar `Event` adlı ana mesaj yapısı içinde (`Event Envelope`) taşınır.

**`Event` Mesajı Alanları:**

| Alan Adı (Field) | Tip (Type) | Açıklama |
| :--------------- | :--------- | :------- |
| `room_code`      | `string`   | İşlemin yapıldığı odanın kodu |
| `timestamp_ms`   | `int64`    | Unix epoch milisaniye cinsinden zaman damgası |
| `sender`         | `string`   | Gönderenin kullanıcı adı veya `SERVER` sabit değeri |
| `type`           | `EventType` (enum) | Olayın tipi (Örn: `EVENT_TYPE_SHAPE`) |
| `payload`        | `oneof`    | İlgili komuta özgü parametreleri taşıyan alt mesaj. |

Sunucudan gelen ve sunucuya giden tüm olay mesajlarında bu genel yapı kullanılır. `payload` olarak atanabilecek tipler: `shape`, `image`, `cursor`, `delete`, `clear`, `chat`, `chat_history`, `user_list`.

---

## 3. Durum Makinası (FSM)

Bir istemcinin hayat döngüsü boyunca aşağıdaki üç temel duruma indirgenmiştir:

```text
          ┌──────────────┐
          │ DISCONNECTED │
          └──────┬───────┘
                 │ gRPC Channel açılır
                 ▼
          ┌──────────────┐
          │  CONNECTED   │──── Login (RPC) gönderilir ────►┌────────────────┐
          └──────────────┘                                 │ AUTHENTICATING │
                                                           └───────┬────────┘
                                                                   │ LoginResponse (success=true) alınır
                                                                   ▼
                                                      ┌────────────────────┐
                                           ┌─────────►│       LOBBY        │◄────────┐
                                           │          └──────────┬──────────┘        │
                                           │                     │ CreateRoom        │
                                           │                     │ veya JoinRoom     │
                                           │                     ▼                   │
                                           │          ┌────────────────────┐         │
                                           └──────────│       ROOM         │─────────┘
                                     LeaveRoom alınır └────────────────────┘ LeaveRoom
                                            Subscribe() dinlenir / SendEvent() yollanır
```

| Durum          | Açıklama                                                                              |
| :------------- | :------------------------------------------------------------------------------------ |
| DISCONNECTED   | gRPC kanal bağlantısı yok.                                                            |
| CONNECTED      | gRPC bağlantısı açık, kimlik doğrulaması yapılmamış.                                  |
| AUTHENTICATING | `Login` RPC isteği gönderilmiş, sunucu yanıtı bekleniyor.                             |
| LOBBY          | Kimlik doğrulaması tamamlanmış, herhangi bir odada değil.                             |
| ROOM           | Bir odaya katılmış, canlı veri akışı (Subscribe) ve gönderme (SendEvent) aktif.       |

---

## 4. Mesaj Tipleri ve Alındığında Yapılacak İşlemler (RPC'ler)

### 4.1 Oturum Yönetimi

---

#### Login (Giriş Yapma)
**RPC Yönü:** İstemci → Sunucu (Unary)

Sunucuya kullanıcı adı ile giriş yapmak için gönderilir. İstemci CONNECTED durumunda iken bu çağrıyı yapar.

**İstek (`LoginRequest`):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `requested_nickname` | `string` | İstenen kullanıcı adı |

**Sunucu Yanıtı (`LoginResponse`):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `success` | `bool` | İşlem onaylandıysa `true`, aksi halde `false` |
| `approved_nickname` | `string` | Onaylanan kullanıcı adı |
| `error_message` | `string` | `success == false` ise hata açıklaması |

**İstemci, bu yanıtı (success=true) aldığında:**
- Onaylanan kullanıcı adını yerel olarak saklar.
- LOBBY durumuna geçer ve oda seçim ekranını gösterir.

---

#### ChangeName (İsim Değiştirme)
**RPC Yönü:** İstemci → Sunucu (Unary)

Kullanıcı adını değiştirme isteği gönderir. LOBBY veya ROOM durumunda iken gönderilebilir. Eğer kullanıcı ROOM içindeyse, başarı halinde sunucu tüm üyelere yeni isim listesini (`UserListEvent`) otomatik iletir.

**İstek (`ChangeNameRequest`):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `old_nickname` | `string` | Mevcut kullanıcı adı |
| `new_nickname` | `string` | İstenen yeni kullanıcı adı |

**Sunucu Yanıtı (`ChangeNameResponse`):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `success` | `bool` | İşlem onaylandıysa `true`, aksi halde `false` |
| `approved_nickname` | `string` | Onaylanan yeni kullanıcı adı |
| `error_message` | `string` | `success == false` ise hata açıklaması |

---

### 4.2 Oda Yönetimi

---

#### CreateRoom (Oda Kurma)
**RPC Yönü:** İstemci → Sunucu (Unary)

İstemci LOBBY durumunda iken yeni bir oda oluşturma isteği gönderir.

**İstek (`RoomCreateRequest`):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `nickname` | `string` | İsteyen kullanıcının adı |

**Sunucu Yanıtı (`RoomEnterResponse`):** Bkz. JoinRoom.

---

#### JoinRoom (Odaya Katılma)
**RPC Yönü:** İstemci → Sunucu (Unary)

İstemci LOBBY durumunda iken var olan bir odaya katılma isteği gönderir.

**İstek (`RoomJoinRequest`):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `nickname` | `string` | İsteyen kullanıcının adı |
| `room_code` | `string` | 6 karakterlik oda kodu |

**Sunucu Yanıtı (`RoomEnterResponse`):**
Eski protokoldeki `ROOM_INFO` ve peşinden gelen Snapshot dizisinin gRPC'deki tekil karşılığıdır. Tüm geçmiş veriyi tek bir mesajda içerir.

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `success` | `bool` | Giriş başarılı mı? |
| `room_code` | `string` | Girilen/Oluşturulan odanın kodu |
| `error_message` | `string` | `success == false` ise hata mesajı |
| `snapshot_events` | `repeated Event` | Geçmişte çizilen objeler ve eski sohbet mesajlarının listesi |
| `users` | `repeated string` | Odadaki anlık kullanıcı isimleri |

**İstemci, bu yanıtı (success=true) aldığında:**
- Çizim alanını tamamen temizler, sohbet geçmişini temizler, uzak imleçleri kaldırır.
- `users` listesi ile üye listesini günceller.
- `snapshot_events` dizisindeki her bir olayı (Şekil, Resim, Mesaj) canvas ve sohbete yükler.
- `Subscribe` RPC akışını açarak canlı dinlemeye başlar.

---

#### LeaveRoom (Odadan Çıkış)
**RPC Yönü:** İstemci → Sunucu (Unary)

Bulunulan odadan ayrılma isteği.

**İstek (`RoomLeaveRequest`):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `nickname` | `string` | İsteyen kullanıcının adı |
| `room_code` | `string` | Ayrılınacak oda kodu |

**Sunucu Yanıtı (`Ack`):** Başarı durumu döner.
**Sunucu:** İstemciyi odadan çıkarır, kalan üyelere güncel `USER_LIST` yayınlar.
**İstemci:** LOBBY durumuna döner. `Subscribe` stream'ini kapatır. Var olan tüm çizimleri, imleçleri ve mesajları temizler.

---

#### Subscribe (Canlı Olay Dinleme)
**RPC Yönü:** İstemci → Sunucu (Server Streaming)

İstemci odaya başarıyla katıldıktan sonra bu fonksiyonu çağırır. Dönüş olarak `stream Event` döner. İstemci bu bağlantıyı oda boyunca açık tutarak diğer istemcilerden gelen anlık olayları dinler.

**İstek (`SubscribeRequest`):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `nickname` | `string` | Dinleyici kullanıcının adı |
| `room_code` | `string` | Dinlenilen odanın kodu |

---

### 4.3 Çizim Komutları

Tüm çizim komutları `SendEvent(Event)` fonksiyonu üzerinden gönderilir. Ana `Event` objesinin `payload`'u olarak `ShapeEvent` mesajı atanır. Her çizim nesnesi ağ genelinde benzersiz olan bir kimlik değeri (UUID) taşır.

#### ShapeEvent Parametreleri 
`Event.type = EVENT_TYPE_SHAPE`

Protobuf'taki `ShapeEvent` mesajı, tüm şekil türlerini tek bir objede taşır. İlgili `shape_type` enum değerine göre alt alanlar okunmalıdır. Tüm renk değerleri `#RRGGBB` biçimindedir.

**Ortak Alanlar (Tüm Şekiller İçin):**
| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `shape_type` | `ShapeType` | Çizilecek nesnenin geometrik türü |
| `id`         | `string` | Nesnenin benzersiz kimliği (UUID) |
| `color`      | `string` | Dolgu veya kenar rengi |
| `stroke`     | `int32`  | Kenar kalınlığı (piksel) |
| `filled`     | `bool`   | Dolu mu? (`true` / `false`) |

---

#### SQUARE — Dikdörtgen
`shape_type = SHAPE_TYPE_SQUARE`

Aşağıdaki özellikler, sınırlayıcı kutuyu belirtir:

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `x` | `int32` | Sol üst köşe X koordinatı |
| `y` | `int32` | Sol üst köşe Y koordinatı |
| `w` | `int32` | Genişlik (piksel) |
| `h` | `int32` | Yükseklik (piksel) |

**İstemci, bu olayı aldığında:**
- Kimliği daha önce eklenmemiş ise dikdörtgeni çizim alanına ekler.
- Kimlik zaten varsa mesajı yok sayar (mükerrer koruma).

---

#### CIRCLE — Elips
`shape_type = SHAPE_TYPE_CIRCLE`

Alan yapısı SQUARE ile özdeştir (`x, y, w, h` alanları kullanılır). Nesne dikdörtgen sınırlayıcı kutu içine sığan elips olarak çizilir.

**İstemci, bu olayı aldığında:** SQUARE ile aynı işlem uygulanır.

---

#### LINE — Çizgi
`shape_type = SHAPE_TYPE_LINE`

Aşağıdaki özellikler kullanılır:

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `x1` | `int32` | Başlangıç noktası X |
| `y1` | `int32` | Başlangıç noktası Y |
| `x2` | `int32` | Bitiş noktası X |
| `y2` | `int32` | Bitiş noktası Y |

---

#### TRIANGLE — Üçgen
`shape_type = SHAPE_TYPE_TRIANGLE`

Aşağıdaki özellikler kullanılır:

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `tx1` | `int32` | 1. köşe X |
| `ty1` | `int32` | 1. köşe Y |
| `tx2` | `int32` | 2. köşe X |
| `ty2` | `int32` | 2. köşe Y |
| `tx3` | `int32` | 3. köşe X |
| `ty3` | `int32` | 3. köşe Y |

---

#### FREEHAND — Serbest Çizim
`shape_type = SHAPE_TYPE_FREEHAND`

Nokta dizileri aşağıdaki dinamik listelerle taşınır:

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `xs` | `repeated int32` | Tüm X koordinatlarının sıralı listesi |
| `ys` | `repeated int32` | Tüm Y koordinatlarının sıralı listesi |

**Not:** `xs` ve `ys` listeleri aynı uzunlukta olmalı, her indeks bir koordinat çiftini temsil eder.

---

### 4.4 Görsel, Nesne Silme ve Temizleme

Tüm bu komutlar `SendEvent(Event)` üzerinden iletilir.

#### IMAGE — Yapıştırılan Görsel
`Event.type = EVENT_TYPE_IMAGE`, Payload = `ImageEvent`

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `id` | `string` | Nesnenin benzersiz kimliği (UUID) |
| `x`  | `int32`  | Görselin sol üst köşesi X |
| `y`  | `int32`  | Görselin sol üst köşesi Y |
| `w`  | `int32`  | Genişlik (piksel) |
| `h`  | `int32`  | Yükseklik (piksel) |
| `png_bytes` | `bytes` | PNG verisinin ham (binary) byte dizisi |

**İstemci, bu mesajı aldığında:** PNG byte verisini işleyip ekrana basar.

---

#### DELETE — Nesne Sil
`Event.type = EVENT_TYPE_DELETE`, Payload = `DeleteEvent`

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `target_id` | `string` | Silinecek nesnenin kimliği (UUID) |

**İstemci, bu mesajı aldığında:** Belirtilen UUID'ye sahip nesneyi çizim alanından kaldırır.

---

#### CLEAR — Tümünü Temizle
`Event.type = EVENT_TYPE_CLEAR`, Payload = `ClearEvent`

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `scope` | `string` | Sabit değer: `"ALL"` |

**İstemci, bu mesajı aldığında:** Çizim alanındaki tüm nesneleri siler.

---

#### CURSOR — İmleç Konumu
`Event.type = EVENT_TYPE_CURSOR`, Payload = `CursorEvent`

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `x` | `int32` | İmleç X koordinatı |
| `y` | `int32` | İmleç Y koordinatı |
| `color` | `string` | İmleç rengi (`#RRGGBB`) |

Sunucuda saklanmaz. `Subscribe` kanalı üzerinden yüksek frekansla odadaki diğer kullanıcılara yansıtılır.

---

### 4.5 Mesajlaşma

Tüm bu komutlar `SendEvent(Event)` (veya sunucudan gelen akış) üzerinden iletilir.

#### CHAT — Anlık Mesaj
`Event.type = EVENT_TYPE_CHAT`, Payload = `ChatEvent`

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `message` | `string` | Mesaj içeriği (Düz metin, Base64 gerekmez) |

Sunucu mesajı odadaki tüm üyelere (Subscribe kanalıyla) iletir ve oda geçmişine kaydeder.

---

#### CHAT_HISTORY — Geçmiş Mesaj
`Event.type = EVENT_TYPE_CHAT_HISTORY`, Payload = `ChatHistoryEvent`

**Sadece sunucudan istemciye:** Odaya yeni girildiğinde (JoinRoom veya CreateRoom yanıtı `snapshot_events` dizisi içinde) gelir. Base64 kodlaması gerektirmez.

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `original_sender` | `string` | Mesajı asıl gönderen kullanıcı adı |
| `message` | `string` | Mesaj içeriği (Düz metin) |
| `original_timestamp_ms`| `int64` | Mesajın asıl gönderildiği Unix zaman damgası |

---

#### USER_LIST — Kullanıcı Listesi
`Event.type = EVENT_TYPE_USER_LIST`, Payload = `UserListEvent`

**Sadece sunucudan istemciye:** Odanın güncel üye listesini bildirir. Odaya giriş çıkış veya isim değiştirme işlemlerinden sonra sunucu `Subscribe` akışı üzerinden herkese yayınlar.

| Alan | Tip | Açıklama |
| :--- | :--- | :--- |
| `users` | `repeated string` | Kullanıcı isimlerinin listesi |

**İstemci, bu mesajı aldığında:** Üye listesini günceller ve listede yer almayanların imlecini ekrandan kaldırır.

---

## 5. Broadcast Kuralları

Sunucu (gRPC & RabbitMQ) olayları belirli yayınlama kurallarına göre iletir.

| Komut (Payload Tipi)                          | Gönderene İletilir mi? (Stream) | Sunucuda Saklanır mı? |
| :-------------------------------------------- | :------------------------------ | :-------------------- |
| ShapeEvent (Tüm Şekiller)                     | Evet (veya Hayır)*              | Evet                  |
| ImageEvent                                    | Evet (veya Hayır)*              | Evet                  |
| DeleteEvent                                   | Evet                            | Evet (ilgili nesne silinir) |
| ClearEvent                                    | Evet                            | Evet (tüm nesneler silinir) |
| CursorEvent                                   | Hayır                           | Hayır                 |
| ChatEvent                                     | Evet                            | Evet                  |
| UserListEvent                                 | Tüm üyelere yansır              | Hayır                 |

*(Yazar Notu: İstemci yerel anlık çizim performansı sebebiyle stream üzerinden kendi ismi ile gelen çizim mesajını yoksayabilir (Zaten kendisi çizmiştir).*

---

## 6. Oda Girişi Akış Sırası (Snapshot)

TCP protokolünde sıralı mesajların okunması yerine gRPC'de `RoomEnterResponse` içindeki özellikler okunur:

```text
Adım  Okunan Veri               İstemci Eylemi
----  ------------------------  ------------------------------------------------
  1   (Response Geldi)          Canvas ve sohbet temizlenir, tüm imleçler silinir
  2   users dizisi              Üye listesi güncellenir
  3   snapshot_events dizisi    Şekiller (SQUARE/CIRCLE vs.) birer birer eklenir
  4   snapshot_events dizisi    Görseller (IMAGE) birer birer eklenir
  5   snapshot_events dizisi    Geçmiş mesajlar (CHAT_HISTORY) sohbet alanına eklenir
  6   (Döngü Bitti)             Subscribe() başlatılarak canlı yayın dinlenir
```

---

## 7. Veri Tipleri ve Kısıtlamalar

| Protobuf Tipi | Java Karşılığı | Açıklama |
| :--- | :--- | :--- |
| `string` | `String` | UTF-8 destekli karakter dizisi. Boru (pipe) yasakları vs. yoktur. |
| `int32` | `int` | İşaretli 32-bit tamsayı aralığında sayı (Koordinat vb.) |
| `int64` | `long` | İşaretli 64-bit tamsayı; Unix epoch milisaniye zaman damgaları için |
| `bool` | `boolean` | `true` veya `false` |
| `bytes` | `ByteString` | İkili formatta (binary array) veriler. Resim png dizisi vs. |
| `enum` | `Enum` | Önceden tanımlanmış numaralandırılmış liste (`ShapeType`, `EventType`) |

Eski protokoldeki `|` ile bölmek veya mesajları `\n` ile bölmek gibi string parse işlemleri tamamen ortadan kaldırılmıştır. Ağ trafiği tamamen gRPC'nin HTTP/2 altyapısı ve Protocol Buffers'ın binary serileştirmesi ile sağlanır.