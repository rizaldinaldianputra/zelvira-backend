package com.zelvira.zelvira.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zelvira.zelvira.dto.FlashSaleItemResponse;
import com.zelvira.zelvira.dto.FlashSaleResponse;
import com.zelvira.zelvira.dto.ProductResponse;
import com.zelvira.zelvira.dto.ProductVariantResponse;
import com.zelvira.zelvira.entity.*;
import com.zelvira.zelvira.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    @Value("${app.nvidia.api-key:}")
    private String nvidiaApiKey;

    @Value("${app.nvidia.base-url:https://integrate.api.nvidia.com/v1}")
    private String nvidiaBaseUrl;

    @Value("${app.nvidia.model:nvidia/nemotron-3.5-super-vl-120b-a12b}")
    private String nvidiaModel;

    @Value("${app.google.frontend-url:https://zelvira.co.id}")
    private String frontendUrl;

    private final ProductService productService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final VoucherRepository voucherRepository;
    private final FlashSaleService flashSaleService;
    private final FaqRepository faqRepository;
    private final OrganizationService organizationService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AiChatService(
            ProductService productService,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CategoryRepository categoryRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            VoucherRepository voucherRepository,
            FlashSaleService flashSaleService,
            FaqRepository faqRepository,
            OrganizationService organizationService,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.productService = productService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.voucherRepository = voucherRepository;
        this.flashSaleService = flashSaleService;
        this.faqRepository = faqRepository;
        this.organizationService = organizationService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(45000);
        this.restTemplate = new RestTemplate(factory);
    }

    public static class AiChatResult {
        private String intent;
        private String reply;
        private String reasoning;
        private boolean usedAi;
        private boolean suggestLiveChat;
        private boolean handoffToLiveCs;
        private List<String> executedQueries = new ArrayList<>();
        private Object data;

        public AiChatResult() {
        }

        public AiChatResult(String intent, String reply, boolean usedAi, Object data) {
            this.intent = intent;
            this.reply = reply;
            this.usedAi = usedAi;
            this.data = data;
        }

        public AiChatResult(String intent, String reply, boolean usedAi, boolean suggestLiveChat, Object data) {
            this.intent = intent;
            this.reply = reply;
            this.usedAi = usedAi;
            this.suggestLiveChat = suggestLiveChat;
            this.data = data;
        }

        public boolean isSuggestLiveChat() {
            return suggestLiveChat;
        }

        public void setSuggestLiveChat(boolean suggestLiveChat) {
            this.suggestLiveChat = suggestLiveChat;
        }

        public boolean isHandoffToLiveCs() {
            return handoffToLiveCs;
        }

        public void setHandoffToLiveCs(boolean handoffToLiveCs) {
            this.handoffToLiveCs = handoffToLiveCs;
        }

        public String getIntent() {
            return intent;
        }

        public void setIntent(String intent) {
            this.intent = intent;
        }

        public String getReply() {
            return reply;
        }

        public void setReply(String reply) {
            this.reply = reply;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }

        public boolean isUsedAi() {
            return usedAi;
        }

        public void setUsedAi(boolean usedAi) {
            this.usedAi = usedAi;
        }

        public List<String> getExecutedQueries() {
            return executedQueries;
        }

        public void setExecutedQueries(List<String> executedQueries) {
            this.executedQueries = executedQueries;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    @Transactional(readOnly = true)
    public AiChatResult processCustomerMessage(String messageText, String imageUrl, String roomId,
            Long explicitUserId) {
        String cleanText = messageText != null ? messageText.trim() : "";
        boolean hasImage = imageUrl != null && !imageUrl.trim().isEmpty();
        User currentUser = resolveUser(roomId, explicitUserId);

        // 0. Check if user explicitly asks to switch to human Live CS / Admin
        if (!cleanText.isEmpty() && isRequestingLiveCs(cleanText)) {
            AiChatResult handoff = new AiChatResult(
                    "handoffLiveCs",
                    "👩‍💼 **Obrolan Dialihkan ke Live Chat Customer Service**\n\n"
                            + "Baik Kak, kami telah menghubungkan ruang obrolan ini ke **Tim Customer Service (Admin) Zelvira**. "
                            + "Admin kami telah menerima notifikasi dan akan segera membalas pesan Kakak secara langsung di sini. Mohon ditunggu sebentar ya Kak! 🙏",
                    false,
                    false,
                    null);
            handoff.setHandoffToLiveCs(true);
            return handoff;
        }

        // 1. Check Tanpa Query (Template Only: sapaan, terimaKasih, diLuarTopik) when
        // no image is attached
        if (!hasImage && !cleanText.isEmpty()) {
            String templateIntent = detectTemplateIntent(cleanText);
            if ("sapaan".equals(templateIntent)) {
                return new AiChatResult("sapaan", sapaan(currentUser), false, false, null);
            }
            if ("terimaKasih".equals(templateIntent)) {
                return new AiChatResult("terimaKasih", terimaKasih(), false, false, null);
            }
            if ("diLuarTopik".equals(templateIntent)) {
                return new AiChatResult("diLuarTopik", diLuarTopik(), false, true, null);
            }

            // 2. Check Teks Tetap Tanpa AI (11. faqPemesanan, 12. faqPengiriman, 13.
            // faqRetur, 14. infoToko)
            String fixedIntent = detectFixedTextIntent(cleanText);
            if ("faqPemesanan".equals(fixedIntent)) {
                return new AiChatResult("faqPemesanan", faqPemesanan(), false, null);
            }
            if ("faqPengiriman".equals(fixedIntent)) {
                return new AiChatResult("faqPengiriman", faqPengiriman(), false, null);
            }
            if ("faqRetur".equals(fixedIntent)) {
                return new AiChatResult("faqRetur", faqRetur(), false, null);
            }
            if ("infoToko".equals(fixedIntent)) {
                return new AiChatResult("infoToko", infoToko(), false, null);
            }

            // 3. Fast check for statusPesanan (10. Wajib Login) if user is NOT logged in
            if (isOrderStatusQuestion(cleanText) && currentUser == null) {
                Map<String, Object> unauthResult = statusPesanan(null, null);
                return new AiChatResult("statusPesanan", String.valueOf(unauthResult.get("message")), false,
                        unauthResult);
            }
        }

        // 4. Call NVIDIA Nemotron Vision-Language AI with Tool Calling (Queries 1-10)
        try {
            return callNvidiaAiWithTools(cleanText, imageUrl, currentUser);
        } catch (Exception e) {
            log.error("[AiChatService] NVIDIA API call failed, using deterministic query fallback: {}", e.getMessage());
            return executeDeterministicFallback(cleanText, currentUser);
        }
    }

    // =========================================================================
    // TANPA QUERY (BALASAN TEMPLATE MURNI)
    // =========================================================================

    public String sapaan(User user) {
        String name = (user != null && user.getFullName() != null && !user.getFullName().isBlank())
                ? "Kak " + user.getFullName()
                : "Kak";
        return "Halo " + name + "! 👋 Selamat datang di **Zelvira Official Store**.\n\n"
                + "Saya asisten virtual Zelvira yang siap membantu Kakak untuk:\n"
                + "• 👗 Mencari produk, cek detail, ukuran, warna & ketersediaan stok\n"
                + "• 💰 Cek harga, rentang harga, produk terlaris & rekomendasi outfit\n"
                + "• 🎟️ Info Flash Sale & kode voucher promo aktif hari ini\n"
                + "• 📦 Cek status pengiriman pesanan (khusus member login)\n"
                + "• ℹ️ Panduan pemesanan, pengiriman, retur & info toko\n\n"
                + "Silakan ketik pertanyaan atau kirim foto produk yang ingin Kakak cari ya! ✨";
    }

    public String terimaKasih() {
        return "Sama-sama Kak! 🌸 Senang bisa membantu. Jika ada lagi yang ingin ditanyakan seputar koleksi produk, promo, atau pesanan di Zelvira, jangan ragu untuk menghubungi kami kembali ya. Selamat berbelanja! 🛍️✨";
    }

    public String diLuarTopik() {
        return "Mohon maaf Kak 🙏, saya adalah asisten AI khusus **Zelvira Official Store** sehingga hanya dapat membantu menjawab pertanyaan seputar produk fashion Zelvira, cek harga & stok, promo/voucher, status pesanan, serta informasi layanan toko kami.\n\n"
                + "💡 *Jika Kakak memiliki kendala khusus atau membutuhkan bantuan langsung dari staf kami, silakan klik tombol **Hubungi Live CS** atau ketik **'Hubungi CS'** ya!* 😊";
    }

    // =========================================================================
    // TEKS TETAP (TANPA AI - 11 s/d 14)
    // =========================================================================

    public String faqPemesanan() {
        StringBuilder sb = new StringBuilder();
        sb.append("🛒 **Panduan Cara Pemesanan di Zelvira**\n\n")
                .append("1. **Pilih Produk & Varian**: Cari produk favorit Kakak, lalu pilih ukuran (Size) dan warna yang diinginkan.\n")
                .append("2. **Tambah ke Keranjang**: Klik tombol **+ Keranjang** atau **Beli Sekarang**.\n")
                .append("3. **Checkout & Alamat**: Buka halaman Keranjang, klik **Checkout**, lalu pilih atau tambahkan alamat pengiriman lengkap.\n")
                .append("4. **Pilih Kurir & Voucher**: Pilih ekspedisi pengiriman (JNE, J&T, SiCepat, Anteraja, GoSend/GrabExpress) dan masukkan kode voucher diskon jika ada.\n")
                .append("5. **Pembayaran Aman**: Selesaikan pembayaran melalui **QRIS, Virtual Account (BCA, Mandiri, BNI, BRI, Permata), GoPay, ShopeePay, Kartu Kredit**, atau **Zelvira Wallet**.\n")
                .append("6. **Konfirmasi Otomatis**: Pembayaran terverifikasi otomatis dan pesanan langsung kami proses! ✨");

        appendMatchingDbFaqs(sb, List.of("pesan", "order", "bayar", "checkout", "pembayaran"));
        return sb.toString();
    }

    public String faqPengiriman() {
        StringBuilder sb = new StringBuilder();
        sb.append("🚚 **Informasi Pengiriman Zelvira**\n\n")
                .append("• **Jadwal Pengiriman**: Setiap hari (Senin – Sabtu pukul 08.00 – 17.00 WIB). Pesanan yang terkonfirmasi sebelum pukul 15.00 WIB dikirim pada hari yang sama.\n")
                .append("• **Mitra Ekspedisi**: JNE, J&T Express, SiCepat, Anteraja, serta pengiriman instan GoSend & GrabExpress.\n")
                .append("• **Estimasi Waktu**:\n")
                .append("  - Instan / Same Day: 2 – 8 jam\n")
                .append("  - Reguler (Jabodetabek & Jawa): 1 – 3 hari kerja\n")
                .append("  - Luar Pulau Jawa: 3 – 6 hari kerja\n")
                .append("• **Lacak Resi**: Nomor resi otomatis muncul di menu **Pesanan Saya** segera setelah paket diserahkan ke kurir.");

        appendMatchingDbFaqs(sb, List.of("kirim", "pengiriman", "ongkir", "kurir", "resi", "ekspedisi"));
        return sb.toString();
    }

    public String faqRetur() {
        StringBuilder sb = new StringBuilder();
        sb.append("🔄 **Kebijakan Retur & Pengembalian Produk (Garansi 7 Hari)**\n\n")
                .append("1. **Batas Waktu Klaim**: Maksimal **7 hari** sejak status pesanan diterima.\n")
                .append("2. **Syarat Retur**:\n")
                .append("   • Produk cacat produksi, rusak saat diterima, atau salah kirim warna/ukuran dari pihak Zelvira.\n")
                .append("   • Produk belum pernah dipakai/dicuci dan label hangtag masih terpasang lengkap.\n")
                .append("   • **Wajib menyertakan video unboxing** tanpa jeda saat membuka paket.\n")
                .append("3. **Cara Mengajukan**: Buka menu **Pesanan Saya** -> pilih pesanan terkait -> klik **Ajukan Komplain / Retur** dan unggah bukti foto/video.\n")
                .append("4. **Biaya Ongkir Retur**: 100% ditanggung oleh Zelvira jika kesalahan berasal dari pihak kami.");

        appendMatchingDbFaqs(sb, List.of("retur", "return", "kembali", "tukar", "komplain", "cacat", "refund"));
        return sb.toString();
    }

    public String infoToko() {
        Organization org = organizationService.getOrganization();
        String name = org.getName() != null ? org.getName() : "Zelvira Official Store";
        String address = org.getAddress() != null ? org.getAddress() : "Indonesia";
        String phone = org.getPhoneNumber() != null ? org.getPhoneNumber() : "-";
        String email = org.getEmail() != null ? org.getEmail() : "help@zelvira.id";

        StringBuilder sb = new StringBuilder();
        sb.append("🏪 **Informasi Resmi ").append(name).append("**\n\n")
                .append("• **Nama Toko**: ").append(name).append("\n")
                .append("• **Alamat**: ").append(address).append("\n")
                .append("• **Jam Operasional CS**: Setiap hari, pukul 08.00 – 22.00 WIB\n")
                .append("• **WhatsApp / Telepon**: ").append(phone).append("\n")
                .append("• **Email**: ").append(email).append("\n")
                .append("• **Website Resmi**: ").append(frontendUrl).append("\n");

        if (org.getInstagramUrl() != null && !org.getInstagramUrl().isBlank()) {
            sb.append("• **Instagram**: ").append(org.getInstagramUrl()).append("\n");
        }
        if (org.getTiktokUrl() != null && !org.getTiktokUrl().isBlank()) {
            sb.append("• **TikTok**: ").append(org.getTiktokUrl()).append("\n");
        }
        return sb.toString().trim();
    }

    private void appendMatchingDbFaqs(StringBuilder sb, List<String> keywords) {
        try {
            List<Faq> activeFaqs = faqRepository.findAllByIsActiveTrueOrderByIdAsc();
            List<Faq> matched = activeFaqs.stream()
                    .filter(f -> {
                        String text = ((f.getQuestion() != null ? f.getQuestion() : "") + " "
                                + (f.getCategory() != null ? f.getCategory() : "")).toLowerCase();
                        return keywords.stream().anyMatch(text::contains);
                    })
                    .limit(2)
                    .collect(Collectors.toList());

            if (!matched.isEmpty()) {
                sb.append("\n\n📌 **Tanya Jawab Terkait:**");
                for (Faq faq : matched) {
                    sb.append("\n• **").append(faq.getQuestion()).append("**\n  ").append(faq.getAnswer());
                }
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // 10 DATABASE QUERIES UNTUK AI (PRODUK, STOK & PROMO, PESANAN)
    // =========================================================================

    /**
     * 1. cariProduk: Mencari produk berdasarkan kata kunci dan/atau kategori
     */
    public List<Map<String, Object>> cariProduk(String keyword, String kategori) {
        return cariProduk(keyword, kategori, null, null, null);
    }

    /**
     * 1b. cariProduk lengkap dengan filter Varian Warna, Ukuran/Size (All Size, S,
     * M, L, XL), dan Target Gender
     */
    public List<Map<String, Object>> cariProduk(String keyword, String kategori, String warna, String ukuran,
            String gender) {
        List<ProductResponse> allActive = getAllActiveProductResponses();
        String rawKw = keyword != null ? keyword.trim() : "";
        String cat = kategori != null ? kategori.trim().toLowerCase() : "";

        // Smart extraction from keyword if arguments are not explicitly given
        String effectiveColor = warna != null && !warna.isBlank() ? warna.trim() : extractColorFromText(rawKw);
        String effectiveSize = ukuran != null && !ukuran.isBlank() ? ukuran.trim() : extractSizeFromText(rawKw);
        String effectiveGender = gender != null && !gender.isBlank() ? gender.trim() : extractGenderFromText(rawKw);

        String normalizedGender = normalizeGender(effectiveGender);
        String normalizedSize = normalizeSize(effectiveSize);
        String targetColor = effectiveColor != null ? effectiveColor.toLowerCase().trim() : "";

        // Clean query terms from keyword
        String cleanKw = cleanSearchKeyword(rawKw, effectiveColor, effectiveSize, effectiveGender).toLowerCase().trim();

        return allActive.stream()
                .filter(p -> {
                    // Match Base Keyword (name, description, material, category, or sku)
                    boolean matchKw = cleanKw.isEmpty()
                            || containsIgnoreCase(p.getName(), cleanKw)
                            || containsIgnoreCase(p.getShortDescription(), cleanKw)
                            || containsIgnoreCase(p.getDescription(), cleanKw)
                            || containsIgnoreCase(p.getMaterial(), cleanKw)
                            || containsIgnoreCase(p.getSku(), cleanKw)
                            || (p.getCategory() != null && containsIgnoreCase(p.getCategory().getName(), cleanKw))
                            || matchesVariantKeywords(p, cleanKw);

                    // Match Category
                    boolean matchCat = cat.isEmpty()
                            || (p.getCategory() != null && containsIgnoreCase(p.getCategory().getName(), cat))
                            || containsIgnoreCase(p.getName(), cat);

                    // Match Target Gender (WOMEN, MEN, KIDS, UNISEX)
                    boolean matchGender = normalizedGender.isEmpty() || matchesGender(p.getGender(), normalizedGender);

                    // Match Variant Color (e.g. Sage Green, Hitam, Maroon, Mocca, Navy, Lilac,
                    // etc.)
                    boolean matchColor = targetColor.isEmpty() || matchesVariantColor(p, targetColor);

                    // Match Variant Size (All Size, S, M, L, XL, XXL, Jumbo)
                    boolean matchSize = normalizedSize.isEmpty() || matchesVariantSize(p, normalizedSize);

                    return matchKw && matchCat && matchGender && matchColor && matchSize;
                })
                .limit(8)
                .map(this::toCompactProductSummary)
                .collect(Collectors.toList());
    }

    /**
     * 2. detailProduk: Mengambil detail lengkap satu produk (deskripsi, bahan,
     * ukuran, warna, rating, stok)
     */
    public Map<String, Object> detailProduk(String namaAtauSlugAtauId) {
        ProductResponse found = findSingleProduct(namaAtauSlugAtauId);
        if (found == null) {
            return Map.of(
                    "found", false,
                    "message", "Produk '" + namaAtauSlugAtauId + "' tidak ditemukan di katalog aktif Zelvira.");
        }

        Map<String, Object> detail = new LinkedHashMap<>(toCompactProductSummary(found));
        detail.put("found", true);
        detail.put("description",
                found.getDescription() != null ? found.getDescription() : found.getShortDescription());
        detail.put("material", found.getMaterial() != null ? found.getMaterial() : "Premium Quality");
        detail.put("gender", found.getGender() != null ? found.getGender() : "UNISEX");
        detail.put("weightGram", found.getWeight() != null ? found.getWeight() : 300);
        detail.put("pointsEarned", found.getPointsEarned() != null ? found.getPointsEarned() : 0);

        List<Map<String, Object>> variants = Optional.ofNullable(found.getVariants())
                .orElse(Collections.emptyList())
                .stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .map(v -> {
                    Map<String, Object> vm = new LinkedHashMap<>();
                    vm.put("sku", v.getSku());
                    vm.put("color", v.getColor() != null ? v.getColor() : "Default");
                    vm.put("size", v.getSize() != null ? v.getSize() : "All Size");
                    vm.put("price", v.getPrice());
                    vm.put("priceFormatted", formatRupiah(v.getPrice()));
                    if (v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0) {
                        vm.put("discountPrice", v.getDiscountPrice());
                        vm.put("discountPriceFormatted", formatRupiah(v.getDiscountPrice()));
                    }
                    vm.put("stock", v.getStock() != null ? v.getStock() : 0);
                    return vm;
                })
                .collect(Collectors.toList());
        detail.put("variants", variants);
        return detail;
    }

    /**
     * 3. cekHarga: Mengecek harga normal, harga varian, dan harga diskon/flash sale
     * suatu produk
     */
    public Map<String, Object> cekHarga(String namaProduk) {
        List<ProductResponse> matched = findMatchingProducts(namaProduk, 5);
        if (matched.isEmpty()) {
            return Map.of(
                    "found", false,
                    "message", "Produk '" + namaProduk + "' tidak ditemukan.");
        }

        List<FlashSaleResponse> activeFlashSales = flashSaleService.getActiveFlashSales();
        Map<Long, BigDecimal> flashPriceByProductId = new HashMap<>();
        for (FlashSaleResponse fs : activeFlashSales) {
            if (fs.getItems() != null) {
                for (FlashSaleItemResponse item : fs.getItems()) {
                    if (item.getProductId() != null && item.getDiscountPrice() != null) {
                        flashPriceByProductId.put(item.getProductId(), item.getDiscountPrice());
                    }
                }
            }
        }

        List<Map<String, Object>> priceList = matched.stream().map(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("url", frontendUrl + "/products/" + p.getSlug());

            List<ProductVariantResponse> variants = Optional.ofNullable(p.getVariants())
                    .orElse(Collections.emptyList());
            BigDecimal minPrice = variants.stream()
                    .map(ProductVariantResponse::getPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO);
            BigDecimal maxPrice = variants.stream()
                    .map(ProductVariantResponse::getPrice)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(minPrice);
            BigDecimal minDiscountPrice = variants.stream()
                    .map(ProductVariantResponse::getDiscountPrice)
                    .filter(d -> d != null && d.compareTo(BigDecimal.ZERO) > 0)
                    .min(BigDecimal::compareTo)
                    .orElse(null);

            item.put("normalPrice", formatRupiah(minPrice));
            if (minPrice.compareTo(maxPrice) != 0) {
                item.put("priceRange", formatRupiah(minPrice) + " - " + formatRupiah(maxPrice));
            }
            if (minDiscountPrice != null && minDiscountPrice.compareTo(minPrice) < 0) {
                item.put("discountPrice", formatRupiah(minDiscountPrice));
            }
            if (flashPriceByProductId.containsKey(p.getId())) {
                item.put("flashSalePrice", formatRupiah(flashPriceByProductId.get(p.getId())));
            }
            return item;
        }).collect(Collectors.toList());

        return Map.of("found", true, "products", priceList);
    }

    /**
     * 4. produkByRentangHarga: Mencari produk dalam rentang harga tertentu
     * (minHarga s/d maxHarga)
     */
    public List<Map<String, Object>> produkByRentangHarga(Double minHarga, Double maxHarga, String keyword) {
        BigDecimal min = minHarga != null && minHarga > 0 ? BigDecimal.valueOf(minHarga) : BigDecimal.ZERO;
        BigDecimal max = maxHarga != null && maxHarga > 0 ? BigDecimal.valueOf(maxHarga)
                : BigDecimal.valueOf(100_000_000);
        String kw = keyword != null ? keyword.trim().toLowerCase() : "";

        return getAllActiveProductResponses().stream()
                .filter(p -> {
                    if (!kw.isEmpty() && !containsIgnoreCase(p.getName(), kw)
                            && (p.getCategory() == null || !containsIgnoreCase(p.getCategory().getName(), kw))) {
                        return false;
                    }
                    BigDecimal effectivePrice = getEffectivePrice(p);
                    return effectivePrice.compareTo(min) >= 0 && effectivePrice.compareTo(max) <= 0;
                })
                .sorted(Comparator.comparing(this::getEffectivePrice))
                .limit(8)
                .map(this::toCompactProductSummary)
                .collect(Collectors.toList());
    }

    /**
     * 5. produkSerupa: Mencari produk serupa berdasarkan kategori, bahan, atau
     * gender dari produk acuan
     */
    public List<Map<String, Object>> produkSerupa(String namaAtauSlugAtauId) {
        ProductResponse target = findSingleProduct(namaAtauSlugAtauId);
        List<ProductResponse> allActive = getAllActiveProductResponses();

        if (target == null) {
            return allActive.stream().limit(5).map(this::toCompactProductSummary).collect(Collectors.toList());
        }

        return allActive.stream()
                .filter(p -> !Objects.equals(p.getId(), target.getId()))
                .sorted((a, b) -> Integer.compare(similarityScore(b, target), similarityScore(a, target)))
                .limit(5)
                .map(this::toCompactProductSummary)
                .collect(Collectors.toList());
    }

    /**
     * 6. rekomendasi: Memberikan rekomendasi produk terbaik berdasarkan preferensi
     * / kategori / gender
     */
    public List<Map<String, Object>> rekomendasi(String preferensi, String gender, String kategori) {
        String pref = preferensi != null ? preferensi.trim().toLowerCase() : "";
        String gen = gender != null ? gender.trim().toUpperCase() : "";
        String cat = kategori != null ? kategori.trim().toLowerCase() : "";

        List<ProductResponse> candidates = getAllActiveProductResponses().stream()
                .filter(p -> {
                    boolean matchGen = gen.isEmpty() || p.getGender() == null
                            || p.getGender().equalsIgnoreCase(gen) || "UNISEX".equalsIgnoreCase(p.getGender());
                    boolean matchCat = cat.isEmpty()
                            || (p.getCategory() != null && containsIgnoreCase(p.getCategory().getName(), cat))
                            || containsIgnoreCase(p.getName(), cat);
                    boolean matchPref = pref.isEmpty()
                            || containsIgnoreCase(p.getName(), pref)
                            || containsIgnoreCase(p.getDescription(), pref)
                            || containsIgnoreCase(p.getMaterial(), pref)
                            || (p.getCategory() != null && containsIgnoreCase(p.getCategory().getName(), pref));
                    return matchGen && matchCat && matchPref;
                })
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            candidates = getAllActiveProductResponses();
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparing((ProductResponse p) -> p.getAverageRating() != null ? p.getAverageRating() : 0.0)
                        .reversed()
                        .thenComparing((ProductResponse p) -> getTotalStock(p), Comparator.reverseOrder()))
                .limit(6)
                .map(this::toCompactProductSummary)
                .collect(Collectors.toList());
    }

    /**
     * 7. produkTerlaris: Mengambil daftar produk paling laris berdasarkan data
     * penjualan (OrderItem) & rating
     */
    public List<Map<String, Object>> produkTerlaris(Integer limit) {
        int maxItems = (limit != null && limit > 0 && limit <= 10) ? limit : 5;
        List<Map<String, Object>> results = new ArrayList<>();
        Set<Long> addedIds = new HashSet<>();

        try {
            List<Object[]> topRows = orderItemRepository.findTopSellingProductIds(PageRequest.of(0, maxItems));
            for (Object[] row : topRows) {
                if (row != null && row.length >= 2 && row[0] instanceof Number) {
                    Long productId = ((Number) row[0]).longValue();
                    long totalSold = ((Number) row[1]).longValue();
                    try {
                        ProductResponse p = productService.getProductById(productId);
                        if (Boolean.TRUE.equals(p.getIsActive())) {
                            Map<String, Object> summary = new LinkedHashMap<>(toCompactProductSummary(p));
                            summary.put("totalSold", totalSold);
                            results.add(summary);
                            addedIds.add(productId);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[AiChatService] Top selling query fallback: {}", e.getMessage());
        }

        if (results.size() < maxItems) {
            List<ProductResponse> fallback = getAllActiveProductResponses().stream()
                    .filter(p -> !addedIds.contains(p.getId()))
                    .sorted(Comparator
                            .comparing((ProductResponse p) -> p.getReviewCount() != null ? p.getReviewCount() : 0)
                            .reversed()
                            .thenComparing(p -> p.getAverageRating() != null ? p.getAverageRating() : 0.0,
                                    Comparator.reverseOrder()))
                    .limit(maxItems - results.size())
                    .collect(Collectors.toList());

            for (ProductResponse p : fallback) {
                results.add(toCompactProductSummary(p));
            }
        }

        return results;
    }

    /**
     * 8. cekStok: Mengecek ketersediaan stok produk beserta rincian per ukuran dan
     * warna
     */
    public Map<String, Object> cekStok(String namaProduk, String warna, String ukuran) {
        ProductResponse product = findSingleProduct(namaProduk);
        if (product == null) {
            return Map.of(
                    "found", false,
                    "message", "Produk '" + namaProduk + "' tidak ditemukan.");
        }

        String colorFilter = warna != null ? warna.trim().toLowerCase() : "";
        String sizeFilter = ukuran != null ? ukuran.trim().toLowerCase() : "";

        List<ProductVariantResponse> variants = Optional.ofNullable(product.getVariants())
                .orElse(Collections.emptyList())
                .stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .filter(v -> colorFilter.isEmpty() || containsIgnoreCase(v.getColor(), colorFilter))
                .filter(v -> sizeFilter.isEmpty() || (v.getSize() != null && v.getSize().equalsIgnoreCase(sizeFilter)))
                .collect(Collectors.toList());

        int totalStock = variants.stream()
                .mapToInt(v -> v.getStock() != null ? v.getStock() : 0)
                .sum();

        List<Map<String, Object>> variantStock = variants.stream().map(v -> {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("sku", v.getSku());
            vm.put("color", v.getColor() != null ? v.getColor() : "Standar");
            vm.put("size", v.getSize() != null ? v.getSize() : "All Size");
            vm.put("stock", v.getStock() != null ? v.getStock() : 0);
            vm.put("status", (v.getStock() != null && v.getStock() > 0) ? "Tersedia" : "Habis");
            vm.put("priceFormatted", formatRupiah(v.getPrice()));
            return vm;
        }).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("found", true);
        res.put("productName", product.getName());
        res.put("url", frontendUrl + "/products/" + product.getSlug());
        res.put("totalStock", totalStock);
        res.put("isAvailable", totalStock > 0);
        res.put("variants", variantStock);
        return res;
    }

    /**
     * 9. promoAktif: Mengambil daftar Flash Sale aktif, Voucher diskon aktif, dan
     * produk yang sedang diskon
     */
    public Map<String, Object> promoAktif() {
        Map<String, Object> promoData = new LinkedHashMap<>();

        // 1. Active Vouchers
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> vouchers = voucherRepository.findByIsActiveTrue().stream()
                .filter(v -> v.getValidUntil() == null || !v.getValidUntil().isBefore(today))
                .map(v -> {
                    Map<String, Object> vm = new LinkedHashMap<>();
                    vm.put("code", v.getCode());
                    vm.put("discountType", v.getDiscountType());
                    vm.put("discount",
                            "PERCENTAGE".equalsIgnoreCase(v.getDiscountType())
                                    || "PERCENT".equalsIgnoreCase(v.getDiscountType())
                                            ? v.getDiscountAmount().intValue() + "%"
                                            : formatRupiah(BigDecimal.valueOf(v.getDiscountAmount())));
                    vm.put("minPurchase",
                            v.getMinPurchase() != null ? formatRupiah(BigDecimal.valueOf(v.getMinPurchase()))
                                    : "Tanpa minimum belanja");
                    if (v.getMaxDiscount() != null && v.getMaxDiscount() > 0) {
                        vm.put("maxDiscount", formatRupiah(BigDecimal.valueOf(v.getMaxDiscount())));
                    }
                    vm.put("validUntil",
                            v.getValidUntil() != null ? v.getValidUntil().toString() : "Selama persediaan masih ada");
                    vm.put("targetMembership", v.getTargetMembership() != null ? v.getTargetMembership() : "ALL");
                    return vm;
                })
                .collect(Collectors.toList());
        promoData.put("activeVouchers", vouchers);

        // 2. Active Flash Sales
        List<FlashSaleResponse> flashSales = flashSaleService.getActiveFlashSales();
        List<Map<String, Object>> flashSaleList = flashSales.stream().map(fs -> {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("name", fs.getName());
            fm.put("endTime", fs.getEndTime() != null ? fs.getEndTime().toString() : "");
            List<Map<String, Object>> items = Optional.ofNullable(fs.getItems()).orElse(Collections.emptyList())
                    .stream()
                    .limit(6)
                    .map(item -> {
                        Map<String, Object> im = new LinkedHashMap<>();
                        im.put("productName", item.getProductName());
                        im.put("originalPrice", formatRupiah(item.getOriginalPrice()));
                        im.put("flashPrice", formatRupiah(item.getDiscountPrice()));
                        im.put("remainingStock", item.getFlashStock());
                        return im;
                    })
                    .collect(Collectors.toList());
            fm.put("items", items);
            return fm;
        }).collect(Collectors.toList());
        promoData.put("activeFlashSales", flashSaleList);

        // 3. Discounted Catalog Products
        List<Map<String, Object>> discountedProducts = getAllActiveProductResponses().stream()
                .filter(p -> p.getVariants() != null && p.getVariants().stream()
                        .anyMatch(v -> v.getDiscountPrice() != null && v.getPrice() != null
                                && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                && v.getDiscountPrice().compareTo(v.getPrice()) < 0))
                .limit(5)
                .map(this::toCompactProductSummary)
                .collect(Collectors.toList());
        promoData.put("discountedProducts", discountedProducts);

        return promoData;
    }

    /**
     * 10. statusPesanan (WAJIB LOGIN): Mengecek status pesanan milik user yang
     * sedang login
     */
    public Map<String, Object> statusPesanan(User loggedInUser, String nomorPesanan) {
        if (loggedInUser == null) {
            return Map.of(
                    "authenticated", false,
                    "found", false,
                    "message", "🔒 **Wajib Login untuk Cek Status Pesanan**\n\n"
                            + "Mohon maaf Kak, untuk menjaga keamanan dan privasi data transaksi, pengecekan status pesanan hanya dapat dilakukan setelah Kakak **Login** ke akun Zelvira.\n\n"
                            + "Silakan login terlebih dahulu melalui halaman **" + frontendUrl
                            + "/login**, lalu tanyakan kembali status pesanan Kakak di sini ya! 😊");
        }

        String cleanOrderNo = nomorPesanan != null ? nomorPesanan.trim() : "";
        List<Order> orders;

        if (!cleanOrderNo.isEmpty()) {
            Optional<Order> orderOpt = orderRepository.findByOrderNumberIgnoreCase(cleanOrderNo);
            if (orderOpt.isEmpty()) {
                return Map.of(
                        "authenticated", true,
                        "found", false,
                        "message", "Pesanan dengan nomor **" + cleanOrderNo + "** tidak ditemukan.");
            }
            Order order = orderOpt.get();
            if (!Objects.equals(order.getUserId(), loggedInUser.getId())) {
                return Map.of(
                        "authenticated", true,
                        "found", false,
                        "message", "Maaf Kak, pesanan **" + cleanOrderNo + "** tidak terdaftar pada akun Kakak.");
            }
            orders = List.of(order);
        } else {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(loggedInUser.getId())
                    .stream()
                    .limit(5)
                    .collect(Collectors.toList());
        }

        if (orders.isEmpty()) {
            return Map.of(
                    "authenticated", true,
                    "found", false,
                    "message", "Saat ini belum ada riwayat pesanan pada akun Kakak (" + loggedInUser.getEmail()
                            + "). Yuk mulai belanja koleksi terbaik Zelvira!");
        }

        List<Map<String, Object>> orderSummaries = orders.stream().map(o -> {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("orderNumber", o.getOrderNumber());
            om.put("status", o.getStatus());
            om.put("statusLabel", translateOrderStatus(o.getStatus()));
            om.put("totalAmount",
                    formatRupiah(BigDecimal.valueOf(o.getTotalAmount() != null ? o.getTotalAmount() : 0)));
            om.put("paymentMethod", o.getPaymentMethod());
            om.put("shippingService", o.getShippingService() != null ? o.getShippingService() : "-");
            om.put("trackingNumber", o.getTrackingNumber() != null ? o.getTrackingNumber() : "Belum tersedia");
            om.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : "");

            List<String> itemNames = Optional.ofNullable(o.getItems()).orElse(Collections.emptyList())
                    .stream()
                    .map(i -> (i.getProductName() != null ? i.getProductName() : "Produk")
                            + " (" + (i.getColor() != null ? i.getColor() : "-") + "/"
                            + (i.getSize() != null ? i.getSize() : "All Size") + ") x" + i.getQuantity())
                    .collect(Collectors.toList());
            om.put("items", itemNames);
            return om;
        }).collect(Collectors.toList());

        return Map.of(
                "authenticated", true,
                "found", true,
                "customerName",
                loggedInUser.getFullName() != null ? loggedInUser.getFullName() : loggedInUser.getEmail(),
                "orders", orderSummaries);
    }

    // =========================================================================
    // NVIDIA NEMOTRON-3.5-SUPER-VL-120B-A12B INTEGRATION (WITH TOOL CALLING)
    // =========================================================================

    private AiChatResult callNvidiaAiWithTools(String userText, String imageUrl, User currentUser) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System instruction
        String userContext = currentUser != null
                ? "Pengguna saat ini SUDAH LOGIN sebagai: "
                        + (currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getEmail())
                        + " (userId=" + currentUser.getId() + ")."
                : "Pengguna saat ini BELUM LOGIN (Tamu/Guest). Jika pengguna menanyakan status pesanan, panggil tool statusPesanan atau beri tahu bahwa mereka wajib login terlebih dahulu.";

        String systemPrompt = "Anda adalah Asisten AI resmi Zelvira Official Store (toko e-commerce fashion & busana muslim premium di Indonesia).\n"
                + userContext + "\n\n"
                + "ATURAN UTAMA:\n"
                + "1. Gunakan Bahasa Indonesia yang ramah, sopan, rapi, dan informatif.\n"
                + "2. Untuk menjawab pertanyaan seputar produk, harga, rentang harga, produk serupa, rekomendasi, produk terlaris, stok, promo aktif, atau status pesanan, WAJIB panggil fungsi/tool yang tersedia agar data 100% akurat dari database Zelvira.\n"
                + "3. JANGAN PERNAH MENGGUNAKAN TABEL PIPA ASCII/MARKDOWN (`|---|---|`) karena rusak dan sulit dibaca di layar chat/mobile! Selalu gunakan poin-poin/list rapi (`•`).\n"
                + "4. Jangan pernah mengarang nama produk, harga, stok, kode voucher, atau status pesanan yang tidak ada di hasil query tool.\n"
                + "5. Jika pengguna mengirim gambar (misal foto baju/busana atau tangkapan layar pesanan), analisis gambar tersebut lalu panggil tool yang relevan (seperti `cariProduk`, `rekomendasi`, atau `statusPesanan`).\n"
                + "6. FORMAT CARD INTERAKTIF (SANGAT PENTING):\n"
                + "   • Produk: Setiap kali menyebutkan atau merekomendasikan produk, WAJIB gunakan format standar berikut agar aplikasi web dan mobile otomatis merendernya sebagai CARD PRODUK interaktif lengkap (dengan foto, nama, kategori, stok, harga, dan tombol Lihat):\n"
                + "     • **[Nama Produk](" + frontendUrl + "/products/{slug})** — {priceFormatted}\n"
                + "       Kategori: {category} | Stok: {totalStock} | Gambar: {imageUrl}\n"
                + "   • Pesanan: SELALU sertakan kode pesanan `ORD-...` (contoh: `ORD-20261103-XXXX`) agar aplikasi langsung memunculkan Card Pesanan interaktif.\n"
                + "   • Promo & Voucher: Sebutkan kode voucher dengan jelas (misal: `KODE: ZELVIRA10` atau voucher `HEMAT50`), nominal diskon, dan tautan `[Klaim Voucher Promo]("
                + frontendUrl + "/promo)` agar aplikasi memunculkan Card Voucher dengan tombol Klaim otomatis.\n"
                + "7. PENTING (ESKALASI LIVE CHAT): Jika hasil pencarian kosong, kendala pengguna belum menemukan solusi/mentok (misal komplain rumit, kendala pembayaran, custom order, atau produk tidak ditemukan), sarankan pengguna dengan sopan untuk beralih ke **Live Chat Customer Service (Admin)** dengan mengetik **'Hubungi CS'** atau menekan tombol **Hubungi Live CS** di ruang chat.\n"
                + "8. PENCARIAN VARIAN WARNA, UKURAN, DAN TARGET GENDER:\n"
                + "   • Pengguna dapat mencari produk berdasarkan varian warna (misal: sage green, maroon, hitam, mocca, navy, lilac, putih, dll), ukuran (misal: All Size, S, M, L, XL, XXL, Jumbo), serta target gender (WANITA/WOMEN, PRIA/MEN, UNISEX, ANAK/KIDS).\n"
                + "   • Saat pengguna menyebutkan warna, ukuran, atau gender (misal: 'gamis hijau sage ukuran L', 'baju pria hitam size XL', 'dress all size wanita'), masukkan parameter `warna`, `ukuran`, dan `gender` ke dalam pemanggilan tool `cariProduk`, `cekStok`, atau `rekomendasi`.\n"
                + "   • Dalam jawaban Anda, sebutkan secara jelas pilihan warna yang tersedia, ukuran yang tersedia (termasuk apakah All Size atau tersedia ukuran spesifik S/M/L/XL), target gender produk, dan stok terkini masing-masing varian agar pengguna mendapatkan info yang sangat lengkap!";

        messages.add(Map.of("role", "system", "content", systemPrompt));

        // User message (Multimodal content array if imageUrl is provided, matching the
        // Python snippet)
        if (imageUrl != null && !imageUrl.isBlank()) {
            List<Map<String, Object>> contentParts = new ArrayList<>();
            contentParts.add(Map.of(
                    "type", "text",
                    "text",
                    userText.isEmpty()
                            ? "Tolong analisis gambar ini dan bantu carikan produk atau informasi yang sesuai di Zelvira."
                            : userText));
            contentParts.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", imageUrl)));
            messages.add(Map.of("role", "user", "content", contentParts));
        } else {
            messages.add(Map.of("role", "user", "content", userText));
        }

        List<Map<String, Object>> tools = buildToolDefinitions();
        List<String> executedQueries = new ArrayList<>();
        Object lastToolData = null;

        // First call to NVIDIA API
        JsonNode firstResponse = sendNvidiaChatRequest(messages, tools);
        JsonNode choice = firstResponse.path("choices").get(0);
        JsonNode messageNode = choice.path("message");
        String reasoning = messageNode.path("reasoning_content").asText(null);

        JsonNode toolCalls = messageNode.path("tool_calls");
        if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
            // Append assistant message with tool_calls
            Map<String, Object> assistantMsg = objectMapper.convertValue(messageNode,
                    new TypeReference<Map<String, Object>>() {
                    });
            messages.add(assistantMsg);

            for (JsonNode toolCall : toolCalls) {
                String toolCallId = toolCall.path("id").asText();
                String funcName = toolCall.path("function").path("name").asText();
                String rawArgs = toolCall.path("function").path("arguments").asText("{}");

                Map<String, Object> args = new HashMap<>();
                try {
                    args = objectMapper.readValue(rawArgs, new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception ignored) {
                }

                Object toolResult = executeToolCall(funcName, args, currentUser);
                executedQueries.add(funcName);
                lastToolData = toolResult;

                Map<String, Object> toolResponseMsg = new LinkedHashMap<>();
                toolResponseMsg.put("role", "tool");
                toolResponseMsg.put("tool_call_id", toolCallId);
                toolResponseMsg.put("name", funcName);
                toolResponseMsg.put("content", objectMapper.writeValueAsString(toolResult));
                messages.add(toolResponseMsg);
            }

            // Second call to NVIDIA API to synthesize final natural language response
            JsonNode secondResponse = sendNvidiaChatRequest(messages, null);
            JsonNode secondMsg = secondResponse.path("choices").get(0).path("message");
            String finalContent = cleanModelOutput(secondMsg.path("content").asText(""));
            String secondReasoning = secondMsg.path("reasoning_content").asText(null);

            if (finalContent.isBlank() && lastToolData != null) {
                finalContent = formatToolDataFallback(executedQueries.get(0), lastToolData);
            }

            boolean isStuckOrEmpty = isToolResultEmptyOrUnresolved(lastToolData, finalContent, userText);
            if (isStuckOrEmpty && !finalContent.toLowerCase().contains("hubungi cs")
                    && !finalContent.toLowerCase().contains("live cs")) {
                finalContent += "\n\n💡 *Belum menemukan solusi yang sesuai Kak? Silakan klik tombol **Hubungi Live CS** atau ketik **\"Hubungi CS\"** untuk mengobrol langsung dengan Admin kami ya!*";
            }

            AiChatResult result = new AiChatResult(
                    executedQueries.isEmpty() ? "ai_chat" : executedQueries.get(0),
                    finalContent,
                    true,
                    isStuckOrEmpty,
                    lastToolData);
            result.setExecutedQueries(executedQueries);
            result.setReasoning(secondReasoning != null ? secondReasoning : reasoning);
            return result;
        }

        // If the model answered directly without tool_calls, check if a query should
        // have been run
        String directContent = cleanModelOutput(messageNode.path("content").asText(""));
        if (directContent.isBlank()) {
            return executeDeterministicFallback(userText, currentUser);
        }

        boolean shouldSuggestLive = isMessageUnresolvedOrComplaint(userText, directContent);
        if (shouldSuggestLive && !directContent.toLowerCase().contains("hubungi cs")
                && !directContent.toLowerCase().contains("live cs")) {
            directContent += "\n\n💡 *Jika Kakak membutuhkan bantuan lebih lanjut, silakan klik tombol **Hubungi Live CS** atau ketik **\"Hubungi CS\"** untuk terhubung langsung dengan Admin Zelvira.*";
        }

        AiChatResult result = new AiChatResult("ai_chat", directContent, true, shouldSuggestLive, null);
        result.setReasoning(reasoning);
        return result;
    }

    private JsonNode sendNvidiaChatRequest(List<Map<String, Object>> messages, List<Map<String, Object>> tools)
            throws Exception {
        String endpoint = (nvidiaBaseUrl.endsWith("/") ? nvidiaBaseUrl.substring(0, nvidiaBaseUrl.length() - 1)
                : nvidiaBaseUrl)
                + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", nvidiaModel);
        body.put("messages", messages);
        body.put("temperature", 1);
        body.put("top_p", 0.95);
        body.put("max_tokens", 4096);
        body.put("seed", 42);
        body.put("stream", false);
        body.put("chat_template_kwargs", Map.of("enable_thinking", true));

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("parallel_tool_calls", true);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(nvidiaApiKey);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("NVIDIA API returned HTTP " + response.getStatusCode());
        }

        return objectMapper.readTree(response.getBody());
    }

    private Object executeToolCall(String funcName, Map<String, Object> args, User currentUser) {
        switch (funcName) {
            case "cariProduk":
                return cariProduk(
                        asString(args.get("keyword")),
                        asString(args.get("kategori")),
                        asString(args.get("warna")),
                        asString(args.get("ukuran")),
                        asString(args.get("gender")));
            case "detailProduk":
                return detailProduk(asString(args.get("namaAtauSlugAtauId")));
            case "cekHarga":
                return cekHarga(asString(args.get("namaProduk")));
            case "produkByRentangHarga":
                return produkByRentangHarga(
                        asDouble(args.get("minHarga")),
                        asDouble(args.get("maxHarga")),
                        asString(args.get("keyword")));
            case "produkSerupa":
                return produkSerupa(asString(args.get("namaAtauSlugAtauId")));
            case "rekomendasi":
                return rekomendasi(
                        asString(args.get("preferensi")),
                        asString(args.get("gender")),
                        asString(args.get("kategori")));
            case "produkTerlaris":
                return produkTerlaris(asInt(args.get("limit")));
            case "cekStok":
                return cekStok(
                        asString(args.get("namaProduk")),
                        asString(args.get("warna")),
                        asString(args.get("ukuran")));
            case "promoAktif":
                return promoAktif();
            case "statusPesanan":
                return statusPesanan(currentUser, asString(args.get("nomorPesanan")));
            case "faqPemesanan":
                return Map.of("info", faqPemesanan());
            case "faqPengiriman":
                return Map.of("info", faqPengiriman());
            case "faqRetur":
                return Map.of("info", faqRetur());
            case "infoToko":
                return Map.of("info", infoToko());
            default:
                return Map.of("error", "Unknown function: " + funcName);
        }
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(toolDef("cariProduk",
                "Mencari daftar produk di katalog Zelvira berdasarkan kata kunci, kategori, varian warna (misal: hitam, sage green, maroon, mocca, navy, putih), ukuran/size (misal: All Size, S, M, L, XL, XXL, Jumbo), dan target gender (WOMEN/Wanita, MEN/Pria, UNISEX, KIDS/Anak).",
                Map.of(
                        "keyword",
                        prop("string",
                                "Kata kunci nama produk, bahan, atau model (misal: gamis, dress, pashmina, blazer, katun)"),
                        "kategori", prop("string", "Nama kategori produk opsional"),
                        "warna",
                        prop("string",
                                "Filter warna spesifik opsional (misal: hitam, sage green, maroon, navy, mocca, putih, lilac, dusty pink)"),
                        "ukuran",
                        prop("string", "Filter ukuran/size opsional (misal: All Size, S, M, L, XL, XXL, Jumbo)"),
                        "gender",
                        prop("string",
                                "Filter target gender opsional: WOMEN/Wanita, MEN/Pria, UNISEX, atau KIDS/Anak")),
                List.of()));

        tools.add(toolDef("detailProduk",
                "Mengambil informasi detail lengkap suatu produk (deskripsi, bahan, ukuran, warna, varian, rating, dan stok).",
                Map.of("namaAtauSlugAtauId", prop("string", "Nama produk, slug, atau ID produk")),
                List.of("namaAtauSlugAtauId")));

        tools.add(toolDef("cekHarga",
                "Mengecek harga normal, rentang harga varian, harga diskon, dan harga flash sale dari suatu produk.",
                Map.of("namaProduk", prop("string", "Nama produk yang ingin dicek harganya")),
                List.of("namaProduk")));

        tools.add(toolDef("produkByRentangHarga",
                "Mencari produk berdasarkan rentang harga minimum dan maksimum dalam Rupiah.",
                Map.of(
                        "minHarga", prop("number", "Harga minimum dalam Rupiah (misal: 50000)"),
                        "maxHarga", prop("number", "Harga maksimum dalam Rupiah (misal: 200000)"),
                        "keyword", prop("string", "Kata kunci produk opsional")),
                List.of()));

        tools.add(toolDef("produkSerupa",
                "Mencari produk serupa atau sejenis dengan produk tertentu berdasarkan kategori, bahan, dan gender.",
                Map.of("namaAtauSlugAtauId", prop("string", "Nama produk, slug, atau ID produk acuan")),
                List.of("namaAtauSlugAtauId")));

        tools.add(toolDef("rekomendasi",
                "Memberikan rekomendasi produk terbaik dari Zelvira berdasarkan preferensi gaya/bahan, gender, atau kategori.",
                Map.of(
                        "preferensi",
                        prop("string", "Preferensi gaya, bahan, atau acara (misal: formal, kasual, pesta, adem)"),
                        "gender", prop("string", "Target gender opsional: WOMEN, MEN, atau UNISEX"),
                        "kategori", prop("string", "Kategori produk opsional")),
                List.of()));

        tools.add(toolDef("produkTerlaris",
                "Mengambil daftar produk terlaris (best seller) dan paling populer di Zelvira.",
                Map.of("limit", prop("integer", "Jumlah produk terlaris yang ditampilkan (default 5)")),
                List.of()));

        tools.add(toolDef("cekStok",
                "Mengecek ketersediaan stok produk secara real-time beserta rincian per warna dan ukuran.",
                Map.of(
                        "namaProduk", prop("string", "Nama produk yang ingin dicek stoknya"),
                        "warna", prop("string", "Filter warna opsional"),
                        "ukuran", prop("string", "Filter ukuran/size opsional (S, M, L, XL, All Size)")),
                List.of("namaProduk")));

        tools.add(toolDef("promoAktif",
                "Mengambil semua promo aktif saat ini: Flash Sale aktif, kode Voucher diskon, dan produk yang sedang diskon.",
                Map.of(),
                List.of()));

        tools.add(toolDef("statusPesanan",
                "Mengecek status pesanan dan nomor resi pengiriman milik pelanggan yang sedang login.",
                Map.of("nomorPesanan", prop("string",
                        "Nomor pesanan / order number opsional (misal: ORD-xxx). Kosongkan untuk melihat pesanan terbaru milik user.")),
                List.of()));

        return tools;
    }

    private Map<String, Object> toolDef(String name, String description, Map<String, Object> properties,
            List<String> required) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", required);

        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", params);

        return Map.of("type", "function", "function", fn);
    }

    private Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    // =========================================================================
    // INTENT DETECTION & DETERMINISTIC FALLBACK
    // =========================================================================

    private String detectTemplateIntent(String text) {
        String lower = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim();
        String[] words = lower.split("\\s+");

        // 1. Sapaan (only when message is a pure greeting / short salutation)
        Set<String> greetings = Set.of("halo", "hai", "hi", "hello", "pagi", "siang", "sore", "malam",
                "selamat", "assalamualaikum", "permisi", "min", "admin", "kak", "sis", "gan", "tes", "test", "ping");
        if (words.length <= 4 && Arrays.stream(words).allMatch(greetings::contains)) {
            return "sapaan";
        }

        // 2. Terima Kasih
        if (lower.matches(
                "^(oke\\s+|ok\\s+|baik\\s+|sip\\s+|mantap\\s+)?(terima\\s*kasih|makasih|thanks|thank\\s*you|tq|thx|syukron)(\\s+kak|\\s+min|\\s+banyak|\\s+ya)*$")) {
            return "terimaKasih";
        }

        // 3. Di Luar Topik (Explicitly unrelated topics)
        List<String> offTopicKeywords = List.of(
                "presiden", "politik", "pemilu", "partai", "koding", "coding", "javascript", "python",
                "rumus matematika", "fisika", "biologi", "resep masakan", "cara memasak", "ramalan cuaca",
                "skor bola", "liga inggris", "kripto", "bitcoin", "saham", "chord gitar", "lirik lagu");
        boolean hasStoreWord = lower.matches(
                ".*\\b(produk|baju|gamis|dress|hijab|kerudung|celana|atasan|rok|jaket|blazer|harga|stok|promo|diskon|voucher|pesanan|order|paket|resi|ongkir|retur|toko|zelvira|beli|belanja|ukuran|size|warna)\\b.*");
        if (!hasStoreWord && offTopicKeywords.stream().anyMatch(lower::contains)) {
            return "diLuarTopik";
        }

        return null;
    }

    private String detectFixedTextIntent(String text) {
        String lower = text.toLowerCase();

        // 11. faqPemesanan
        if (lower.contains("cara pesan") || lower.contains("cara memesan") || lower.contains("cara beli")
                || lower.contains("cara order") || lower.contains("cara belanja") || lower.contains("panduan pemesanan")
                || lower.contains("metode pembayaran") || lower.contains("cara bayar")
                || lower.contains("konfirmasi pembayaran")) {
            return "faqPemesanan";
        }

        // 12. faqPengiriman
        if (lower.contains("info pengiriman") || lower.contains("jadwal pengiriman")
                || lower.contains("lama pengiriman")
                || lower.contains("estimasi pengiriman") || lower.contains("ekspedisi apa")
                || lower.contains("kurir apa")
                || lower.contains("kapan dikirim") || lower.contains("pengiriman dari mana")) {
            return "faqPengiriman";
        }

        // 13. faqRetur
        if (lower.contains("cara retur") || lower.contains("kebijakan retur") || lower.contains("syarat retur")
                || lower.contains("pengembalian barang") || lower.contains("tukar barang")
                || lower.contains("tukar ukuran")
                || lower.contains("barang cacat") || lower.contains("salah kirim") || lower.contains("garansi retur")
                || lower.contains("cara refund")) {
            return "faqRetur";
        }

        // 14. infoToko
        if (lower.contains("info toko") || lower.contains("alamat toko") || lower.contains("lokasi toko")
                || lower.contains("jam buka") || lower.contains("jam operasional") || lower.contains("kontak toko")
                || lower.contains("nomor wa") || lower.contains("nomor whatsapp") || lower.contains("tentang toko")
                || lower.contains("profil toko")) {
            return "infoToko";
        }

        return null;
    }

    public boolean isRequestingLiveCs(String text) {
        if (text == null)
            return false;
        String lower = text.toLowerCase().trim();
        return lower.contains("hubungi cs") || lower.contains("live cs") || lower.contains("live chat")
                || lower.contains("chat admin") || lower.contains("hubungi admin")
                || lower.contains("bicara dengan admin")
                || lower.contains("bicara dengan cs") || lower.contains("cs manusia") || lower.contains("operator")
                || lower.equals("admin") || lower.equals("cs");
    }

    @SuppressWarnings("unchecked")
    private boolean isToolResultEmptyOrUnresolved(Object toolData, String reply, String userText) {
        if (toolData instanceof List && ((List<?>) toolData).isEmpty()) {
            return true;
        }
        if (toolData instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) toolData;
            if (Boolean.FALSE.equals(map.get("found")) && !Boolean.FALSE.equals(map.get("authenticated"))) {
                return true;
            }
        }
        return isMessageUnresolvedOrComplaint(userText, reply);
    }

    private boolean isMessageUnresolvedOrComplaint(String userText, String reply) {
        String lowerUser = userText != null ? userText.toLowerCase() : "";
        String lowerReply = reply != null ? reply.toLowerCase() : "";
        return lowerUser.contains("komplain") || lowerUser.contains("rusak") || lowerUser.contains("belum sampai")
                || lowerUser.contains("masalah") || lowerUser.contains("gagal bayar") || lowerUser.contains("error")
                || lowerUser.contains("tidak bisa") || lowerUser.contains("bantuan cs")
                || lowerReply.contains("tidak ditemukan") || lowerReply.contains("mohon maaf")
                || lowerReply.contains("belum tersedia");
    }

    private boolean isOrderStatusQuestion(String text) {
        String lower = text.toLowerCase();
        return lower.contains("status pesanan") || lower.contains("cek pesanan") || lower.contains("pesanan saya")
                || lower.contains("orderan saya") || lower.contains("cek resi") || lower.contains("lacak pesanan")
                || lower.contains("ord-");
    }

    private AiChatResult executeDeterministicFallback(String userText, User currentUser) {
        String lower = userText.toLowerCase();

        if (isOrderStatusQuestion(lower)) {
            Matcher m = Pattern.compile("(ord-[a-z0-9-]+)", Pattern.CASE_INSENSITIVE).matcher(userText);
            String orderNo = m.find() ? m.group(1) : null;
            Map<String, Object> data = statusPesanan(currentUser, orderNo);
            return new AiChatResult("statusPesanan", formatToolDataFallback("statusPesanan", data), false, data);
        }

        if (lower.contains("promo") || lower.contains("voucher") || lower.contains("diskon")
                || lower.contains("flash sale") || lower.contains("kupon")) {
            Map<String, Object> data = promoAktif();
            return new AiChatResult("promoAktif", formatToolDataFallback("promoAktif", data), false, data);
        }

        if (lower.contains("terlaris") || lower.contains("best seller") || lower.contains("populer")
                || lower.contains("paling laku")) {
            List<Map<String, Object>> data = produkTerlaris(5);
            return new AiChatResult("produkTerlaris", formatToolDataFallback("produkTerlaris", data), false, data);
        }

        if (lower.contains("rekomendasi") || lower.contains("saran")) {
            List<Map<String, Object>> data = rekomendasi(userText, null, null);
            return new AiChatResult("rekomendasi", formatToolDataFallback("rekomendasi", data), false, data);
        }

        if (lower.contains("stok") || lower.contains("stock") || lower.contains("ready")) {
            String kw = extractProductKeyword(lower);
            Map<String, Object> data = cekStok(kw, null, null);
            return new AiChatResult("cekStok", formatToolDataFallback("cekStok", data), false, data);
        }

        if (lower.contains("harga") || lower.contains("berapa")) {
            String kw = extractProductKeyword(lower);
            Map<String, Object> data = cekHarga(kw);
            return new AiChatResult("cekHarga", formatToolDataFallback("cekHarga", data), false, data);
        }

        String extractedColor = extractColorFromText(lower);
        String extractedSize = extractSizeFromText(lower);
        String extractedGender = extractGenderFromText(lower);
        List<Map<String, Object>> found = cariProduk(extractProductKeyword(lower), null, extractedColor, extractedSize,
                extractedGender);
        return new AiChatResult("cariProduk", formatToolDataFallback("cariProduk", found), false, found);
    }

    @SuppressWarnings("unchecked")
    private String formatToolDataFallback(String intent, Object data) {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("message")) {
                return String.valueOf(map.get("message"));
            }
            if ("promoAktif".equals(intent)) {
                StringBuilder sb = new StringBuilder("🎉 **Promo & Voucher Aktif Zelvira Hari Ini**\n\n");
                List<Map<String, Object>> vouchers = (List<Map<String, Object>>) map.getOrDefault("activeVouchers",
                        Collections.emptyList());
                if (!vouchers.isEmpty()) {
                    sb.append("🎟️ **Kode Voucher Diskon:**\n");
                    for (Map<String, Object> v : vouchers) {
                        sb.append("• **`").append(v.get("code")).append("`** — Diskon ").append(v.get("discount"))
                                .append(" (Min. belanja: ").append(v.get("minPurchase")).append(")\n");
                    }
                    sb.append("\n");
                }
                List<Map<String, Object>> flashSales = (List<Map<String, Object>>) map.getOrDefault("activeFlashSales",
                        Collections.emptyList());
                if (!flashSales.isEmpty()) {
                    sb.append("⚡ **Flash Sale Aktif:**\n");
                    for (Map<String, Object> fs : flashSales) {
                        sb.append("• **").append(fs.get("name")).append("**\n");
                    }
                }
                return sb.toString().trim();
            }
            if ("statusPesanan".equals(intent)) {
                List<Map<String, Object>> orders = (List<Map<String, Object>>) map.getOrDefault("orders",
                        Collections.emptyList());
                StringBuilder sb = new StringBuilder("📦 **Status Pesanan Kakak:**\n\n");
                for (Map<String, Object> o : orders) {
                    sb.append("• **").append(o.get("orderNumber")).append("** — Status: **")
                            .append(o.get("statusLabel")).append("**\n")
                            .append("  Total: ").append(o.get("totalAmount"))
                            .append(" | Kurir: ").append(o.get("shippingService"))
                            .append(" | Resi: ").append(o.get("trackingNumber")).append("\n");
                }
                return sb.toString().trim();
            }
            if ("cekStok".equals(intent)) {
                StringBuilder sb = new StringBuilder("📦 **Info Stok ").append(map.get("productName")).append("**\n");
                sb.append("Total Stok Tersedia: **").append(map.get("totalStock")).append(" pcs**\n\n");
                List<Map<String, Object>> vars = (List<Map<String, Object>>) map.getOrDefault("variants",
                        Collections.emptyList());
                for (Map<String, Object> v : vars) {
                    sb.append("• Warna ").append(v.get("color")).append(" (Size ").append(v.get("size")).append("): **")
                            .append(v.get("stock")).append(" pcs** — ").append(v.get("priceFormatted")).append("\n");
                }
                return sb.toString().trim();
            }
        }

        if (data instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) data;
            if (list.isEmpty()) {
                return "Saat ini belum ada produk yang cocok dengan pencarian tersebut Kak. Silakan coba kata kunci lain atau lihat koleksi terbaru kami di "
                        + frontendUrl + "/products ✨";
            }
            StringBuilder sb = new StringBuilder("👗 **Berikut koleksi produk Zelvira yang tersedia:**\n\n");
            for (Map<String, Object> p : list) {
                sb.append("• **").append(p.get("name")).append("** — ").append(p.get("priceFormatted"))
                        .append(" (Stok: ").append(p.get("totalStock")).append(")\n")
                        .append("  🔗 ").append(p.get("url")).append("\n");
            }
            return sb.toString().trim();
        }

        return "Berikut informasi yang Kakak butuhkan dari Zelvira Official Store.";
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private User resolveUser(String roomId, Long explicitUserId) {
        if (explicitUserId != null) {
            return userRepository.findById(explicitUserId).orElse(null);
        }
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        if (roomId.startsWith("user_")) {
            String raw = roomId.substring("user_".length()).trim();
            try {
                Long uid = Long.parseLong(raw);
                return userRepository.findById(uid).orElse(null);
            } catch (NumberFormatException e) {
                if (raw.contains("@")) {
                    return userRepository.findByEmail(raw).orElse(null);
                }
            }
        }
        if (roomId.contains("@")) {
            return userRepository.findByEmail(roomId).orElse(null);
        }
        return null;
    }

    private List<ProductResponse> getAllActiveProductResponses() {
        return productService.getAllProducts(0, 100, null, null)
                .getContent()
                .stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .collect(Collectors.toList());
    }

    private ProductResponse findSingleProduct(String query) {
        if (query == null || query.isBlank()) {
            List<ProductResponse> all = getAllActiveProductResponses();
            return all.isEmpty() ? null : all.get(0);
        }
        String q = query.trim();
        try {
            Long id = Long.parseLong(q);
            return productService.getProductById(id);
        } catch (Exception ignored) {
        }

        try {
            return productService.getProductBySlug(q.toLowerCase());
        } catch (Exception ignored) {
        }

        List<ProductResponse> matched = findMatchingProducts(q, 1);
        return matched.isEmpty() ? null : matched.get(0);
    }

    private List<ProductResponse> findMatchingProducts(String query, int limit) {
        List<ProductResponse> all = getAllActiveProductResponses();
        if (query == null || query.isBlank()) {
            return all.stream().limit(limit).collect(Collectors.toList());
        }
        String q = query.trim().toLowerCase();
        List<ProductResponse> exactOrContains = all.stream()
                .filter(p -> containsIgnoreCase(p.getName(), q)
                        || containsIgnoreCase(p.getSlug(), q)
                        || containsIgnoreCase(p.getSku(), q))
                .limit(limit)
                .collect(Collectors.toList());

        if (!exactOrContains.isEmpty()) {
            return exactOrContains;
        }

        String[] tokens = q.split("\\s+");
        return all.stream()
                .filter(p -> Arrays.stream(tokens).anyMatch(t -> t.length() > 2 && containsIgnoreCase(p.getName(), t)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toCompactProductSummary(ProductResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("slug", p.getSlug());
        m.put("category", p.getCategory() != null ? p.getCategory().getName() : "Fashion");
        String img = (p.getImages() != null && !p.getImages().isEmpty()) ? p.getImages().get(0) : p.getImageUrl();
        m.put("imageUrl", img != null ? img : "");
        BigDecimal price = getEffectivePrice(p);
        m.put("price", price);
        m.put("priceFormatted", formatRupiah(price));
        m.put("totalStock", getTotalStock(p));
        m.put("rating", p.getAverageRating() != null ? p.getAverageRating() : 0.0);
        m.put("reviewCount", p.getReviewCount() != null ? p.getReviewCount() : 0);
        m.put("gender", p.getGender() != null && !p.getGender().isBlank() ? p.getGender().toUpperCase() : "UNISEX");

        List<String> colors = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        List<String> variantSummaries = new ArrayList<>();

        if (p.getVariants() != null && !p.getVariants().isEmpty()) {
            for (ProductVariantResponse v : p.getVariants()) {
                if (Boolean.TRUE.equals(v.getIsActive())) {
                    if (v.getColor() != null && !v.getColor().isBlank() && !colors.contains(v.getColor())) {
                        colors.add(v.getColor());
                    }
                    if (v.getSize() != null && !v.getSize().isBlank() && !sizes.contains(v.getSize())) {
                        sizes.add(v.getSize());
                    }
                    int stock = v.getStock() != null ? v.getStock() : 0;
                    variantSummaries.add(v.getColor() + " (Size " + v.getSize() + ", Stok: " + stock + ")");
                }
            }
        }

        m.put("availableColors", colors);
        m.put("availableSizes", sizes);
        m.put("variantDetails", variantSummaries);
        m.put("url", frontendUrl + "/products/" + p.getSlug());
        return m;
    }

    private BigDecimal getEffectivePrice(ProductResponse p) {
        if (p.getVariants() != null && !p.getVariants().isEmpty()) {
            for (ProductVariantResponse v : p.getVariants()) {
                if (v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0) {
                    return v.getDiscountPrice();
                }
                if (v.getPrice() != null) {
                    return v.getPrice();
                }
            }
        }
        return p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
    }

    private int getTotalStock(ProductResponse p) {
        if (p.getVariants() != null && !p.getVariants().isEmpty()) {
            return p.getVariants().stream()
                    .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                    .mapToInt(v -> v.getStock() != null ? v.getStock() : 0)
                    .sum();
        }
        return p.getQty() != null ? p.getQty() : 0;
    }

    private int similarityScore(ProductResponse candidate, ProductResponse target) {
        int score = 0;
        if (candidate.getCategoryId() != null && Objects.equals(candidate.getCategoryId(), target.getCategoryId())) {
            score += 5;
        }
        if (candidate.getMaterial() != null && target.getMaterial() != null
                && candidate.getMaterial().equalsIgnoreCase(target.getMaterial())) {
            score += 3;
        }
        if (candidate.getGender() != null && target.getGender() != null
                && candidate.getGender().equalsIgnoreCase(target.getGender())) {
            score += 2;
        }
        return score;
    }

    private String extractProductKeyword(String lowerText) {
        String cleaned = lowerText
                .replaceAll(
                        "\\b(tolong|cari|carikan|cek|harga|stok|stock|produk|barang|apakah|ada|ready|berapa|yang|di|zelvira|untuk|dong|kak|min)\\b",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    private String cleanModelOutput(String raw) {
        if (raw == null)
            return "";
        return raw.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private boolean containsIgnoreCase(String source, String target) {
        return source != null && target != null && source.toLowerCase().contains(target.toLowerCase());
    }

    private String formatRupiah(BigDecimal amount) {
        if (amount == null)
            return "Rp 0";
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("id", "ID"));
        nf.setMaximumFractionDigits(0);
        return "Rp " + nf.format(amount);
    }

    private String translateOrderStatus(String status) {
        if (status == null)
            return "Menunggu";
        switch (status.toUpperCase()) {
            case "PENDING":
                return "Menunggu Pembayaran";
            case "PAID":
                return "Sudah Dibayar (Menunggu Diproses)";
            case "PROCESSING":
                return "Sedang Dikemas / Diproses";
            case "DELIVERING":
            case "SHIPPED":
                return "Sedang Dikirim oleh Kurir";
            case "COMPLETED":
                return "Pesanan Selesai";
            case "CANCELLED":
                return "Dibatalkan";
            case "COMPLAINT":
                return "Dalam Penanganan Komplain";
            case "RETURNED":
                return "Diretur / Dikembalikan";
            default:
                return status;
        }
    }

    private String asString(Object val) {
        return val != null ? String.valueOf(val) : null;
    }

    private Double asDouble(Object val) {
        if (val instanceof Number)
            return ((Number) val).doubleValue();
        if (val != null) {
            try {
                return Double.parseDouble(String.valueOf(val));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Integer asInt(Object val) {
        if (val instanceof Number)
            return ((Number) val).intValue();
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // =========================================================================
    // SMART COLOR, SIZE, & GENDER FILTERING HELPERS
    // =========================================================================

    public String normalizeGender(String raw) {
        if (raw == null || raw.isBlank())
            return "";
        String lower = raw.trim().toLowerCase();
        if (lower.contains("wanita") || lower.contains("perempuan") || lower.contains("cewek")
                || lower.contains("akhwat") || lower.contains("ibu") || lower.contains("women")
                || lower.contains("woman") || lower.contains("female")) {
            return "WOMEN";
        }
        if (lower.contains("pria") || lower.contains("laki") || lower.contains("cowok")
                || lower.contains("ikhwan") || lower.contains("bapak") || lower.contains("men")
                || lower.contains("man") || lower.contains("male")) {
            return "MEN";
        }
        if (lower.contains("anak") || lower.contains("kid") || lower.contains("boy") || lower.contains("girl")
                || lower.contains("balita")) {
            return "KIDS";
        }
        if (lower.contains("unisex")) {
            return "UNISEX";
        }
        return raw.toUpperCase().trim();
    }

    public boolean matchesGender(String productGender, String targetGender) {
        if (targetGender == null || targetGender.isBlank())
            return true;
        if (productGender == null || productGender.isBlank())
            return true;
        String prod = normalizeGender(productGender);
        if ("UNISEX".equalsIgnoreCase(prod))
            return true;
        return prod.equalsIgnoreCase(targetGender);
    }

    public String normalizeSize(String raw) {
        if (raw == null || raw.isBlank())
            return "";
        String s = raw.trim().toLowerCase();
        if (s.contains("all") || s.contains("all size") || s.contains("allsize") || s.contains("onesize")
                || s.contains("bebas")) {
            return "ALL SIZE";
        }
        if (s.contains("xxxl") || s.contains("3xl"))
            return "XXXL";
        if (s.contains("xxl") || s.contains("2xl"))
            return "XXL";
        if (s.contains("xl"))
            return "XL";
        if (s.contains("jumbo") || s.contains("big size"))
            return "JUMBO";
        if (s.equals("s") || s.equals("size s") || s.equals("ukuran s"))
            return "S";
        if (s.equals("m") || s.equals("size m") || s.equals("ukuran m"))
            return "M";
        if (s.equals("l") || s.equals("size l") || s.equals("ukuran l"))
            return "L";
        if (s.equals("xs"))
            return "XS";
        return raw.toUpperCase().trim();
    }

    public boolean matchesVariantSize(ProductResponse p, String targetSize) {
        if (targetSize == null || targetSize.isBlank())
            return true;
        if (p.getVariants() == null || p.getVariants().isEmpty())
            return true;

        return p.getVariants().stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .anyMatch(v -> {
                    if (v.getSize() == null || v.getSize().isBlank())
                        return false;
                    String vSize = v.getSize().trim().toUpperCase();
                    if ("ALL SIZE".equals(targetSize)) {
                        return vSize.contains("ALL");
                    }
                    return vSize.equalsIgnoreCase(targetSize) || vSize.contains(targetSize);
                });
    }

    public boolean matchesVariantColor(ProductResponse p, String targetColor) {
        if (targetColor == null || targetColor.isBlank())
            return true;
        if (p.getVariants() == null || p.getVariants().isEmpty())
            return true;

        String queryColor = targetColor.toLowerCase().trim();
        return p.getVariants().stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .anyMatch(v -> {
                    if (v.getColor() == null || v.getColor().isBlank())
                        return false;
                    String varColor = v.getColor().toLowerCase().trim();
                    return varColor.contains(queryColor) || queryColor.contains(varColor);
                });
    }

    private boolean matchesVariantKeywords(ProductResponse p, String keyword) {
        if (keyword == null || keyword.isBlank() || p.getVariants() == null)
            return false;
        String kw = keyword.toLowerCase().trim();
        return p.getVariants().stream().anyMatch(v -> (v.getColor() != null && containsIgnoreCase(v.getColor(), kw)) ||
                (v.getSize() != null && containsIgnoreCase(v.getSize(), kw)));
    }

    public String extractColorFromText(String text) {
        if (text == null || text.isBlank())
            return "";
        String lower = text.toLowerCase();
        List<String> knownColors = List.of(
                "sage green", "hijau sage", "dusty pink", "baby pink", "broken white", "biru navy",
                "sky blue", "hitam", "black", "putih", "white", "maroon", "marun", "navy", "mocca", "moka",
                "sage", "lilac", "ungu", "pink", "merah", "coklat", "cokelat", "abu-abu", "abu", "grey", "gray",
                "krem", "cream", "beige", "khaki", "hijau", "green", "army", "olive", "emerald",
                "mustard", "kuning", "yellow", "terracotta", "bata", "orange", "oranye", "lavender", "silver", "gold");
        for (String c : knownColors) {
            if (Pattern.compile("\\b" + Pattern.quote(c) + "\\b").matcher(lower).find()) {
                return c;
            }
        }
        return "";
    }

    public String extractSizeFromText(String text) {
        if (text == null || text.isBlank())
            return "";
        String lower = text.toLowerCase();
        if (lower.contains("all size") || lower.contains("allsize") || lower.contains("all-size")
                || lower.contains("onesize")) {
            return "ALL SIZE";
        }
        if (lower.contains("jumbo") || lower.contains("big size")) {
            return "JUMBO";
        }
        if (Pattern.compile("\\b(xxxl|3xl)\\b").matcher(lower).find()) {
            return "XXXL";
        }
        if (Pattern.compile("\\b(xxl|2xl)\\b").matcher(lower).find()) {
            return "XXL";
        }
        if (Pattern.compile("\\bxl\\b").matcher(lower).find()) {
            return "XL";
        }
        if (Pattern.compile("\\b(size\\s+l|ukuran\\s+l|size\\s*l|ukuran\\s*l)\\b").matcher(lower).find()
                || Pattern.compile("\\bl\\b").matcher(lower).find()) {
            return "L";
        }
        if (Pattern.compile("\\b(size\\s+m|ukuran\\s+m|size\\s*m|ukuran\\s*m)\\b").matcher(lower).find()
                || Pattern.compile("\\bm\\b").matcher(lower).find()) {
            return "M";
        }
        if (Pattern.compile("\\b(size\\s+s|ukuran\\s+s|size\\s*s|ukuran\\s*s)\\b").matcher(lower).find()
                || Pattern.compile("\\bs\\b").matcher(lower).find()) {
            return "S";
        }
        if (Pattern.compile("\\bxs\\b").matcher(lower).find()) {
            return "XS";
        }
        return "";
    }

    public String extractGenderFromText(String text) {
        if (text == null || text.isBlank())
            return "";
        String lower = text.toLowerCase();
        if (lower.contains("wanita") || lower.contains("cewek") || lower.contains("perempuan")
                || lower.contains("akhwat") || lower.contains("muslimah") || lower.contains("women")
                || lower.contains("woman")) {
            return "WOMEN";
        }
        if (lower.contains("pria") || lower.contains("cowok") || lower.contains("laki")
                || lower.contains("ikhwan") || lower.contains("men") || lower.contains("man")) {
            return "MEN";
        }
        if (lower.contains("anak") || lower.contains("kids") || lower.contains("kid") || lower.contains("balita")) {
            return "KIDS";
        }
        if (lower.contains("unisex")) {
            return "UNISEX";
        }
        return "";
    }

    private String cleanSearchKeyword(String rawKw, String color, String size, String gender) {
        String cleaned = rawKw;
        if (color != null && !color.isBlank()) {
            cleaned = cleaned.replaceAll("(?i)\\b" + Pattern.quote(color) + "\\b", " ");
        }
        if (size != null && !size.isBlank()) {
            cleaned = cleaned.replaceAll("(?i)\\b" + Pattern.quote(size) + "\\b", " ");
        }
        if (gender != null && !gender.isBlank()) {
            cleaned = cleaned.replaceAll(
                    "(?i)\\b(wanita|perempuan|cewek|pria|cowok|laki-laki|laki|anak|anak-anak|kids|women|men|unisex)\\b",
                    " ");
        }
        cleaned = cleaned.replaceAll("(?i)\\b(warna|ukuran|size|untuk|buat|all\\s*size)\\b", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

}