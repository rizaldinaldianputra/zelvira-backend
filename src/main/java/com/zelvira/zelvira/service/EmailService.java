package com.zelvira.zelvira.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${app.mail.from:${spring.mail.username:noreply@zelvira.my.id}}")
    private String fromEmail;

    @Value("${app.mail.sender-name:Zelixa Official}")
    private String senderName;

    /**
     * Send an HTML email helper method
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, senderName);
            } catch (Exception e) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true indicates HTML format

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {} via SMTP: {}", to, e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("554")) {
                log.error(
                        "[SMTP ERROR 554] Postfix menolak pengiriman karena akun noreply@zelvira.my.id belum terdaftar di SASL/Dovecot docker-mailserver. Jalankan di VPS: docker exec -it zelvira-mailserver setup email add noreply@zelvira.my.id <MAIL_PASSWORD>");
                throw new RuntimeException(
                        "Gagal mengirim email OTP: Akun email noreply@zelvira.my.id belum terdaftar di docker-mailserver. Silakan jalankan perintah di VPS: docker exec -it zelvira-mailserver setup email add noreply@zelvira.my.id <MAIL_PASSWORD>",
                        e);
            }
            throw new RuntimeException("Gagal mengirim email: " + e.getMessage(), e);
        }
    }

    /**
     * Send OTP for Forgot Password
     */
    public void sendOtpEmail(String toEmail, String otpCode) {
        String subject = "Zelixa - Kode Verifikasi Reset Password";
        String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                    <div style="max-width: 560px; margin: 30px auto; background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.05); border: 1px solid #e2e8f0;">
                        <!-- Header Banner -->
                        <div style="background: linear-gradient(135deg, #ec4899 0%%, #a855f7 50%%, #6366f1 100%%); padding: 32px 24px; text-align: center;">
                            <h1 style="color: #ffffff; font-size: 28px; font-weight: 900; letter-spacing: -0.5px; margin: 0; text-transform: uppercase; font-style: italic;">ZELIXA</h1>
                            <p style="color: rgba(255, 255, 255, 0.9); font-size: 13px; font-weight: 600; margin: 6px 0 0 0; letter-spacing: 1px; text-transform: uppercase;">Keamanan & Pemulihan Akun</p>
                        </div>

                        <!-- Content Body -->
                        <div style="padding: 32px 28px;">
                            <h2 style="color: #0f172a; font-size: 20px; font-weight: 800; margin: 0 0 12px 0;">Permintaan Reset Kata Sandi</h2>
                            <p style="color: #475569; font-size: 14px; line-height: 1.6; margin: 0 0 20px 0;">
                                Halo, kami menerima permintaan untuk mengatur ulang kata sandi akun Zelixa Anda. Gunakan kode One-Time Password (OTP) berikut untuk melanjutkan:
                            </p>

                            <!-- OTP Box -->
                            <div style="background: #fdf2f8; border: 2px dashed #ec4899; border-radius: 16px; padding: 22px; text-align: center; margin: 24px 0;">
                                <p style="color: #9d174d; font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 2px; margin: 0 0 8px 0;">Kode Verifikasi OTP</p>
                                <div style="font-family: 'Courier New', Courier, monospace; font-size: 36px; font-weight: 900; letter-spacing: 10px; color: #db2777; margin: 0;">
                                    %s
                                </div>
                            </div>

                            <!-- Expiry & Security Notes -->
                            <div style="background-color: #f8fafc; border-left: 4px solid #6366f1; padding: 12px 16px; border-radius: 8px; margin-bottom: 24px;">
                                <p style="color: #334155; font-size: 13px; margin: 0; line-height: 1.5;">
                                    ⏱️ <strong>Masa Berlaku:</strong> Kode ini berlaku selama <strong>5 menit</strong>.<br/>
                                    🔒 <strong>Penting:</strong> Jangan berikan kode OTP ini kepada siapapun demi keamanan akun Anda.
                                </p>
                            </div>

                            <p style="color: #64748b; font-size: 13px; line-height: 1.5; margin: 0 0 8px 0;">
                                Jika Anda tidak merasa melakukan permintaan ini, Anda dapat mengabaikan email ini. Kata sandi akun Anda tetap aman dan tidak akan berubah.
                            </p>
                        </div>

                        <!-- Footer -->
                        <div style="background-color: #f1f5f9; padding: 20px 24px; text-align: center; border-top: 1px solid #e2e8f0;">
                            <p style="color: #64748b; font-size: 12px; margin: 0 0 4px 0;">Email ini dikirim secara otomatis oleh sistem <strong>Zelixa</strong>.</p>
                            <p style="color: #94a3b8; font-size: 11px; margin: 0;">&copy; 2026 Zelixa. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(otpCode);

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Send Order Confirmation
     */
    public void sendOrderConfirmation(String toEmail, String orderId, String customerName, String totalAmount) {
        String subject = "Zelixa - Konfirmasi Pesanan #" + orderId;
        String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                    <div style="max-width: 560px; margin: 30px auto; background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.05); border: 1px solid #e2e8f0;">
                        <div style="background: linear-gradient(135deg, #ec4899 0%%, #a855f7 50%%, #6366f1 100%%); padding: 32px 24px; text-align: center;">
                            <h1 style="color: #ffffff; font-size: 28px; font-weight: 900; letter-spacing: -0.5px; margin: 0; text-transform: uppercase; font-style: italic;">ZELIXA</h1>
                            <p style="color: rgba(255, 255, 255, 0.9); font-size: 13px; font-weight: 600; margin: 6px 0 0 0; letter-spacing: 1px; text-transform: uppercase;">Pesanan Diterima</p>
                        </div>

                        <div style="padding: 32px 28px;">
                            <h2 style="color: #0f172a; font-size: 20px; font-weight: 800; margin: 0 0 12px 0;">Terima Kasih atas Pesanan Anda!</h2>
                            <p style="color: #475569; font-size: 14px; line-height: 1.6; margin: 0 0 20px 0;">
                                Halo <strong>%s</strong>, pesanan Anda dengan nomor <strong>#%s</strong> telah berhasil dibuat dan menunggu pembayaran.
                            </p>

                            <table style="width: 100%%; border-collapse: collapse; margin: 20px 0; background-color: #f8fafc; border-radius: 12px; overflow: hidden; border: 1px solid #e2e8f0;">
                                <tr>
                                    <td style="padding: 14px 18px; color: #475569; font-size: 14px; font-weight: 600;">Total Tagihan</td>
                                    <td style="padding: 14px 18px; text-align: right; color: #ec4899; font-size: 18px; font-weight: 900;">%s</td>
                                </tr>
                            </table>

                            <p style="color: #64748b; font-size: 13px; line-height: 1.5; margin: 0;">
                                Anda dapat memantau status pesanan dan menyelesaikan pembayaran langsung melalui web atau aplikasi Zelixa.
                            </p>
                        </div>

                        <div style="background-color: #f1f5f9; padding: 20px 24px; text-align: center; border-top: 1px solid #e2e8f0;">
                            <p style="color: #94a3b8; font-size: 11px; margin: 0;">&copy; 2026 Zelixa. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(customerName, orderId, totalAmount);

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Send Promotional Email
     */
    public void sendPromoEmail(String toEmail, String promoTitle, String promoDescription, String discountCode) {
        String subject = "Zelvira Special Offer - " + promoTitle;
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eeeeee; border-radius: 10px; background-color: #ffffff;">
                    <div style="text-align: center; padding: 20px; background-color: #ffe6e6; border-radius: 10px 10px 0 0;">
                        <h2 style="color: #ff3333; margin: 0;">%s</h2>
                    </div>
                    <div style="padding: 20px;">
                        <p style="color: #333333; font-size: 16px;">%s</p>
                        <div style="background-color: #f8f9fa; padding: 15px; text-align: center; border-radius: 5px; margin: 20px 0; border: 2px dashed #ff3333;">
                            <p style="color: #666666; margin: 0 0 10px 0;">Use promo code at checkout:</p>
                            <h2 style="color: #ff3333; letter-spacing: 2px; margin: 0;">%s</h2>
                        </div>
                    </div>
                    <hr style="border: none; border-top: 1px solid #eeeeee; margin: 20px 0;" />
                    <p style="color: #999999; font-size: 12px; text-align: center;">&copy; 2026 Zelvira. Terms and conditions apply.</p>
                </div>
                """
                .formatted(promoTitle, promoDescription, discountCode);

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Send Digital Product Order Invoice
     */
    public void sendDigitalInvoiceEmail(String toEmail, com.zelvira.zelvira.entity.DigitalOrder order) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            log.warn("Cannot send digital invoice: email is empty for order {}", order.getOrderNumber());
            return;
        }

        String subject = "Zelvira - Invoice Transaksi Digital #" + order.getOrderNumber();
        String snSection = "";
        if (order.getDigiflazzSn() != null && !order.getDigiflazzSn().trim().isEmpty()) {
            snSection = """
                        <tr style="background-color: #f0fdf4;">
                            <td style="padding: 12px; border: 1px solid #bbf7d0;"><strong>SN / Token / Bukti</strong></td>
                            <td style="padding: 12px; border: 1px solid #bbf7d0; font-family: monospace; font-size: 16px; font-weight: bold; color: #166534; text-align: right;">%s</td>
                        </tr>
                    """
                    .formatted(order.getDigiflazzSn());
        }

        String formattedPrice = String.format("Rp %,.0f", order.getPrice()).replace(",", ".");

        String htmlContent = """
                    <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 0 auto; padding: 25px; border: 1px solid #e2e8f0; border-radius: 14px; background-color: #ffffff;">
                        <div style="text-align: center; border-bottom: 2px solid #f1f5f9; padding-bottom: 20px; margin-bottom: 20px;">
                            <h1 style="color: #4f46e5; margin: 0; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;">ZELIXA</h1>
                            <p style="color: #64748b; font-size: 13px; margin: 4px 0 0 0;">Bukti Pembayaran Produk Digital</p>
                        </div>

                        <div style="background-color: #ecfdf5; border-left: 4px solid #10b981; padding: 14px; border-radius: 6px; margin-bottom: 20px;">
                            <p style="color: #065f46; font-size: 15px; font-weight: 600; margin: 0;">Pembayaran & Eksekusi Berhasil!</p>
                            <p style="color: #047857; font-size: 13px; margin: 4px 0 0 0;">Produk digital Anda telah berhasil diproses oleh sistem.</p>
                        </div>

                        <table style="width: 100%%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;">
                            <tr>
                                <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; color: #64748b;">No. Transaksi</td>
                                <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; font-weight: bold; text-align: right; color: #1e293b;">#%s</td>
                            </tr>
                            <tr>
                                <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; color: #64748b;">Produk</td>
                                <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; font-weight: bold; text-align: right; color: #1e293b;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; color: #64748b;">No. Tujuan / ID Pelanggan</td>
                                <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; font-weight: bold; text-align: right; color: #4f46e5; font-family: monospace; font-size: 15px;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; color: #64748b;">Kategori / Brand</td>
                                <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; text-align: right; color: #475569;">%s / %s</td>
                            </tr>
                            %s
                            <tr style="background-color: #f8fafc;">
                                <td style="padding: 12px; font-weight: bold; color: #1e293b; font-size: 15px;">Total Pembayaran</td>
                                <td style="padding: 12px; font-weight: 800; color: #4f46e5; text-align: right; font-size: 16px;">%s</td>
                            </tr>
                        </table>

                        <div style="background-color: #f8fafc; border: 1px dashed #cbd5e1; padding: 14px; border-radius: 8px; text-align: center; margin-bottom: 25px;">
                            <p style="color: #64748b; font-size: 12px; margin: 0;">Status Pembayaran: <strong style="color: #10b981;">LUNAS (Midtrans)</strong> | Status Transaksi: <strong style="color: #10b981;">SUKSES (Digiflazz)</strong></p>
                        </div>

                        <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 25px 0 15px 0;" />
                        <p style="color: #94a3b8; font-size: 12px; text-align: center; margin: 0;">Terima kasih telah berbelanja di Zelvira! Simpan email ini sebagai bukti transaksi resmi Anda.</p>
                    </div>
                """
                .formatted(
                        order.getOrderNumber(),
                        order.getProductName(),
                        order.getCustomerNo(),
                        order.getCategory() != null ? order.getCategory() : "-",
                        order.getBrand() != null ? order.getBrand() : "-",
                        snSection,
                        formattedPrice);

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Send Physical Order Paid Invoice
     */
    public void sendPaidInvoiceEmail(String toEmail, com.zelvira.zelvira.entity.Order order) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            if (order.getUser() != null && order.getUser().getEmail() != null
                    && !order.getUser().getEmail().trim().isEmpty()) {
                toEmail = order.getUser().getEmail();
            } else {
                log.warn("Cannot send paid invoice: email is empty for order {}", order.getOrderNumber());
                return;
            }
        }

        String recipientName = order.getShippingRecipient() != null ? order.getShippingRecipient() : "Pelanggan";
        String subject = "Zelixa - Bukti Pembayaran Lunas #" + order.getOrderNumber();

        StringBuilder itemsRows = new StringBuilder();
        if (order.getItems() != null) {
            for (com.zelvira.zelvira.entity.OrderItem item : order.getItems()) {
                String variantInfo = "";
                if (item.getColor() != null || item.getSize() != null) {
                    variantInfo = " <span style=\"color: #64748b; font-size: 12px;\">("
                            + (item.getColor() != null ? item.getColor() : "")
                            + (item.getColor() != null && item.getSize() != null ? ", " : "")
                            + (item.getSize() != null ? item.getSize() : "") + ")</span>";
                }
                String itemTotal = String.format("Rp %,.0f", item.getPrice() * item.getQuantity()).replace(",", ".");
                String unitPrice = String.format("Rp %,.0f", item.getPrice()).replace(",", ".");

                itemsRows
                        .append("""
                                    <tr>
                                        <td style="padding: 12px 10px; border-bottom: 1px solid #f1f5f9; color: #1e293b;">
                                            <strong style="color: #0f172a;">%s</strong>%s<br/>
                                            <span style="font-size: 12px; color: #64748b;">%d x %s</span>
                                        </td>
                                        <td style="padding: 12px 10px; border-bottom: 1px solid #f1f5f9; text-align: right; font-weight: 700; color: #0f172a;">%s</td>
                                    </tr>
                                """
                                .formatted(
                                        item.getProductName() != null ? item.getProductName() : "Produk",
                                        variantInfo,
                                        item.getQuantity(),
                                        unitPrice,
                                        itemTotal));
            }
        }

        String discountsHtml = "";
        if (order.getVoucherDiscountAmount() != null && order.getVoucherDiscountAmount() > 0) {
            discountsHtml += String
                    .format("""
                                <tr>
                                    <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; color: #16a34a; font-size: 13px;">Diskon Voucher (%s)</td>
                                    <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; text-align: right; color: #16a34a; font-weight: 700; font-size: 14px;">-Rp %,.0f</td>
                                </tr>
                            """,
                            order.getVoucherCode() != null ? order.getVoucherCode() : "Promo",
                            order.getVoucherDiscountAmount())
                    .replace(",", ".");
        }
        if (order.getPointsDiscountAmount() != null && order.getPointsDiscountAmount() > 0) {
            discountsHtml += String
                    .format("""
                                <tr>
                                    <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; color: #16a34a; font-size: 13px;">Potongan Poin (%d Poin)</td>
                                    <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; text-align: right; color: #16a34a; font-weight: 700; font-size: 14px;">-Rp %,.0f</td>
                                </tr>
                            """,
                            order.getPointsUsed() != null ? order.getPointsUsed() : 0, order.getPointsDiscountAmount())
                    .replace(",", ".");
        }

        String formattedTotal = String.format("Rp %,.0f", order.getTotalAmount()).replace(",", ".");
        String formattedShipping = order.getShippingAmount() != null && order.getShippingAmount() > 0
                ? String.format("Rp %,.0f", order.getShippingAmount()).replace(",", ".")
                : "Gratis Ongkir (Rp 0)";

        String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 30px auto; background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.05); border: 1px solid #e2e8f0;">
                        <!-- Header Banner -->
                        <div style="background: linear-gradient(135deg, #ec4899 0%%, #a855f7 50%%, #6366f1 100%%); padding: 32px 24px; text-align: center;">
                            <h1 style="color: #ffffff; font-size: 28px; font-weight: 900; letter-spacing: -0.5px; margin: 0; text-transform: uppercase; font-style: italic;">ZELIXA</h1>
                            <p style="color: rgba(255, 255, 255, 0.95); font-size: 13px; font-weight: 600; margin: 6px 0 0 0; letter-spacing: 1px; text-transform: uppercase;">Bukti Pembayaran Transaksi Resmi</p>
                        </div>

                        <div style="padding: 32px 28px;">
                            <!-- Success Alert -->
                            <div style="background-color: #ecfdf5; border-left: 4px solid #10b981; padding: 16px; border-radius: 10px; margin-bottom: 24px;">
                                <p style="color: #065f46; font-size: 16px; font-weight: 800; margin: 0;">Pembayaran Berhasil Dikonfirmasi! 🎉</p>
                                <p style="color: #047857; font-size: 13px; margin: 6px 0 0 0; line-height: 1.5;">
                                    Halo <strong>%s</strong>, pesanan Anda dengan nomor <strong>#%s</strong> telah lunas dan sedang kami siapkan untuk pengiriman.
                                </p>
                            </div>

                            <!-- Items Table -->
                            <table style="width: 100%%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;">
                                <tr style="background-color: #f8fafc;">
                                    <th style="padding: 12px 10px; text-align: left; color: #475569; font-size: 12px; text-transform: uppercase; font-weight: 800; border-bottom: 2px solid #e2e8f0;">Detail Produk</th>
                                    <th style="padding: 12px 10px; text-align: right; color: #475569; font-size: 12px; text-transform: uppercase; font-weight: 800; border-bottom: 2px solid #e2e8f0;">Subtotal</th>
                                </tr>
                                %s
                                <tr>
                                    <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; color: #64748b; font-size: 13px;">Ongkos Kirim (%s)</td>
                                    <td style="padding: 10px; border-bottom: 1px solid #f1f5f9; text-align: right; color: #1e293b; font-weight: 600;">%s</td>
                                </tr>
                                %s
                                <tr style="background-color: #fdf2f8;">
                                    <td style="padding: 14px 10px; font-weight: 800; color: #0f172a; font-size: 15px;">Total Pembayaran</td>
                                    <td style="padding: 14px 10px; font-weight: 900; color: #db2777; text-align: right; font-size: 18px;">%s</td>
                                </tr>
                            </table>

                            <!-- Shipping Details -->
                            <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; padding: 16px; border-radius: 12px; margin-bottom: 20px; font-size: 13px; color: #475569;">
                                <p style="margin: 0 0 6px 0; font-weight: 800; color: #0f172a; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px;">Alamat Pengiriman:</p>
                                <p style="margin: 0; color: #0f172a; font-weight: 700; font-size: 14px;">%s (%s)</p>
                                <p style="margin: 4px 0 0 0; color: #64748b; line-height: 1.5;">%s</p>
                            </div>

                            <!-- Payment Meta -->
                            <div style="background-color: #f8fafc; border: 1px dashed #cbd5e1; padding: 14px; border-radius: 10px; text-align: center; margin-bottom: 20px;">
                                <p style="color: #64748b; font-size: 12px; margin: 0;">
                                    Metode Pembayaran: <strong style="color: #0f172a;">%s</strong> | Status: <strong style="color: #10b981;">LUNAS (Terverifikasi)</strong>
                                </p>
                            </div>

                            <p style="color: #64748b; font-size: 12px; text-align: center; margin: 0; line-height: 1.5;">
                                Simpan email ini sebagai tanda bukti pembayaran yang sah. Anda dapat memantau status pesanan kapan saja melalui aplikasi Zelixa.
                            </p>
                        </div>

                        <!-- Footer -->
                        <div style="background-color: #f1f5f9; padding: 20px 24px; text-align: center; border-top: 1px solid #e2e8f0;">
                            <p style="color: #64748b; font-size: 12px; margin: 0 0 4px 0;">Terima kasih telah berbelanja di <strong>Zelixa</strong>!</p>
                            <p style="color: #94a3b8; font-size: 11px; margin: 0;">&copy; 2026 Zelixa. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(
                        recipientName,
                        order.getOrderNumber(),
                        itemsRows.toString(),
                        order.getShippingService() != null ? order.getShippingService() : "Reguler",
                        formattedShipping,
                        discountsHtml,
                        formattedTotal,
                        recipientName,
                        order.getShippingPhone() != null ? order.getShippingPhone() : "-",
                        order.getShippingAddress() != null ? order.getShippingAddress() : "-",
                        order.getPaymentMethod() != null ? order.getPaymentMethod() : "MIDTRANS");

        sendHtmlEmail(toEmail, subject, htmlContent);
    }
}
