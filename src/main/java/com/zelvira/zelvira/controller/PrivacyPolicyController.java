package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.entity.PrivacyPolicy;
import com.zelvira.zelvira.repository.PrivacyPolicyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/privacy-policy")
public class PrivacyPolicyController {
    private final PrivacyPolicyRepository repository;

    public PrivacyPolicyController(PrivacyPolicyRepository repository) {
        this.repository = repository;
    }

    /**
     * Get the single current privacy policy document (for admin or client consumption).
     * If no record exists yet, an initial default template is created and returned.
     */
    @GetMapping("/current")
    public ResponseEntity<PrivacyPolicy> getCurrent() {
        return ResponseEntity.ok(getOrCreateDefault());
    }

    /**
     * Get the currently active published privacy policy (for public web and mobile).
     */
    @GetMapping("/active")
    public ResponseEntity<PrivacyPolicy> getActive() {
        Optional<PrivacyPolicy> active = repository.findFirstByIsActiveTrueOrderByUpdatedAtDesc();
        if (active.isPresent()) {
            return ResponseEntity.ok(active.get());
        }

        // If repository is completely uninitialized, seed and return default
        if (repository.count() == 0) {
            PrivacyPolicy defaultPolicy = getOrCreateDefault();
            return ResponseEntity.ok(defaultPolicy);
        }

        // Exists in DB but is explicitly unpublished / draft
        return ResponseEntity.notFound().build();
    }

    /**
     * List endpoint preserved for backward compatibility.
     */
    @GetMapping
    public List<PrivacyPolicy> getAll(
            @RequestParam(required = false, defaultValue = "false") boolean includeDraft,
            @RequestParam(required = false) Boolean onlyActive) {
        if (includeDraft && !Boolean.TRUE.equals(onlyActive)) {
            List<PrivacyPolicy> all = repository.findAll();
            if (all.isEmpty()) {
                return List.of(getOrCreateDefault());
            }
            return all;
        }

        List<PrivacyPolicy> activeList = repository.findAllByIsActiveTrueOrderByUpdatedAtDesc();
        if (activeList.isEmpty() && repository.count() == 0) {
            return List.of(getOrCreateDefault());
        }
        return activeList;
    }

    /**
     * Create or update the single privacy policy document.
     * Prevents multiple duplicate table rows by updating the existing singleton document.
     */
    @PostMapping
    public ResponseEntity<PrivacyPolicy> createOrUpdate(@RequestBody PrivacyPolicy policy) {
        return ResponseEntity.ok(upsert(policy));
    }

    @PutMapping
    public ResponseEntity<PrivacyPolicy> saveWithoutId(@RequestBody PrivacyPolicy policy) {
        return ResponseEntity.ok(upsert(policy));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrivacyPolicy> updateWithId(@PathVariable Long id, @RequestBody PrivacyPolicy policy) {
        policy.setId(id);
        return ResponseEntity.ok(upsert(policy));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private synchronized PrivacyPolicy getOrCreateDefault() {
        return repository.findFirstByOrderByUpdatedAtDesc()
                .or(() -> repository.findFirstByOrderByIdAsc())
                .orElseGet(() -> {
                    PrivacyPolicy defaultPolicy = PrivacyPolicy.builder()
                            .title("Kebijakan Privasi Zelvira")
                            .content(getDefaultPrivacyHtml())
                            .isActive(true)
                            .build();
                    return repository.save(defaultPolicy);
                });
    }

    private synchronized PrivacyPolicy upsert(PrivacyPolicy input) {
        Optional<PrivacyPolicy> existingOpt = Optional.empty();
        if (input.getId() != null) {
            existingOpt = repository.findById(input.getId());
        }

        if (existingOpt.isEmpty()) {
            existingOpt = repository.findFirstByOrderByUpdatedAtDesc()
                    .or(() -> repository.findFirstByOrderByIdAsc());
        }

        if (existingOpt.isPresent()) {
            PrivacyPolicy target = existingOpt.get();
            if (input.getTitle() != null && !input.getTitle().trim().isEmpty()) {
                target.setTitle(input.getTitle().trim());
            }
            if (input.getContent() != null) {
                target.setContent(input.getContent());
            }
            if (input.getIsActive() != null) {
                target.setIsActive(input.getIsActive());
            }
            return repository.save(target);
        } else {
            if (input.getTitle() == null || input.getTitle().trim().isEmpty()) {
                input.setTitle("Kebijakan Privasi Zelvira");
            }
            if (input.getContent() == null || input.getContent().trim().isEmpty()) {
                input.setContent(getDefaultPrivacyHtml());
            }
            if (input.getIsActive() == null) {
                input.setIsActive(true);
            }
            return repository.save(input);
        }
    }

    private String getDefaultPrivacyHtml() {
        return "<h2>Komitmen Perlindungan Privasi Zelvira</h2>"
                + "<p>Privasi Anda adalah prioritas utama kami di <strong>Zelvira</strong>. Kebijakan ini menjelaskan bagaimana kami mengumpulkan, menggunakan, menyimpan, dan melindungi data pribadi Anda saat menggunakan layanan website dan aplikasi mobile kami.</p>"
                + "<h3>1. Data yang Kami Kumpulkan</h3>"
                + "<p>Kami mengumpulkan data yang Anda berikan secara langsung saat mendaftar akun atau melakukan transaksi:</p>"
                + "<ul>"
                + "<li><strong>Informasi Akun:</strong> Nama lengkap, alamat email, nomor telepon/WhatsApp, dan kata sandi terenkripsi.</li>"
                + "<li><strong>Informasi Pengiriman:</strong> Alamat tujuan pengantaran, nama penerima, nomor kontak aktif, dan catatan khusus pengiriman.</li>"
                + "<li><strong>Informasi Transaksi:</strong> Riwayat pembelian, metode pembayaran, invoice, dan status pesanan.</li>"
                + "</ul>"
                + "<h3>2. Penggunaan Informasi Pribadi</h3>"
                + "<p>Data Anda kami gunakan semata-mata untuk kepentingan operasional dan kenyamanan berbelanja:</p>"
                + "<ul>"
                + "<li>Memproses pembayaran, pengemasan produk, dan pengiriman pesanan ke alamat Anda.</li>"
                + "<li>Mengirimkan konfirmasi transaksi, pembaruan status nomor resi pengiriman, dan pemberitahuan penting.</li>"
                + "<li>Memberikan respon dan bantuan cepat melalui Customer Service Zelvira.</li>"
                + "<li>Menyediakan program promo khusus, voucher diskon, dan rekomendasi produk yang relevan.</li>"
                + "</ul>"
                + "<h3>3. Kerahasiaan Data & Pihak Ketiga</h3>"
                + "<p><strong>Zelvira tidak pernah dan tidak akan pernah menjual atau menyewakan informasi pribadi Anda kepada pihak manapun.</strong> Data hanya dibagikan kepada mitra operasional resmi:</p>"
                + "<ul>"
                + "<li><strong>Mitra Ekspedisi Logistik:</strong> JNE, J&T, SiCepat, dll. untuk pengantaran barang ke alamat Anda.</li>"
                + "<li><strong>Payment Gateway Berizin BI:</strong> Midtrans, Xendit, QRIS untuk pemrosesan pembayaran yang aman dan tersertifikasi PCI-DSS.</li>"
                + "</ul>"
                + "<h3>4. Keamanan Data</h3>"
                + "<p>Kami menerapkan standar enkripsi SSL/TLS tingkat tinggi dan sistem penyimpanan aman untuk melindungi data pribadi Anda dari akses tanpa izin, kebocoran, atau perubahan yang tidak sah.</p>"
                + "<h3>5. Hak Pengguna & Penghapusan Akun</h3>"
                + "<p>Anda berhak memperbarui data profil atau meminta penghapusan akun beserta seluruh riwayat data pribadi Anda kapan saja melalui menu Pengaturan Profil di aplikasi atau website Zelvira.</p>";
    }
}
