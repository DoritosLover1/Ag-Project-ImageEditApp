# PaiCollab Messaging Protocol (PCMP) v2.1 — Tam Spesifikasyon

Bu doküman, PaiCollab sunucusu ile istemciler arasındaki tüm iletişimi tanımlar.
Herhangi bir programlama dili (Java, Python, C, Go, JS…) bu dokümana uyarak
sunucuyla tam uyumlu bir istemci yazabilir.

---

## 1. Taşıma Katmanı (Transport Layer)

| Özellik | Değer |
| :--- | :--- |
| Protokol | TCP |
| Varsayılan port | `12345` |
| Karakter seti | UTF-8 |
| Mesaj sonu | `\n` (LF, 0x0A) |
| Alan ayırıcı | `\|` (pipe) |

Her mesaj **tek bir satırdır**. Mesaj sonuna `\n` eklenir.
Alıcı taraf gelen veriyi `\n` karakterine göre bölerek mesajları ayırır.

---

## 2. Mesaj Zarfı (Envelope)

Her mesaj 4 sabit başlık alanı + komuta özel veri alanlarından oluşur:

```
MESSAGE_ID|TIMESTAMP|SENDER|COMMAND|DATA_FIELD_1|DATA_FIELD_2|...
   p[0]      p[1]     p[2]   p[3]     p[4]         p[5]      ...
```

| Alan | İndeks | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| MESSAGE_ID | `p[0]` | String | 8 karakterlik UUID parçası (ör: `a1b2c3d4`) |
| TIMESTAMP | `p[1]` | long | Unix epoch milisaniye zaman damgası |
| SENDER | `p[2]` | String | Gönderenin kullanıcı adı veya `SERVER` |
| COMMAND | `p[3]` | String | Büyük harfle komut adı |
| DATA_FIELDS | `p[4+]` | ... | Komuta özel parametreler |

Mesajı parse etmek için `|` karakterine göre split yapılır.

---

## 3. Komutlar — Oturum Yönetimi

### 3.1. LOGIN
İstemci → Sunucu. Sunucuya giriş isteği.

```
p[0]|p[1]|username|LOGIN|username
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | username | String | İstenen kullanıcı adı |

Örnek: `a1b2c3d4|1715621000000|Ahmet|LOGIN|Ahmet`

Sunucu yanıtı: `LOGIN_SUCCESS` veya `ERROR`

### 3.2. LOGIN_SUCCESS
Sunucu → İstemci. Giriş başarılı.

```
p[0]|p[1]|SERVER|LOGIN_SUCCESS|nickname
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | nickname | String | Onaylanan kullanıcı adı |

### 3.3. ERROR
Sunucu → İstemci. Herhangi bir hata.

```
p[0]|p[1]|SERVER|ERROR|error_message
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | error_message | String | Hata açıklaması |

---

## 4. Komutlar — Oda Yönetimi

### 4.1. CREATE_ROOM
İstemci → Sunucu. Yeni oda oluştur.

```
p[0]|p[1]|username|CREATE_ROOM|NEW
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | — | String | Sabit değer: `NEW` |

### 4.2. JOIN_ROOM
İstemci → Sunucu. Var olan bir odaya katıl.

```
p[0]|p[1]|username|JOIN_ROOM|roomCode
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | roomCode | String | 6 karakterlik oda kodu (büyük harf) |

Sunucu yanıtı: `ROOM_INFO` + snapshot veya `ERROR`

### 4.3. ROOM_INFO
Sunucu → İstemci. Odaya giriş onayı.

```
p[0]|p[1]|SERVER|ROOM_INFO|roomCode
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | roomCode | String | Girilen odanın kodu |

**ÖNEMLİ:** İstemci bu mesajı aldığında canvas'ı ve chat'i temizlemelidir.
Ardından sunucu snapshot verilerini (çizimler + mesajlar) gönderecektir.

### 4.4. LEAVE_ROOM
İstemci → Sunucu. Odadan ayrıl.

```
p[0]|p[1]|username|LEAVE_ROOM|LEAVE
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | — | String | Sabit değer: `LEAVE` |

### 4.5. QUIT
İstemci → Sunucu. Uygulamayı tamamen kapat ve bağlantıyı kes.

```
p[0]|p[1]|username|QUIT|QUIT
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | — | String | Sabit değer: `QUIT` |

### 4.6. USER_LIST
Sunucu → Odadaki tüm üyeler. Aktif kullanıcı listesi.

```
p[0]|p[1]|SERVER|USER_LIST|user1,user2,user3
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | users | String | Virgülle ayrılmış kullanıcı adları |

Bu mesaj oda değişikliği (giriş/çıkış) olduğunda otomatik gönderilir.
İstemci bu listeye göre ayrılan kullanıcıların cursor'larını temizlemelidir.

### 4.7. NEW_USERNAME
İstemci → Sunucu. Kullanıcı adı değiştirme isteği.

```
p[0]|p[1]|oldNick|NEW_USERNAME|oldNick|newNick
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | oldNick | String | Mevcut kullanıcı adı |
| p[5] | newNick | String | İstenen yeni kullanıcı adı |

### 4.8. NAME_CHANGED
Sunucu → İstemci. İsim değişikliği onayı.

```
p[0]|p[1]|SERVER|NAME_CHANGED|newNick
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | newNick | String | Onaylanan yeni kullanıcı adı |

---

## 5. Komutlar — Çizim Verileri (Canvas)

Tüm renk değerleri `#RRGGBB` formatında Hex kodudur.
Tüm `id` değerleri istemci tarafında üretilen UUID'dir.

### 5.1. SQUARE (Dikdörtgen)

```
p[0]|p[1]|sender|SQUARE|x|y|w|h|color|stroke|filled|id
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | x | int | Sol üst köşe X |
| p[5] | y | int | Sol üst köşe Y |
| p[6] | w | int | Genişlik |
| p[7] | h | int | Yükseklik |
| p[8] | color | String | Renk (`#RRGGBB`) |
| p[9] | stroke | int | Çizgi kalınlığı |
| p[10] | filled | boolean | `true` / `false` |
| p[11] | id | String | Benzersiz obje kimliği (UUID) |

### 5.2. CIRCLE (Elips)

```
p[0]|p[1]|sender|CIRCLE|x|y|w|h|color|stroke|filled|id
```

SQUARE ile aynı alan yapısı. Elips olarak yorumlanır.

| İndeks | p[4]=x | p[5]=y | p[6]=w | p[7]=h | p[8]=color | p[9]=stroke | p[10]=filled | p[11]=id |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |

### 5.3. LINE (Çizgi)

```
p[0]|p[1]|sender|LINE|x1|y1|x2|y2|color|stroke|id
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | x1 | int | Başlangıç X |
| p[5] | y1 | int | Başlangıç Y |
| p[6] | x2 | int | Bitiş X |
| p[7] | y2 | int | Bitiş Y |
| p[8] | color | String | Renk (`#RRGGBB`) |
| p[9] | stroke | int | Çizgi kalınlığı |
| p[10] | id | String | Benzersiz obje kimliği |

### 5.4. TRIANGLE (Üçgen)

```
p[0]|p[1]|sender|TRIANGLE|x1|y1|x2|y2|x3|y3|color|stroke|filled|id
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | x1 | int | Köşe 1 X |
| p[5] | y1 | int | Köşe 1 Y |
| p[6] | x2 | int | Köşe 2 X |
| p[7] | y2 | int | Köşe 2 Y |
| p[8] | x3 | int | Köşe 3 X |
| p[9] | y3 | int | Köşe 3 Y |
| p[10] | color | String | Renk (`#RRGGBB`) |
| p[11] | stroke | int | Çizgi kalınlığı |
| p[12] | filled | boolean | `true` / `false` |
| p[13] | id | String | Benzersiz obje kimliği |

### 5.5. FREEHAND (Serbest Çizim)

```
p[0]|p[1]|sender|FREEHAND|x_points|y_points|color|stroke|id
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | x_points | String | Virgülle ayrılmış X koordinatları (ör: `10,15,20,25`) |
| p[5] | y_points | String | Virgülle ayrılmış Y koordinatları (ör: `30,35,40,45`) |
| p[6] | color | String | Renk (`#RRGGBB`) |
| p[7] | stroke | int | Çizgi kalınlığı |
| p[8] | id | String | Benzersiz obje kimliği |

### 5.6. IMAGE (Yapıştırılan Resim)

```
p[0]|p[1]|sender|IMAGE|x|y|w|h|base64_data|id
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | x | int | Resim X pozisyonu |
| p[5] | y | int | Resim Y pozisyonu |
| p[6] | w | int | Resim genişliği |
| p[7] | h | int | Resim yüksekliği |
| p[8] | base64_data | String | PNG resim verisinin Base64 kodlanmış hali |
| p[9] | id | String | Benzersiz obje kimliği |

### 5.7. DELETE (Obje Sil)

```
p[0]|p[1]|sender|DELETE|targetId
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | targetId | String | Silinecek objenin UUID'si |

### 5.8. CLEAR (Tümünü Temizle)

```
p[0]|p[1]|sender|CLEAR|ALL
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | — | String | Sabit değer: `ALL` |

### 5.9. CURSOR (İmleç Pozisyonu)

```
p[0]|p[1]|sender|CURSOR|x|y|color
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | x | int | İmleç X koordinatı |
| p[5] | y | int | İmleç Y koordinatı |
| p[6] | color | String | İmleç rengi (`#RRGGBB`) |

CURSOR verileri sunucuda saklanmaz, sadece anlık olarak iletilir.

---

## 6. Komutlar — Mesajlaşma (Chat)

### 6.1. CHAT (Anlık Mesaj)

```
p[0]|p[1]|sender|CHAT|message
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | message | String | Mesaj metni (düz metin) |

Sunucu bu mesajı odadaki TÜM üyelere (gönderen dahil) iletir.
Sunucu ayrıca mesajı oda geçmişine kaydeder.

### 6.2. CHAT_HISTORY (Geçmiş Mesaj)
Sunucu → İstemci. Odaya girişte geçmiş mesajların gönderimi.

```
p[0]|p[1]|SERVER|CHAT_HISTORY|originalSender|base64_message|originalTimestamp
```

| İndeks | Alan | Tip | Açıklama |
| :--- | :--- | :--- | :--- |
| p[4] | originalSender | String | Mesajı ilk gönderen kullanıcı adı |
| p[5] | base64_message | String | Mesaj metninin Base64 kodlanmış hali (UTF-8) |
| p[6] | originalTimestamp | long | Mesajın ilk gönderildiği zaman (epoch ms) |

Mesaj metni Base64 ile kodlanır çünkü orijinal metin `|` veya `\n` içerebilir.

---

## 7. Broadcast Kuralları

| Komut | Gönderene de iletilir mi? | Sunucuda saklanır mı? |
| :--- | :--- | :--- |
| SQUARE / CIRCLE / LINE / TRIANGLE / FREEHAND | Hayır | Evet |
| IMAGE | Hayır | Evet |
| DELETE | Hayır | Evet (silinir) |
| CLEAR | Evet | Evet (temizlenir) |
| CURSOR | Hayır | Hayır |
| CHAT | Evet | Evet |
| USER_LIST | Evet | Hayır |
| LOGIN_SUCCESS / ERROR / NAME_CHANGED / ROOM_INFO | Sadece hedef istemci | — |

---

## 8. Oda Girişi Akış Sırası (Snapshot)

Bir kullanıcı `JOIN_ROOM` veya `CREATE_ROOM` gönderdiğinde sunucu şu sırayla yanıt verir:

```
1. ROOM_INFO        → İstemci canvas + chat'i temizler
2. USER_LIST         → Üye listesi güncellenir
3. SQUARE/CIRCLE/... → Mevcut çizimler tek tek gönderilir (0..N adet)
4. IMAGE             → Mevcut resimler tek tek gönderilir (0..N adet)
5. CHAT_HISTORY      → Geçmiş mesajlar tek tek gönderilir (0..N adet)
```

---

## 9. Bağlantı Durum Makinesi (FSM)

```
    ┌─────────────┐
    │ DISCONNECTED │
    └──────┬──────┘
           │ TCP bağlantısı kur
           ▼
    ┌──────────────┐   LOGIN    ┌────────────────┐
    │  CONNECTED   │──────────→│ AUTHENTICATING │
    └──────────────┘           └───────┬────────┘
                                       │ LOGIN_SUCCESS
                                       ▼
                               ┌───────────┐
                       ┌──────→│   LOBBY   │←─────┐
                       │       └─────┬─────┘      │
                       │             │ CREATE/JOIN │
                       │             ▼             │
                       │       ┌───────────┐      │
                       └───────│   ROOM    │──────┘
                   LEAVE_ROOM  └───────────┘
                                CHAT / DRAW / CURSOR
```

---

## 10. Teknik Notlar

1. **ID Üretimi:** İstemci, her çizim objesine ve resme istemci tarafında UUID atar. Sunucu bu ID'yi olduğu gibi saklar ve iletir.
2. **Mükerrer Koruma:** İstemci, gelen objelerin ID'sini kontrol ederek aynı objeyi iki kez eklememeli. Bu özellikle snapshot sırasında kritiktir.
3. **Cursor Temizliği:** `USER_LIST` alındığında, listede olmayan kullanıcıların cursor'ları ekrandan kaldırılmalıdır. `ROOM_INFO` alındığında tüm cursor'lar temizlenmelidir.
4. **Base64:** `IMAGE` komutu ve `CHAT_HISTORY` içindeki mesaj metni Base64 ile kodlanır. Kodlama standardı: RFC 4648 (standart Base64, padding dahil).
5. **Renk Formatı:** Tüm renkler `#RRGGBB` formatında 7 karakterlik hex string'dir. Örnek: `#FF0000` (kırmızı).
6. **Oda Kodu:** 6 karakterlik, büyük harf + rakamlardan oluşan string. Örnek: `ABC123`.
